package com.hydrainnit.refract;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refract's ambient screen, in the spirit of Apple TV's screensaver: a full-screen photo or movie
 * backdrop with a large clock, the weather and a short description. Used by the Android system
 * screensaver (RefractDream) and by Home itself when it is idle ("Refract Ambient").
 *
 * Modes: "photos" (the profile's wallpaper category, rotating), "movies" (TMDB backdrops with
 * the title logo, overview, rating and date), "single" (one photo) and "minimal" (a dark or
 * light field with just the clock and weather).
 *
 * Burn-in: the clock, weather and text blocks each drift to a new spot inside a small safe box
 * every minute (a 3 s glide; a fade with Reduce Motion). Groups move as one (a movie's logo,
 * overview and rating stay together) and never leave the safe area. This lowers static exposure
 * on OLED panels; it cannot rule burn-in out.
 *
 * Memory: one decoded backdrop is kept; the next one is decoded only when it is about to be shown
 * and the previous one is dropped when the cross-fade ends (on low-RAM TVs the image is swapped
 * without a cross-fade so only one is ever in memory). No timers run while it is detached.
 */
final class Ambient extends FrameLayout {
    static final String PREFS = "ambient";
    static final String PHOTOS = "photos", MOVIES = "movies", SINGLE = "single", MINIMAL = "minimal";
    static final String[] MODES = {PHOTOS, MOVIES, SINGLE, MINIMAL};
    static final String[] MODE_NAMES = {"Photos", "Movies", "Single Photo", "Minimal"};

    /** Every screensaver setting, read once when it starts. */
    static final class Settings {
        String mode, category, singleId, transition, clockStyle, clockFont, clockPos, clock24;
        boolean follow, weather, description, attribution, reduceMotion, drift, clockSeconds, clockDate;
        int interval, dim, clockSize, clockOpacity, clockSpacing, clockDx, clockDy;

        static Settings load(Context c) {
            SharedPreferences p = prefs(c);
            Settings s = new Settings();
            s.mode = p.getString("mode", PHOTOS);
            s.follow = p.getBoolean("follow", true);
            s.category = s.follow ? Wallpapers.category(c) : p.getString("category", Wallpapers.category(c));
            Wallpapers.Photo sel = Wallpapers.selected(c);
            s.singleId = p.getString("single", sel != null ? sel.id : Wallpapers.defaultPhoto(c).id);
            s.interval = p.getInt("interval", 30);
            s.transition = p.getString("transition", "fade");
            s.weather = p.getBoolean("weather", true);
            s.description = p.getBoolean("description", true);
            s.attribution = p.getBoolean("attribution", true);
            s.reduceMotion = p.getBoolean("reduce_motion", false);
            s.drift = p.getBoolean("drift", true);
            s.dim = p.getInt("dim", 0);
            s.clockStyle = p.getString("clock_style", ClockView.CINEMATIC);
            s.clockFont = p.getString("clock_font", "thin");
            s.clockPos = p.getString("clock_pos", "top");
            s.clock24 = p.getString("clock_24", "auto");
            s.clockSeconds = p.getBoolean("clock_seconds", false);
            s.clockDate = p.getBoolean("clock_date", true);
            s.clockSize = p.getInt("clock_size", 100);
            s.clockOpacity = p.getInt("clock_opacity", 90);
            s.clockSpacing = p.getInt("clock_spacing", -2);
            s.clockDx = p.getInt("clock_dx", 0);
            s.clockDy = p.getInt("clock_dy", 0);
            return s;
        }
    }

    /** What idle Home shows: the carousel full screen (as before) or this Ambient screen. */
    static final String K_IDLE = "idle", IDLE_CAROUSEL = "carousel", IDLE_AMBIENT = "ambient";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final String[] POSITIONS = {"top", "center", "top_left", "bottom_left", "bottom_right"};
    static final String[] POSITION_NAMES = {"Top", "Centre", "Top Left", "Bottom Left", "Bottom Right"};

    final Settings s;
    final Theme theme;
    final float u;
    final boolean lowRam;
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final int screenW, screenH;

    ImageView back, front;
    View veil, dim;
    ClockView clock;
    LinearLayout weatherGroup, infoGroup, movieGroup;
    ImageView weatherIcon, movieLogo;
    TextView weatherTemp, weatherText, infoTitle, infoCredit, movieTitle, movieOverview, movieMeta;
    final List<Wallpapers.Photo> photos = new ArrayList<>();
    List<Suggestions.Item> movies = new ArrayList<>();
    int index;
    /** Bumped for every image change: results of an older decode are thrown away. */
    int generation;
    boolean running;

    Ambient(Context c) {
        super(c);
        s = Settings.load(c);
        theme = Theme.of(c);
        u = Ui.unit(c);
        android.graphics.Point size = Ui.screen(c);
        screenW = size.x;
        screenH = size.y;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        lowRam = am != null && (am.isLowRamDevice() || am.getMemoryClass() <= 128);
        setBackgroundColor(theme.light && s.mode.equals(MINIMAL) ? 0xFFE9EBF0 : 0xFF000000);
        build();
    }

    int dp(float v) {
        return Math.round(v * u);
    }

    private void build() {
        back = image();
        front = image();
        addView(back, match());
        addView(front, match());
        // One static legibility veil: darker at the bottom (text) and slightly at the top (clock).
        veil = new View(getContext());
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x00000000, 0x00000000, 0x8C000000}));
        addView(veil, match());
        if (s.mode.equals(MINIMAL)) {
            veil.setBackground(theme.light
                    ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFF4F5F8, 0xFFD5D9E2})
                    : new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF101218, 0xFF050507}));
        }

        clock = new ClockView(getContext(), theme);
        clock.configure(s);
        LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int sideM = dp(52), topM = dp(38);
        switch (s.clockPos) {
            case "center": clp.gravity = Gravity.CENTER; break;
            case "top_left": clp.gravity = Gravity.TOP | Gravity.START; clp.leftMargin = sideM; clp.topMargin = topM; break;
            case "bottom_left": clp.gravity = Gravity.BOTTOM | Gravity.START; clp.leftMargin = sideM; clp.bottomMargin = topM; break;
            case "bottom_right": clp.gravity = Gravity.BOTTOM | Gravity.END; clp.rightMargin = sideM; clp.bottomMargin = topM; break;
            default: clp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; clp.topMargin = dp(46); break;
        }
        clock.setTranslationX(dp(s.clockDx));
        clock.setTranslationY(dp(s.clockDy));
        addView(clock, clp);

        boolean minimalLight = s.mode.equals(MINIMAL) && theme.light;
        int ink = minimalLight ? 0xFF111114 : Color.WHITE;

        // Weather: icon, temperature, condition (and place) in one group.
        weatherGroup = new LinearLayout(getContext());
        weatherGroup.setOrientation(LinearLayout.HORIZONTAL);
        weatherGroup.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(getContext());
        weatherGroup.addView(weatherIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        weatherTemp = text(26, "regular", ink, 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(8);
        weatherGroup.addView(weatherTemp, tlp);
        weatherText = text(13, "medium", ink, 0.85f);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.leftMargin = dp(10);
        weatherGroup.addView(weatherText, wlp);
        boolean clockTopLeft = s.clockPos.equals("top_left");
        LayoutParams wglp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (clockTopLeft ? Gravity.END : Gravity.START));
        wglp.leftMargin = wglp.rightMargin = dp(52);
        wglp.topMargin = dp(38);
        addView(weatherGroup, wglp);
        weatherGroup.setVisibility(GONE);

        boolean clockBottomLeft = s.clockPos.equals("bottom_left");
        int infoGravity = Gravity.BOTTOM | (clockBottomLeft ? Gravity.END : Gravity.START);

        // Photo description and credit.
        infoGroup = new LinearLayout(getContext());
        infoGroup.setOrientation(LinearLayout.VERTICAL);
        infoGroup.setGravity(clockBottomLeft ? Gravity.END : Gravity.START);
        infoTitle = text(14, "medium", ink, 0.9f);
        infoTitle.setMaxLines(2);
        infoTitle.setEllipsize(TextUtils.TruncateAt.END);
        infoGroup.addView(infoTitle, new LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT));
        infoCredit = text(11.5f, "regular", ink, 0.6f);
        infoGroup.addView(infoCredit);
        LayoutParams ilp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, infoGravity);
        ilp.leftMargin = ilp.rightMargin = dp(52);
        ilp.bottomMargin = dp(38);
        addView(infoGroup, ilp);
        infoGroup.setVisibility(GONE);

        // Movie: logo (or title), overview, rating and date, kept together.
        movieGroup = new LinearLayout(getContext());
        movieGroup.setOrientation(LinearLayout.VERTICAL);
        movieLogo = new ImageView(getContext());
        movieLogo.setAdjustViewBounds(true);
        movieLogo.setScaleType(ImageView.ScaleType.FIT_START);
        movieLogo.setMaxWidth(dp(280));
        movieLogo.setMaxHeight(dp(96));
        movieGroup.addView(movieLogo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        movieTitle = text(34, "display", Color.WHITE, 1f);
        movieTitle.setMaxLines(2);
        movieTitle.setEllipsize(TextUtils.TruncateAt.END);
        movieGroup.addView(movieTitle, new LinearLayout.LayoutParams(dp(440), ViewGroup.LayoutParams.WRAP_CONTENT));
        movieMeta = text(12.5f, "medium", Color.WHITE, 0.85f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(10);
        movieGroup.addView(movieMeta, mlp);
        movieOverview = text(13, "regular", Color.WHITE, 0.82f);
        movieOverview.setMaxLines(3);
        movieOverview.setEllipsize(TextUtils.TruncateAt.END);
        movieOverview.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = dp(6);
        movieGroup.addView(movieOverview, olp);
        LayoutParams mglp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, infoGravity);
        mglp.leftMargin = mglp.rightMargin = dp(52);
        mglp.bottomMargin = dp(38);
        addView(movieGroup, mglp);
        movieGroup.setVisibility(GONE);

        dim = new View(getContext());
        dim.setBackgroundColor(Color.BLACK);
        dim.setAlpha(Math.max(0, Math.min(70, s.dim)) / 100f);
        addView(dim, match());
    }

    ImageView image() {
        ImageView v = new ImageView(getContext());
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    TextView text(float size, String weight, int color, float alpha) {
        TextView t = new TextView(getContext());
        Ui.textSize(t, size);
        t.setTypeface(Ui.font(getContext(), weight));
        t.setTextColor(color);
        t.setAlpha(alpha);
        if (color == Color.WHITE) t.setShadowLayer(dp(5), 0, dp(1), 0x99000000);
        return t;
    }

    static LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---------------------------------------------------------------- lifecycle

    void start() {
        if (running) return;
        running = true;
        updateWeather();
        Weather.refresh(getContext(), false, () -> handler.post(this::updateWeather));
        switch (s.mode) {
            case MOVIES: startMovies(); break;
            case SINGLE: {
                Wallpapers.Photo p = Wallpapers.byId(getContext(), s.singleId);
                if (p == null) p = Wallpapers.defaultPhoto(getContext());
                photos.add(p);
                showPhoto(0);
                break;
            }
            case MINIMAL: break;
            default:
                photos.addAll(Wallpapers.inCategory(getContext(), s.category));
                if (photos.isEmpty()) photos.addAll(Wallpapers.all(getContext()));
                Collections.shuffle(photos);
                showPhoto(0);
                break;
        }
        if (s.drift) handler.postDelayed(drift, 60000);
        handler.postDelayed(weatherTick, 15 * 60000L);
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        back.setImageDrawable(null);
        front.setImageDrawable(null);
        movieLogo.setImageDrawable(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (running) stop();
    }

    final Runnable advance = this::advanceNow;

    void advanceNow() {
        if (!running) return;
        if (s.mode.equals(MOVIES)) showMovie(index + 1);
        else showPhoto(index + 1);
    }

    final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            Weather.refresh(getContext(), false, () -> handler.post(Ambient.this::updateWeather));
            handler.postDelayed(this, 15 * 60000L);
        }
    };

    void scheduleNext() {
        handler.removeCallbacks(advance);
        boolean rotates = s.mode.equals(PHOTOS) ? photos.size() > 1 : s.mode.equals(MOVIES) && movies.size() > 1;
        if (rotates && s.interval > 0) handler.postDelayed(advance, s.interval * 1000L);
    }

    int decodeWidth() {
        return lowRam ? Math.min(1920, screenW) : screenW;
    }

    // ---------------------------------------------------------------- photos

    void showPhoto(int i) {
        if (photos.isEmpty()) return;
        index = (i + photos.size()) % photos.size();
        Wallpapers.Photo p = photos.get(index);
        int gen = ++generation;
        io.execute(() -> {
            Bitmap d = Wallpapers.decode(getContext(), p, decodeWidth());
            // Not downloaded and no internet: one of Refract's gradients in its place.
            Bitmap b = d != null ? d : MainActivity.makeWallpaperBase(theme.light ? "daylight"
                    : MainActivity.WALL_KEYS[Math.floorMod(index, MainActivity.WALL_KEYS.length)]);
            boolean photo = d != null;
            handler.post(() -> {
                if (gen != generation || !running) return;
                swap(b);
                infoTitle.setText(p.title);
                infoCredit.setText(p.credit());
                infoCredit.setVisibility(s.attribution ? VISIBLE : GONE);
                infoGroup.setVisibility(photo && (s.description || s.attribution) ? VISIBLE : GONE);
                infoTitle.setVisibility(s.description ? VISIBLE : GONE);
                scheduleNext();
            });
        });
    }

    // ---------------------------------------------------------------- movies

    void startMovies() {
        List<Suggestions.Item> c = Movies.cached(getContext(), "trending", true);
        if (c != null) {
            movies = c;
            showMovie(0);
        }
        if (!Movies.configured(getContext())) {
            if (c == null) noMovies("Movies need a TMDB API key. Add one in Settings \u2192 API & Content Services.");
            return;
        }
        io.execute(() -> {
            try {
                List<Suggestions.Item> fresh = Movies.load(getContext(), "trending");
                handler.post(() -> {
                    if (!running) return;
                    boolean first = movies.isEmpty();
                    movies = fresh;
                    if (first) showMovie(0);
                });
            } catch (Exception e) {
                if (c == null) handler.post(() -> noMovies("Movies aren\u2019t available right now. Check the internet connection."));
            }
        });
    }

    void noMovies(String why) {
        movieGroup.setVisibility(VISIBLE);
        movieLogo.setVisibility(GONE);
        movieTitle.setText("Movies");
        movieMeta.setText(why);
        movieOverview.setVisibility(GONE);
    }

    void showMovie(int i) {
        if (movies.isEmpty()) return;
        index = (i + movies.size()) % movies.size();
        Suggestions.Item it = movies.get(index);
        int gen = ++generation;
        io.execute(() -> {
            Bitmap b = Suggestions.image(getContext(), it.backdrop, "w1280", Math.min(1280, screenW));
            String lp = Movies.configured(getContext()) ? Movies.logoFor(getContext(), it) : (it.logo == null ? "" : it.logo);
            Bitmap logo = lp.isEmpty() ? null : Suggestions.image(getContext(), lp, "w500", 500);
            handler.post(() -> {
                if (gen != generation || !running) return;
                if (b != null) swap(b);
                // The logo, title, overview and meta change in the same frame as the artwork.
                movieGroup.setVisibility(VISIBLE);
                movieLogo.setImageBitmap(logo);
                movieLogo.setVisibility(logo != null ? VISIBLE : GONE);
                movieTitle.setText(it.title);
                movieTitle.setVisibility(logo != null ? GONE : VISIBLE);
                movieMeta.setText(Movies.meta(it));
                movieMeta.setVisibility(movieMeta.getText().length() > 0 ? VISIBLE : GONE);
                movieOverview.setText(it.overview);
                movieOverview.setVisibility(s.description && !it.overview.isEmpty() ? VISIBLE : GONE);
                scheduleNext();
            });
        });
    }

    // ---------------------------------------------------------------- transitions

    /** New image over the old: a cross-fade (or a cut on low-RAM TVs / "Cut"), then the old one is dropped. */
    void swap(Bitmap b) {
        ImageView next = back, old = front;
        back = old;
        front = next;
        next.animate().cancel();
        next.setImageBitmap(b);
        bringChildToFront(next);
        bringChildToFront(veil);
        bringChildToFront(clock);
        bringChildToFront(weatherGroup);
        bringChildToFront(infoGroup);
        bringChildToFront(movieGroup);
        bringChildToFront(dim);
        boolean cut = lowRam || s.transition.equals("cut") || old.getDrawable() == null;
        if (cut) {
            next.setAlpha(1f);
            old.setAlpha(0f);
            old.setImageDrawable(null);
            return;
        }
        long d = s.transition.equals("slow") ? 3000 : 1200;
        next.setAlpha(0f);
        float z = s.reduceMotion ? 1f : 1.02f;  // reduce motion: a plain fade, no zoom
        next.setScaleX(z);
        next.setScaleY(z);
        next.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(d).withEndAction(() -> {
            old.setAlpha(0f);
            old.setImageDrawable(null);  // only the shown image stays decoded
        }).start();
    }

    void updateWeather() {
        Weather.Data d = s.weather && Weather.enabled(getContext()) ? Weather.cached(getContext()) : null;
        if (d == null) {
            weatherGroup.setVisibility(GONE);
            return;
        }
        weatherGroup.setVisibility(VISIBLE);
        weatherIcon.setImageBitmap(WeatherIcons.get(d.code, d.day, dp(34)));
        weatherTemp.setText(d.temp + "\u00b0");
        weatherText.setText(Weather.describe(d.code, d.day) + (d.place.isEmpty() ? "" : "\n" + d.place));
    }

    // ---------------------------------------------------------------- burn-in drift

    final java.util.Random rnd = new java.util.Random();

    /**
     * Once a minute every overlay group glides to a new offset inside a small box (+-18 x +-12
     * design units around its home position, which already sits inside the safe area).
     */
    final Runnable drift = new Runnable() {
        @Override public void run() {
            if (!running) return;
            for (View g : new View[]{clock, weatherGroup, infoGroup, movieGroup}) move(g, g == clock);
            handler.postDelayed(this, 60000);
        }
    };

    void move(View g, boolean isClock) {
        float bx = isClock ? dp(s.clockDx) : 0, by = isClock ? dp(s.clockDy) : 0;
        float tx = bx + (rnd.nextFloat() * 2 - 1) * dp(18), ty = by + (rnd.nextFloat() * 2 - 1) * dp(12);
        if (s.reduceMotion) {
            g.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                g.setTranslationX(tx);
                g.setTranslationY(ty);
                g.animate().alpha(1f).setDuration(300).withEndAction(null).start();
            }).start();
        } else {
            g.animate().translationX(tx).translationY(ty).setDuration(3000)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
        }
    }
}
