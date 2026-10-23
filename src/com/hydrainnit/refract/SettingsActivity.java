package com.hydrainnit.refract;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * tvOS-style settings: a main list of sections, each opening its own page, with the page's icon
 * and title on the left and its rows on the right; Back returns to the main list.
 *
 * A TMDB key can also be supplied over adb (stored encrypted, like one typed on the TV):
 *   adb shell am start -n com.hydrainnit.refract/.SettingsActivity --es tmdb_token &lt;token&gt;
 * Every page follows the Light / Dark appearance.
 */
public class SettingsActivity extends Activity {

    LinearLayout list;
    final List<Runnable> refreshers = new ArrayList<>();
    Theme theme;
    final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        theme = Theme.of(this);
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Secrets.put(this, Secrets.TMDB, token.trim());
            Suggestions.changed(this);
            Toast.makeText(this, "TMDB key saved", Toast.LENGTH_SHORT).show();
        }
        build();
        String pg = getIntent().getStringExtra("page");  // adb: --es page "Top Shelf"
        if (pg != null) show(pg.equals("api") ? API : pg);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // tvOS-style pages: the main list opens a page per section; Back returns to the main list.
    static final String ROOT = "root", PROFILES = "Profiles", SHELF = "Top Shelf", PRESENT = "Presentation",
            INPUTS = "Inputs", WEATHER = "Weather", WATCHING = "Continue Watching", HOME = "Home",
            PERMS = "Permissions", ABOUT = "About", APPEARANCE = "Appearance", BACKGROUND = "Background",
            API = "API & Content Services", MOVIES = "Movies", SCREENSAVER = "Screensaver & Clock", ATTRIB = "Attributions";

    FrameLayout leftPane;
    ScrollView scroller;
    String page = ROOT, lastPage = null;
    final java.util.Map<String, android.view.View> navRows = new java.util.HashMap<>();

    void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(theme.pageBackground());
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        leftPane = new FrameLayout(this);
        split.addView(leftPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(0, Ui.px(this, 60), Ui.px(this, 44), Ui.px(this, 60));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(Ui.px(this, 12), 0, Ui.px(this, 12), 0);
        scroller.addView(list);
        split.addView(scroller, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));
        setContentView(root);
        show(ROOT);
    }

    /** Builds one page: its icon and title on the left, its rows on the right. */
    void show(String p) {
        sheet = null;
        lastPage = page;
        page = p;
        list.removeAllViews();
        refreshers.clear();
        navRows.clear();
        buildLeft(p);
        switch (p) {
            case PROFILES: pageProfiles(); break;
            case SHELF: pageShelf(); break;
            case PRESENT: pagePresentation(); break;
            case INPUTS: pageInputs(); break;
            case WEATHER: pageWeather(); break;
            case WATCHING: pageWatching(); break;
            case HOME: pageHome(); break;
            case PERMS: pagePermissions(); break;
            case ABOUT: pageAbout(); break;
            case APPEARANCE: pageAppearance(); break;
            case BACKGROUND: pageBackground(); break;
            case API: pageApi(); break;
            case MOVIES: pageMovies(); break;
            case SCREENSAVER: pageScreensaver(); break;
            case ATTRIB: pageAttributions(); break;
            default: pageRoot(); break;
        }
        scroller.scrollTo(0, 0);
        // Returning to the main list puts focus back on the section you came from.
        android.view.View focus = p.equals(ROOT) && lastPage != null ? navRows.get(lastPage) : null;
        if (focus == null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (list.getChildAt(i).isFocusable()) {
                    focus = list.getChildAt(i);
                    break;
                }
            }
        }
        if (focus != null) focus.requestFocus();
        list.setAlpha(0f);
        list.setTranslationX(Ui.px(this, p.equals(ROOT) ? -16 : 16));
        list.animate().alpha(1f).translationX(0).setDuration(220).start();
    }

    @Override
    public void onBackPressed() {
        if (sheet != null) sheet.dismiss();
        else if (page.equals(ATTRIB)) show(ABOUT);
        else if (page.equals(BACKGROUND)) show(HOME);
        else if (!page.equals(ROOT)) show(ROOT);
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();  // e.g. a permission granted on a system screen
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshAll();
    }

    /** Left side: the page's icon (Refract's glass prism on the main list), its title, and the brand line. */
    ImageView leftIcon;
    void buildLeft(String p) {
        leftPane.removeAllViews();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        android.view.View icon;
        if (p.equals(ROOT)) {
            // Refract's mark: the glass prism tile.
            FrameLayout tile = new FrameLayout(this);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.refract_mark);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(mark, new FrameLayout.LayoutParams(Ui.px(this, 90), Ui.px(this, 90), Gravity.CENTER));
            icon = tile;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pageIcon(p, Ui.px(this, 150), Ui.px(this, 90)));
            iv.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.px(this, 12));
            iv.setBackground(shape);
            icon = iv;
            leftIcon = iv;
        }
        if (!p.equals(ROOT)) icon.setElevation(Ui.px(this, 8));
        left.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 150), Ui.px(this, 90)));

        TextView title = new TextView(this);
        title.setText(p.equals(ROOT) ? "Settings" : p);
        title.setTextColor(theme.text);
        title.setMaxWidth(Ui.px(this, 300));
        title.setTypeface(Ui.font(this, "display"));
        title.setLetterSpacing(-0.02f);
        Ui.textSize(title, p.equals(ROOT) ? 34 : 28);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(this, 22);
        left.addView(title, tlp);

        if (p.equals(ROOT)) {
            TextView by = new TextView(this);
            by.setText("Refract Launcher \u00b7 Made by Hydrainnit");
            by.setTextColor(theme.textDim);
            by.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(by, 13);
            by.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.px(this, 4);
            left.addView(by, blp);
        }
        leftPane.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** A 5:3 icon for a section page: a gradient tile with a white glyph (or the section's own art). */
    android.graphics.Bitmap pageIcon(String p, int w, int h) {
        if (p.equals(PRESENT)) return com.hydrainnit.refract.present.PresentIcon.banner(w, h);
        int[] grad;
        String glyph;
        switch (p) {
            case PROFILES: grad = new int[]{0xFFBF5AF2, 0xFF7D3CCB}; glyph = null; break;
            case SHELF: grad = new int[]{0xFF64D2FF, 0xFF0A84FF}; glyph = "shelf"; break;
            case INPUTS: grad = new int[]{0xFF6E6E73, 0xFF2C2C2E}; glyph = "input"; break;
            case WEATHER: grad = new int[]{0xFF5AC8FA, 0xFF2D6BD8}; glyph = null; break;
            case WATCHING: grad = new int[]{0xFFFF6482, 0xFFD6305A}; glyph = "present"; break;
            case HOME: grad = new int[]{0xFF30D158, 0xFF1E9E48}; glyph = "house"; break;
            case PERMS: grad = new int[]{0xFF8E8E93, 0xFF48484A}; glyph = "shield"; break;
            case APPEARANCE: grad = new int[]{0xFFF2F2F7, 0xFF3A3A3C}; glyph = "appearance"; break;
            case BACKGROUND: grad = new int[]{0xFFFF9F0A, 0xFFD6305A}; glyph = "photo"; break;
            case API: grad = new int[]{0xFF5E5CE6, 0xFF2D2A8C}; glyph = "key"; break;
            case MOVIES: grad = new int[]{0xFFFF453A, 0xFF8E1B14}; glyph = "film"; break;
            case SCREENSAVER: grad = new int[]{0xFF0A84FF, 0xFF0B2A5C}; glyph = "clock"; break;
            case ATTRIB: grad = new int[]{0xFF30D158, 0xFF0E6B33}; glyph = "info"; break;
            default: grad = new int[]{0xFF8E8E93, 0xFF3A3A3C}; glyph = "gear"; break;
        }
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, grad[0], grad[1], android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        android.graphics.Bitmap g;
        if (p.equals(PROFILES)) g = Profiles.avatar(this, Profiles.current(this), (int) (h * 0.62f));
        else if (p.equals(WEATHER)) g = WeatherIcons.get(0, true, (int) (h * 0.7f));
        else g = Glyphs.get(glyph, (int) (h * 0.56f), Color.WHITE);
        c.drawBitmap(g, (w - g.getWidth()) / 2f, (h - g.getHeight()) / 2f, null);
        return b;
    }

    // ---------------------------------------------------------------- pages

    void pageRoot() {
        nav(PROFILES, () -> Profiles.current(this).name);
        nav(APPEARANCE, () -> theme.light ? "Light" : "Dark");
        nav(SHELF, () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_NONE: return "Off";
                default: return "Recommendations & News";
            }
        });
        nav(PRESENT, () -> com.hydrainnit.refract.present.Presenter.enabled(this) ? "On" : "Off");
        if (!Inputs.list(this).isEmpty()) nav(INPUTS, () -> Inputs.showCard(this) ? "On" : "Off");
        nav(WEATHER, () -> !Weather.enabled(this) ? "Off" : Weather.place(this).isEmpty() ? "On" : Weather.place(this));
        nav(WATCHING, () -> !Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "Off" : MediaWatchService.granted(this) ? "On"
                : Perms.listenerSupported(this) ? "Needs permission" : "Not on this TV");
        nav(HOME, this::backgroundName);
        nav(MOVIES, () -> Movies.homeTheme(this) ? "Home theme on" : Movies.configured(this) ? "Movies tab" : "Needs TMDB key");
        nav(SCREENSAVER, this::idleName);
        nav(API, () -> Secrets.has(this, Secrets.TMDB) ? "TMDB " + Secrets.describe(Secrets.status(this, Secrets.TMDB)).toLowerCase()
                : "Not configured");
        nav(PERMS, () -> {
            int missing = (Perms.hasListener(this) || !Perms.listenerSupported(this) ? 0 : 1) + (Perms.hasLocation(this) ? 0 : 1)
                    + (Perms.hasAdmin(this) || !Perms.adminSupported(this) ? 0 : 1) + (Perms.isDefaultHome(this) ? 0 : 1);
            return missing == 0 ? "All allowed" : missing + " to allow";
        });
        nav(ABOUT, () -> "Version " + version());
        row("TV Settings", () -> "", () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));
    }

    void pageProfiles() {
        row("Current profile", () -> Profiles.current(this).name, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("pick_profile", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        row("Show profiles on wake", () -> Profiles.askOnWake(this) ? "On" : "Off",
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
    }

    void pageShelf() {
        row("Show in carousel", () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_NONE: return "Nothing";
                default: return "Both";
            }
        }, () -> {
            String[] keys = {Suggestions.SHELF_BOTH, Suggestions.SHELF_RECS, Suggestions.SHELF_NEWS, Suggestions.SHELF_NONE};
            String[] names = {"Recommendations & News", "Recommendations", "News", "Nothing"};
            int sel = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.shelf(this)));
            dialog("Show in carousel").setSingleChoiceItems(names, sel, (d, w) -> {
                Suggestions.setShelf(this, keys[w]);
                d.dismiss();
                show(SHELF);
            }).show();
        });
        if (Suggestions.showRecs(this)) {
            section("Recommendations");
            row("Source", () -> Suggestions.sourceName(Suggestions.source(this)), this::chooseSource);
            row("Content", () -> {
                String t = Suggestions.types(this);
                return t.equals("movie") ? "Movies" : t.equals("tv") ? "TV Series" : "Movies & Series";
            }, this::chooseTypes);
        }
        if (Suggestions.showRecs(this) && Suggestions.source(this).equals(Suggestions.SRC_STREMIO)) {
            row("Opens in", () -> {
                Suggestions.Provider p = Suggestions.stremioApp(this);
                return Suggestions.installed(this, p.pkg) ? p.name : "Stremio (not installed)";
            }, () -> Toast.makeText(this, "Titles open in Stremio, or in Nuvio when only Nuvio is installed.",
                    Toast.LENGTH_LONG).show());
        } else if (Suggestions.showRecs(this)) {
            row("Streaming services", () -> {
                int n = Suggestions.providers(this).size();
                return n == Suggestions.PROVIDERS.size() ? "All" : n + " selected";
            }, this::chooseProviders);
            row("Languages", () -> {
                Set<String> l = Suggestions.languages(this);
                if (l.isEmpty()) return "Any";
                List<String> names = new ArrayList<>();
                for (int i = 0; i < Suggestions.LANG_CODES.length; i++) {
                    if (l.contains(Suggestions.LANG_CODES[i])) names.add(Suggestions.LANG_NAMES[i]);
                }
                return names.size() > 2 ? names.size() + " languages" : String.join(", ", names);
            }, this::chooseLanguages);
            row("TMDB key", () -> Suggestions.hasToken(this) ? Secrets.describe(Secrets.status(this, Secrets.TMDB)) : "Not configured",
                    () -> show(API));
        }
        if (Suggestions.showNews(this)) {
            section("News");
            row("Newsrooms", () -> {
                int n = News.sources(this).size();
                return n == News.SOURCES.size() ? "All" : n + " selected";
            }, this::chooseNewsrooms);
            row("Local news for", () -> News.city(this).isEmpty() ? "Unknown" : News.city(this), () ->
                    Toast.makeText(this, "Local news follows the weather location (Settings \u2192 Weather \u2192 Location).",
                            Toast.LENGTH_LONG).show());
            row("Update news every", () -> News.refreshMinutes(this) + " minutes", () -> {
                int[] mins = News.REFRESH_CHOICES;
                String[] names = new String[mins.length];
                int sel = 1;
                for (int i = 0; i < mins.length; i++) {
                    names[i] = mins[i] + " minutes" + (mins[i] == 30 ? "  (default)" : "");
                    if (mins[i] == News.refreshMinutes(this)) sel = i;
                }
                dialog("Update news every").setSingleChoiceItems(names, sel, (d, w) -> {
                    Suggestions.prefs(this).edit().putInt(News.K_REFRESH, mins[w]).apply();
                    refreshAll();
                    d.dismiss();
                }).show();
            });
        }
        if (Suggestions.enabled(this)) {
            section("Carousel");
            row("Auto-advance", () -> {
                int sec = Suggestions.advanceSeconds(this);
                return sec == 0 ? "Off" : "Every " + sec + " s";
            }, this::chooseAdvance);
            row("Refresh now", () -> "", () -> {
                Suggestions.changed(this);
                Toast.makeText(this, "The carousel will refresh on Home", Toast.LENGTH_SHORT).show();
            });
        }
    }

    void chooseNewsrooms() {
        String[] names = new String[News.SOURCES.size()];
        boolean[] on = new boolean[names.length];
        Set<String> cur = News.sources(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = News.SOURCES.get(i).name;
            on[i] = cur.contains(News.SOURCES.get(i).id);
        }
        dialog("Newsrooms").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) ids.add(News.SOURCES.get(i).id);
                    if (ids.isEmpty()) ids.add("toi");
                    News.setSources(this, ids);
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void pagePresentation() {
        row("Presentation mode", () -> com.hydrainnit.refract.present.Presenter.enabled(this) ? "On" : "Off", () -> {
            boolean on = !com.hydrainnit.refract.present.Presenter.enabled(this);
            com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_ENABLED, on);
            if (on) Toast.makeText(this, "Presentation is on. Send content from a browser.", Toast.LENGTH_LONG).show();
        });
        row("Start on boot", () -> com.hydrainnit.refract.present.Presenter.startOnBoot(this) ? "On" : "Off", () ->
                com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_BOOT,
                        !com.hydrainnit.refract.present.Presenter.startOnBoot(this)));
        row("Keep screen awake", () -> com.hydrainnit.refract.present.Presenter.keepAwake(this) ? "On" : "Off", () ->
                com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_AWAKE,
                        !com.hydrainnit.refract.present.Presenter.keepAwake(this)));
        row("Address & pairing code", () -> com.hydrainnit.refract.present.Presenter.enabled(this)
                ? com.hydrainnit.refract.present.Presenter.address().replace("http://", "") : "Turn on first", this::showPresentInfo);
        row("Start presentation now", () -> "", () -> {
            if (!com.hydrainnit.refract.present.Presenter.enabled(this)) {
                com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_ENABLED, true);
            }
            com.hydrainnit.refract.present.Presenter.open(this);
        });
    }

    void pageInputs() {
        row("Inputs card on Home", () -> Inputs.showCard(this) ? "On" : "Off", () ->
                Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, !Inputs.showCard(this)).apply());
        section("This TV");
        for (Inputs.Input in : Inputs.list(this)) {
            row(in.label, () -> in.disconnected ? "Not connected" : "", () -> {
                if (!Inputs.open(this, in)) Toast.makeText(this, "Can't switch to " + in.label, Toast.LENGTH_SHORT).show();
            });
        }
    }

    void pageWeather() {
        row("Show weather", () -> Weather.enabled(this) ? "On" : "Off", () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
            Weather.changed(this);
        });
        row("Location", () -> Weather.manual(this) ? Weather.place(this)
                : Weather.place(this).isEmpty() ? "Automatic" : "Automatic \u00b7 " + Weather.place(this), this::chooseLocation);
        row("Temperature", () -> {
            String u = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            return u.equals("c") ? "Celsius" : u.equals("f") ? "Fahrenheit" : "Automatic";
        }, () -> {
            String[] keys = {"auto", "c", "f"};
            String[] names = {"Automatic", "Celsius (\u00b0C)", "Fahrenheit (\u00b0F)"};
            String cur = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            int sel = cur.equals("c") ? 1 : cur.equals("f") ? 2 : 0;
            dialog("Temperature").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_UNITS, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Updates");
        row("Source", () -> Weather.sourceName(Weather.source(this)), () -> {
            String[] keys = Weather.SOURCES;
            String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (3 days)"};
            int sel = java.util.Arrays.asList(keys).indexOf(Weather.source(this));
            dialog("Weather source").setSingleChoiceItems(names, Math.max(0, sel), (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_SOURCE, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
                Toast.makeText(this, "If it can't be reached, the other sources are used.", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Update every", () -> Weather.refreshMinutes(this) + " minutes", () -> {
            int[] mins = Weather.REFRESH_CHOICES;
            String[] names = new String[mins.length];
            int sel = 1;
            for (int i = 0; i < mins.length; i++) {
                names[i] = mins[i] + " minutes" + (mins[i] == 15 ? "  (default)" : "");
                if (mins[i] == Weather.refreshMinutes(this)) sel = i;
            }
            dialog("Update weather every").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putInt(Weather.K_REFRESH, mins[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    void pageWatching() {
        row("Show Continue Watching", () -> Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "On" : "Off", () -> {
            boolean on = !Profiles.homePrefs(this).getBoolean("cw_enabled", true);
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", on).apply();
            if (!on) Toast.makeText(this, "Continue Watching titles won\u2019t appear in the carousel", Toast.LENGTH_SHORT).show();
        });
        row("Access", () -> MediaWatchService.granted(this) ? "Allowed"
                : Perms.listenerSupported(this) ? "Allow\u2026" : "Not on this TV", () -> Perms.requestListener(this));
        row("Clear history", () -> "", () -> {
            MediaWatchService.Store.clear(this);
            Toast.makeText(this, "Continue Watching cleared", Toast.LENGTH_SHORT).show();
        });
    }

    void pageHome() {
        row("Home screen", () -> Perms.isDefaultHome(this) ? "Refract Launcher" : "Make Refract the home screen\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Background", this::backgroundName, () -> show(BACKGROUND));
        row("Navigation sounds", () -> Clicks.enabled(this) ? "On" : "Off",
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));
        row("Screensaver & idle Home", this::idleName, () -> show(SCREENSAVER));
        section("Search");
        row("Movies & TV in search", () -> Search.titlesEnabled(this) ? "On" : "Off", () -> {
            Search.prefs(this).edit().putBoolean(Search.K_TITLES, !Search.titlesEnabled(this)).apply();
            refreshAll();
        });
        row("Recent searches", () -> Search.recent(this).isEmpty() ? "None" : "Clear", () -> {
            Search.clearRecent(this);
            refreshAll();
        });
        section("Setup");
        row("Run setup again", () -> "", () -> startActivity(new Intent(this, SetupActivity.class)));
        row("Reset app layout", () -> "", () -> {
            Profiles.homePrefs(this).edit().remove("order").apply();
            Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show();
        });
    }

    void pagePermissions() {
        row("Continue Watching & Do Not Disturb", () -> Perms.hasListener(this) ? "Allowed"
                : Perms.listenerSupported(this) ? "Allow\u2026" : "Not on this TV",
                () -> Perms.requestListener(this));
        row("Power Off & Sleep Timer", () -> Perms.hasAdmin(this) ? "Allowed"
                : Perms.adminSupported(this) ? "Allow\u2026" : "Not on this TV", () -> Perms.requestAdmin(this));
        row("Refract as the home screen", () -> Perms.isDefaultHome(this) ? "Yes" : "Set\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Wi-Fi name in Control Center", () -> Perms.hasLocation(this) ? "Allowed" : "Allow\u2026",
                () -> Perms.requestLocation(this));
    }

    void pageAbout() {
        row("Refract Launcher", () -> "Version " + version(), () -> { });
        row("Made by", () -> "Hydrainnit", () -> { });
        row("Based on", () -> "Hume Launcher by Petrobot", () -> show(ATTRIB));
        row("Weather", () -> Weather.sourceName(Weather.source(this)), () -> { });
        row("News", () -> News.sources(this).size() + " Indian newsrooms", () -> { });
        nav(ATTRIB, () -> "Credits & licences");
    }

    boolean screensaverAllowed() {
        return getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true);
    }

    /** The TV's chosen screensaver (readable without permission), e.g. "Aerial Views". */
    String dreamLabel() {
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        if ("0".equals(Settings.Secure.getString(getContentResolver(), "screensaver_enabled"))) return "Off in TV settings";
        if (comp == null || comp.isEmpty()) return "TV default";
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn == null) return "TV default";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            return "TV default";
        }
    }

    /** Choose / set up the screensaver: the TV's screensaver settings, else the screensaver app itself. */
    void openDreamSettings() {
        if (Suggestions.start(this, new Intent("android.settings.DREAM_SETTINGS"))) return;
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        android.content.ComponentName cn = comp == null ? null : android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn != null) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(cn.getPackageName());
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
            if (l != null && Suggestions.start(this, l)) return;
        }
        Toast.makeText(this, "Choose the screensaver in TV Settings \u2192 Device Preferences \u2192 Screen saver", Toast.LENGTH_LONG).show();
        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void section(String name) {
        TextView t = new TextView(this);
        t.setText(name.toUpperCase());
        t.setTextColor(theme.textFaint);
        t.setTypeface(Ui.font(this, "medium"));
        t.setLetterSpacing(0.06f);
        Ui.textSize(t, 11.5f);
        t.setPadding(Ui.px(this, 14), Ui.px(this, 18), 0, Ui.px(this, 6));
        list.addView(t);
    }

    interface Value {
        String get();
    }

    /** A row that opens a section page: label, current value and a chevron. */
    void nav(String target, Value value) {
        android.view.View r = row(target, () -> value.get() + "   \u203a", () -> show(target));
        navRows.put(target, r);
    }

    /** A settings row: label left, current value right; focus lifts it onto a light plate. */
    android.view.View row(String label, Value value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        int padH = Ui.px(this, 16);
        r.setPadding(padH, 0, padH, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.px(this, 10));
        int rest = theme.light ? 0x8CFFFFFF : 0x14FFFFFF;
        bg.setColor(rest);
        r.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(theme.text);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.rightMargin = Ui.px(this, 16);
        r.addView(l, llp);
        TextView v = new TextView(this);
        v.setTextColor(theme.textDim);
        v.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(v, 15);
        v.setSingleLine(true);
        r.addView(v);
        Runnable refresh = () -> v.setText(value.get());
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? theme.rowFocused : rest);
            l.setTextColor(has ? theme.onFocused : theme.text);
            v.setTextColor(has ? theme.onFocusedDim : theme.textDim);
            view.setElevation(has && theme.light ? Ui.px(this, 6) : 0);
            view.animate().withLayer().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(180).start();
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(this, 40));
        lp.bottomMargin = Ui.px(this, 6);
        list.addView(r, lp);
        return r;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev);
    }

    Sheet dialog(String title) {
        return new Sheet(title);
    }

    Sheet sheet;

    /**
     * A choice, as tvOS shows it: the options replace the page's rows on the right (the current
     * one ticked), the title on the left; OK picks, Back returns to the row you came from. It has
     * the AlertDialog.Builder methods the pages use, so every chooser and text entry renders in
     * place instead of as an Android dialog.
     */
    final class Sheet implements android.content.DialogInterface {
        final String title;
        String message;
        android.view.View view;
        CharSequence[] items;
        int checked = -1;
        boolean[] multi;
        OnClickListener itemClick;
        OnMultiChoiceClickListener multiClick;
        boolean single;
        final List<Object[]> buttons = new ArrayList<>();
        String returnPage;
        int returnIndex;

        Sheet(String title) {
            this.title = title;
        }

        Sheet setTitle(String t) {
            return this;
        }

        Sheet setMessage(CharSequence m) {
            message = m == null ? null : m.toString();
            return this;
        }

        Sheet setView(android.view.View v) {
            view = v;
            return this;
        }

        Sheet setSingleChoiceItems(CharSequence[] names, int sel, OnClickListener l) {
            items = names;
            checked = sel;
            itemClick = l;
            single = true;
            return this;
        }

        Sheet setItems(CharSequence[] names, OnClickListener l) {
            items = names;
            itemClick = l;
            return this;
        }

        Sheet setMultiChoiceItems(CharSequence[] names, boolean[] on, OnMultiChoiceClickListener l) {
            items = names;
            multi = on;
            multiClick = l;
            return this;
        }

        Sheet setPositiveButton(String label, OnClickListener l) {
            buttons.add(new Object[]{label, l, BUTTON_POSITIVE});
            return this;
        }

        Sheet setNeutralButton(String label, OnClickListener l) {
            buttons.add(new Object[]{label, l, BUTTON_NEUTRAL});
            return this;
        }

        Sheet setNegativeButton(String label, OnClickListener l) {
            buttons.add(new Object[]{label, l, BUTTON_NEGATIVE});
            return this;
        }

        Sheet show() {
            returnPage = sheet != null ? sheet.returnPage : page;
            android.view.View f = getCurrentFocus();
            returnIndex = sheet != null ? sheet.returnIndex : f == null ? -1 : list.indexOfChild(f);
            sheet = this;
            list.removeAllViews();
            refreshers.clear();
            buildLeft(returnPage);
            ((TextView) ((LinearLayout) leftPane.getChildAt(0)).getChildAt(1)).setText(title);
            if (message != null) note(message);
            if (view != null) {
                if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
                list.addView(view);
            }
            android.view.View first = null;
            for (int i = 0; items != null && i < items.length; i++) {
                int w = i;
                String label = items[i].toString();
                android.view.View r = row(label, () -> multi != null ? (multi[w] ? "\u2713" : "") : w == checked ? "\u2713" : "", () -> {
                    if (multi != null) {
                        multi[w] = !multi[w];
                        if (multiClick != null) multiClick.onClick(this, w, multi[w]);
                    } else if (single) {
                        checked = w;
                        if (itemClick != null) itemClick.onClick(this, w);
                    } else {
                        dismiss();
                        if (itemClick != null) itemClick.onClick(this, w);
                    }
                });
                if (first == null || w == checked) first = r;
            }
            if (!buttons.isEmpty() && items != null) section(" ");
            for (Object[] b : buttons) {
                OnClickListener l = (OnClickListener) b[1];
                int which = (int) b[2];
                android.view.View r = row((String) b[0], () -> "", () -> {
                    dismiss();
                    if (l != null) l.onClick(this, which);
                });
                if (first == null && view == null) first = r;
            }
            android.view.View focus = first;
            if (view != null) {
                android.view.View in = view.findFocus() != null ? view.findFocus() : firstFocusable(view);
                if (in != null) focus = in;
            }
            if (focus != null) focus.requestFocus();
            scroller.scrollTo(0, 0);
            list.setAlpha(0f);
            list.setTranslationX(Ui.px(SettingsActivity.this, 16));
            list.animate().alpha(1f).translationX(0).setDuration(200).start();
            return this;
        }

        @Override
        public void dismiss() {
            if (sheet != this) return;  // already replaced by another sheet or a page
            sheet = null;
            int at = returnIndex;
            SettingsActivity.this.show(returnPage);
            if (at >= 0 && at < list.getChildCount()) list.getChildAt(at).requestFocus();
        }

        @Override
        public void cancel() {
            dismiss();
        }
    }

    static android.view.View firstFocusable(android.view.View v) {
        if (v.isFocusable()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = firstFocusable(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    /** Downloads what the background and the screensaver need, in the background (nothing if on disk). */
    void fetchPhotos() {
        android.content.Context app = getApplicationContext();
        new Thread(() -> Wallpapers.prefetch(app), "refract-photos").start();
    }

    void refreshAll() {
        for (Runnable x : refreshers) x.run();
    }

    // ---------------------------------------------------------------- Appearance

    void pageAppearance() {
        row("Appearance", () -> theme.light ? "Light" : "Dark", () -> {
            String[] names = {"Dark  (smoked glass, white text)", "Light  (frosted glass, dark text)"};
            dialog("Appearance").setSingleChoiceItems(names, theme.light ? 1 : 0, (d, w) -> {
                d.dismiss();
                if ((w == 1) == theme.light) return;
                Theme.set(this, w == 1);
                recreate();  // every surface re-reads the tokens; Home restyles when it resumes
            }).show();
        });
        note("Applies to Home, Control Center, Movies, Setup, Settings and the screensaver.");
    }

    // ---------------------------------------------------------------- Background

    String backgroundName() {
        Wallpapers.Photo p = Wallpapers.selected(this);
        if (p != null) return p.title.length() > 28 ? Wallpapers.categoryName(this, p.category) : p.title;
        return "Gradient";
    }

    /**
     * Bundled photographs by category. Moving through the rows previews the photo on the left
     * (decoded small, off the UI thread, newest request wins); OK makes it this profile's background.
     */
    String previewId;

    void pageBackground() {
        Wallpapers.Photo cur = Wallpapers.selected(this);
        note("Shown behind your apps when you move down to them. Photos from Pexels, credited on screen and in Attributions. "
                + "They download to the TV when first shown; without the internet, Refract's gradients appear instead. "
                + "4K is sharper only on TVs that draw apps in 4K.");
        row("Photo Quality", () -> Wallpapers.quality(this).equals("2160") ? "4K" : "1080p", () -> {
            String[] keys = {"1080", "2160"};
            String[] names = {"1080p  (about 0.4 MB a photo)", "4K  (about 1.5 MB a photo)"};
            int sel = Wallpapers.quality(this).equals("2160") ? 1 : 0;
            dialog("Photo quality").setSingleChoiceItems(names, sel, (d, w) -> {
                if (!keys[w].equals(Wallpapers.quality(this))) {
                    Wallpapers.setQuality(this, keys[w]);
                    // Fetch the new quality now; the old files are removed as each one is replaced.
                    fetchPhotos();
                }
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Downloaded", () -> {
            long b = Wallpapers.bytesOnDisk(this);
            return b == 0 ? "None yet" : String.format(java.util.Locale.US, "%.1f MB", b / 1048576f);
        }, () -> { });
        for (Wallpapers.Category c : Wallpapers.categories(this)) {
            List<Wallpapers.Photo> in = Wallpapers.inCategory(this, c.id);
            if (in.isEmpty()) continue;
            section(c.name);
            for (Wallpapers.Photo p : in) {
                android.view.View r = row(p.title, () -> {
                    Wallpapers.Photo now = Wallpapers.selected(this);
                    return now != null && now.id.equals(p.id) ? "\u2713" : (p.theme.equals("light") ? "Light" : "Dark");
                }, () -> {
                    Wallpapers.select(this, p);
                    boolean ready = Wallpapers.downloaded(this, p) || Wallpapers.online(this);
                    Toast.makeText(this, ready ? "Background set \u00b7 " + p.credit()
                            : "Background set \u00b7 it downloads when the TV is online; a gradient shows until then", Toast.LENGTH_SHORT).show();
                    fetchPhotos();
                });
                android.view.View.OnFocusChangeListener base = r.getOnFocusChangeListener();
                r.setOnFocusChangeListener((v, has) -> {
                    base.onFocusChange(v, has);
                    if (has) previewPhoto(p);
                });
                if (cur != null && cur.id.equals(p.id)) handler.post(r::requestFocus);
            }
        }
    }

    void previewPhoto(Wallpapers.Photo p) {
        previewId = p.id;
        ImageView target = leftIcon;
        int w = Ui.px(this, 300);
        worker.execute(() -> {
            if (!p.id.equals(previewId)) return;
            android.graphics.Bitmap b = Wallpapers.decode(this, p, w);
            handler.post(() -> {
                if (b == null || !p.id.equals(previewId) || target != leftIcon || isFinishing()) return;
                target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                target.setImageBitmap(b);
            });
        });
    }

    // ---------------------------------------------------------------- API keys

    /**
     * Optional keys. Values are encrypted with the Android Keystore and never shown in full; the
     * row shows a masked form and the last check. TMDB can be tested (one small request; a network
     * problem or an outage is never reported as a bad key). Pexels is stored but not used or
     * checked while its API is paused.
     */
    void pageApi() {
        section("TMDB");
        row("TMDB key", () -> Secrets.has(this, Secrets.TMDB) ? Secrets.masked(Secrets.get(this, Secrets.TMDB)) : "Not configured",
                () -> editKey(Secrets.TMDB, "TMDB key",
                        "A v3 API key or a v4 read access token, free at themoviedb.org \u2192 Settings \u2192 API."));
        row("Status", () -> Secrets.describe(Secrets.status(this, Secrets.TMDB)), () -> { });
        row("Test TMDB key", () -> "", this::testTmdb);
        section("Pexels");
        row("Pexels key", () -> Secrets.has(this, Secrets.PEXELS) ? Secrets.masked(Secrets.get(this, Secrets.PEXELS)) : "Not configured",
                () -> editKey(Secrets.PEXELS, "Pexels key", "Saved for when the Pexels API is available again. It isn't checked or used now."));
        row("Status", () -> Wallpapers.PEXELS_API_PAUSED && Secrets.has(this, Secrets.PEXELS) ? "Saved \u00b7 API paused"
                : Secrets.describe(Secrets.status(this, Secrets.PEXELS)), () -> { });
        section("Privacy");
        note("Keys stay on this TV, encrypted with its keystore, and are sent only to TMDB or Pexels. Like any key kept "
                + "in an app, a determined owner of the device could extract it, so use a free personal key.");
    }

    void editKey(String name, String title, String help) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(Secrets.has(this, name) ? Secrets.masked(Secrets.get(this, name)) + "  (saved)" : "Paste or type the key");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.CheckBox reveal = new android.widget.CheckBox(this);
        reveal.setText("Show while typing");
        reveal.setOnCheckedChangeListener((b, on) -> {
            in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (on ? android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD));
            in.setSelection(in.getText().length());
        });
        int pad = Ui.px(this, 14);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        box.addView(reveal);
        Sheet b = dialog(title).setMessage(help).setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String v = in.getText().toString().trim();
                    if (v.isEmpty()) return;
                    Secrets.put(this, name, v);
                    if (name.equals(Secrets.TMDB)) {
                        Suggestions.changed(this);
                        testTmdb();
                    }
                    refreshAll();
                })
                .setNegativeButton("Cancel", null);
        if (Secrets.has(this, name)) {
            b.setNeutralButton("Remove", (d, w) -> {
                Secrets.put(this, name, "");
                if (name.equals(Secrets.TMDB)) Suggestions.changed(this);
                refreshAll();
            });
        }
        b.show();
        in.requestFocus();
    }

    void testTmdb() {
        if (!Secrets.has(this, Secrets.TMDB)) {
            Toast.makeText(this, "Add a TMDB key first", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Checking with TMDB\u2026", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            String st = Suggestions.testKey(this);
            handler.post(() -> {
                if (isFinishing()) return;
                refreshAll();
                Toast.makeText(this, "TMDB: " + Secrets.describe(st), Toast.LENGTH_LONG).show();
            });
        });
    }

    // ---------------------------------------------------------------- Movies

    void pageMovies() {
        row("Movies Home Theme", () -> Movies.homeTheme(this) ? "On" : "Off", () -> {
            boolean on = !Movies.homeTheme(this);
            Movies.setHomeTheme(this, on);
            if (on && !Movies.configured(this)) Toast.makeText(this, "Add a TMDB key to see movies", Toast.LENGTH_LONG).show();
        });
        note("Featured films fill Home behind your apps, with the title logo, overview, TMDB rating and release date. "
                + "News and recommendations are hidden while it is on and come back as they were when it is off.");
        row("Change featured film", () -> {
            int s = Movies.rotateSeconds(this);
            return s == 0 ? "Only when I move" : "Every " + s + " s";
        }, () -> {
            int[] secs = {0, 10, 20, 30, 60};
            String[] names = {"Only when I move  (default)", "Every 10 seconds", "Every 20 seconds", "Every 30 seconds", "Every minute"};
            int sel = 0;
            for (int i = 0; i < secs.length; i++) if (secs[i] == Movies.rotateSeconds(this)) sel = i;
            dialog("Change featured film").setSingleChoiceItems(names, sel, (d, w) -> {
                Profiles.homePrefs(this).edit().putInt(Movies.K_ROTATE, secs[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("TMDB key", () -> Movies.configured(this) ? Secrets.describe(Secrets.status(this, Secrets.TMDB)) : "Not configured",
                () -> show(API));
        row("Clear movie cache", () -> "", () -> {
            for (String[] c : Movies.COLLECTIONS) Movies.cacheFile(this, c[0]).delete();
            Suggestions.changed(this);
            Toast.makeText(this, "Movies will be fetched again", Toast.LENGTH_SHORT).show();
        });
    }

    // ---------------------------------------------------------------- Screensaver & clock

    String idleName() {
        if (screensaverAllowed()) return "System screensaver";
        return Ambient.IDLE_AMBIENT.equals(Ambient.prefs(this).getString(Ambient.K_IDLE, Ambient.IDLE_CAROUSEL))
                ? "Refract Ambient" : "Refract carousel";
    }

    void choice(String label, String key, String def, String[] keys, String[] names) {
        row(label, () -> {
            int i = java.util.Arrays.asList(keys).indexOf(Ambient.prefs(this).getString(key, def));
            return names[Math.max(0, i)];
        }, () -> {
            int i = Math.max(0, java.util.Arrays.asList(keys).indexOf(Ambient.prefs(this).getString(key, def)));
            dialog(label).setSingleChoiceItems(names, i, (d, w) -> {
                Ambient.prefs(this).edit().putString(key, keys[w]).apply();
                fetchPhotos();  // a photo screensaver gets its whole category on the TV
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    void intChoice(String label, String key, int def, int[] vals, String[] names) {
        row(label, () -> {
            int v = Ambient.prefs(this).getInt(key, def);
            for (int i = 0; i < vals.length; i++) if (vals[i] == v) return names[i];
            return String.valueOf(v);
        }, () -> {
            int v = Ambient.prefs(this).getInt(key, def), sel = 0;
            for (int i = 0; i < vals.length; i++) if (vals[i] == v) sel = i;
            dialog(label).setSingleChoiceItems(names, sel, (d, w) -> {
                Ambient.prefs(this).edit().putInt(key, vals[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    void toggle(String label, String key, boolean def) {
        row(label, () -> Ambient.prefs(this).getBoolean(key, def) ? "On" : "Off",
                () -> Ambient.prefs(this).edit().putBoolean(key, !Ambient.prefs(this).getBoolean(key, def)).apply());
    }

    void pageScreensaver() {
        row("When Home is idle", this::idleName, () -> {
            String[] names = {"Refract Ambient  (photos or movies with a clock)", "Refract full-screen carousel",
                    "System screensaver  (" + dreamLabel() + ")"};
            int sel = screensaverAllowed() ? 2 : idleName().equals("Refract Ambient") ? 0 : 1;
            dialog("When Home is idle").setSingleChoiceItems(names, sel, (d, w) -> {
                getSharedPreferences("screensaver", MODE_PRIVATE).edit().putBoolean("allow", w == 2).apply();
                Ambient.prefs(this).edit().putString(Ambient.K_IDLE, w == 0 ? Ambient.IDLE_AMBIENT : Ambient.IDLE_CAROUSEL).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Start after", () -> {
            int sec = Profiles.homePrefs(this).getInt("idle_sec", 20);
            return sec == 0 ? "Off" : sec + " seconds";
        }, () -> {
            int[] secs = {0, 10, 20, 30, 60, 300};
            String[] names = {"Off", "10 seconds", "20 seconds  (default)", "30 seconds", "1 minute", "5 minutes"};
            int cur = Profiles.homePrefs(this).getInt("idle_sec", 20), sel = 2;
            for (int i = 0; i < secs.length; i++) if (secs[i] == cur) sel = i;
            dialog("Start after").setSingleChoiceItems(names, sel, (d, w) -> {
                Profiles.homePrefs(this).edit().putInt("idle_sec", secs[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Preview Refract Ambient", () -> "", () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("panel", "ambient").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        section("Refract Ambient");
        choice("Show", "mode", Ambient.PHOTOS, Ambient.MODES, Ambient.MODE_NAMES);
        row("Photo category", () -> Ambient.prefs(this).getBoolean("follow", true) ? "Same as background"
                : Wallpapers.categoryName(this, Ambient.prefs(this).getString("category", Wallpapers.category(this))), () -> {
            List<Wallpapers.Category> cats = Wallpapers.categories(this);
            String[] names = new String[cats.size() + 1];
            names[0] = "Same as background";
            for (int i = 0; i < cats.size(); i++) names[i + 1] = cats.get(i).name;
            dialog("Photo category").setItems(names, (d, w) -> {
                if (w == 0) Ambient.prefs(this).edit().putBoolean("follow", true).apply();
                else Ambient.prefs(this).edit().putBoolean("follow", false).putString("category", cats.get(w - 1).id).apply();
                fetchPhotos();  // a photo screensaver gets its whole category on the TV
                refreshAll();
            }).show();
        });
        row("Single photo", () -> {
            Wallpapers.Photo p = Wallpapers.byId(this, Ambient.Settings.load(this).singleId);
            return p == null ? "" : p.title;
        }, () -> {
            List<Wallpapers.Photo> all = Wallpapers.all(this);
            String[] names = new String[all.size()];
            for (int i = 0; i < names.length; i++) names[i] = all.get(i).title;
            dialog("Single photo").setItems(names, (d, w) -> {
                Ambient.prefs(this).edit().putString("single", all.get(w).id).apply();
                fetchPhotos();  // a photo screensaver gets its whole category on the TV
                refreshAll();
            }).show();
        });
        intChoice("Change every", "interval", 30, new int[]{15, 30, 60, 120, 300},
                new String[]{"15 seconds", "30 seconds  (default)", "1 minute", "2 minutes", "5 minutes"});
        choice("Transition", "transition", "fade", new String[]{"fade", "slow", "cut"}, new String[]{"Cross-fade", "Slow fade", "Cut"});
        toggle("Weather", "weather", true);
        toggle("Description", "description", true);
        toggle("Photo credit", "attribution", true);
        toggle("Reduce motion", "reduce_motion", false);
        toggle("Move content slightly (burn-in care)", "drift", true);
        intChoice("Dim", "dim", 0, new int[]{0, 20, 40, 60}, new String[]{"Off", "20%", "40%", "60%"});
        section("Clock");
        choice("Style", "clock_style", ClockView.CINEMATIC, ClockView.STYLES, ClockView.STYLE_NAMES);
        choice("Font", "clock_font", "thin", ClockView.FONTS, ClockView.FONT_NAMES);
        choice("Position", "clock_pos", "top", Ambient.POSITIONS, Ambient.POSITION_NAMES);
        intChoice("Size", "clock_size", 100, new int[]{60, 80, 100, 120, 140}, new String[]{"Small", "Medium", "Large  (default)", "Larger", "Huge"});
        intChoice("Opacity", "clock_opacity", 90, new int[]{50, 70, 90, 100}, new String[]{"50%", "70%", "90%  (default)", "100%"});
        intChoice("Letter spacing", "clock_spacing", -2, new int[]{-6, -2, 0, 4}, new String[]{"Tight", "Default", "Normal", "Wide"});
        intChoice("Nudge sideways", "clock_dx", 0, new int[]{-60, -30, 0, 30, 60}, new String[]{"Left 60", "Left 30", "None", "Right 30", "Right 60"});
        intChoice("Nudge up / down", "clock_dy", 0, new int[]{-40, -20, 0, 20, 40}, new String[]{"Up 40", "Up 20", "None", "Down 20", "Down 40"});
        choice("Hours", "clock_24", "auto", new String[]{"auto", "12", "24"}, new String[]{"Same as the TV", "12-hour", "24-hour"});
        toggle("Seconds", "clock_seconds", false);
        toggle("Date", "clock_date", true);
        section("System screensaver");
        row("Screensaver", () -> dreamLabel(), this::openDreamSettings);
        note("To use Refract Ambient as the TV's own screensaver, choose \u201cRefract Ambient\u201d there.");
        row("Start screensaver now", () -> "", () -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Suggestions.start(this, i)) Toast.makeText(this, "This TV can't start the screensaver from an app", Toast.LENGTH_LONG).show();
        });
    }

    // ---------------------------------------------------------------- Attributions

    /** Every credit in one place: the original app, fonts, code, photographs, data and services. */
    void pageAttributions() {
        section("Original software");
        credit("Hume Launcher", "Petrobot",
                "Refract Launcher is a fork of Hume Launcher, Copyright 2026 Petrobot, licensed under the Apache License 2.0. "
                        + "The original licence and NOTICE are kept in the source. Thank you, Petrobot.");
        section("Fonts & code");
        credit("Inter typeface", "The Inter Project Authors \u00b7 OFL 1.1", "SIL Open Font License 1.1 (assets/fonts/Inter-LICENSE.txt).");
        credit("QR Code generator (Java)", "Project Nayuki \u00b7 MIT", "Copyright (c) Project Nayuki, MIT License.");
        section("Photographs");
        for (Wallpapers.Photo p : Wallpapers.all(this)) {
            credit(p.title, p.photographer + " on Pexels", p.credit() + "\n" + (p.sourceUrl == null ? "" : p.sourceUrl)
                    + "\nFree to use under the Pexels License (pexels.com/license).");
        }
        section("Data & services");
        credit("TMDB", "Movie data & artwork", "This product uses the TMDB API but is not endorsed or certified by TMDB. "
                + "Movie titles, overviews, ratings, release dates, logos and images come from The Movie Database (themoviedb.org).");
        credit("JustWatch", "Where to watch", "Streaming availability and recommendations from JustWatch (justwatch.com).");
        credit("Open-Meteo", "Weather \u00b7 CC BY 4.0", "Weather data by Open-Meteo.com, licensed under CC BY 4.0.");
        credit("MET Norway", "Weather \u00b7 CC BY 4.0", "Weather data from the Norwegian Meteorological Institute (api.met.no), CC BY 4.0.");
        credit("wttr.in", "Weather", "Weather from wttr.in by Igor Chubin.");
        credit("Indian newsrooms", News.sources(this).size() + " sources", "Headlines link to and belong to their publishers.");
        section("Brand");
        credit("Refract logo & banner", "Hydrainnit", "Original artwork for Refract Launcher (tools/brand.py).");
    }

    void credit(String name, String by, String detail) {
        row(name, () -> by, () -> dialog(name).setMessage(detail).setPositiveButton("Done", null).show());
    }

    /** A line of explanation between rows. */
    void note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.textDim);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 12.5f);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(Ui.px(this, 14), Ui.px(this, 4), Ui.px(this, 14), Ui.px(this, 10));
        list.addView(t);
    }

    void chooseSource() {
        String[] keys = {Suggestions.SRC_JUSTWATCH, Suggestions.SRC_TMDB, Suggestions.SRC_STREMIO};
        String[] names = {"JustWatch  (no setup needed)", "TMDB  (needs a free token)", "Stremio  (opens in Stremio or Nuvio)"};
        int cur = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.source(this)));
        dialog("Recommendation source").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_SOURCE, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
            show(SHELF);
            if (w == 1 && !Suggestions.hasToken(this)) show(API);
        }).show();
    }

    /** Automatic (from the internet address) or a city found by name. */
    void chooseLocation() {
        String[] names = {"Automatic", "Choose City\u2026"};
        dialog("Weather location").setSingleChoiceItems(names, Weather.manual(this) ? 1 : 0, (d, w) -> {
            d.dismiss();
            if (w == 0) {
                Weather.setPlace(this, null);
                refreshAll();
            } else {
                searchCity();
            }
        }).show();
    }

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("City name, e.g. Bengaluru");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("Choose City")
                .setView(box)
                .setPositiveButton("Search", (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCities(q, res));
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null) {
            Toast.makeText(this, "Couldn't search. Check the internet connection.", Toast.LENGTH_LONG).show();
            return;
        }
        if (res.isEmpty()) {
            Toast.makeText(this, "No city called \u201c" + q + "\u201d", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        dialog("Choose City").setItems(names, (d, w) -> {
            Weather.setPlace(this, res.get(w));
            refreshAll();
        }).setNegativeButton("Cancel", null).show();
    }

    /** Address, QR and pairing code for sending content from a phone or computer. */
    void showPresentInfo() {
        if (!com.hydrainnit.refract.present.Presenter.enabled(this)) {
            Toast.makeText(this, "Turn on Presentation mode first", Toast.LENGTH_SHORT).show();
            return;
        }
        com.hydrainnit.refract.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.px(this, 16);
        box.setPadding(pad, pad, pad, pad / 2);
        android.widget.ImageView qr = new android.widget.ImageView(this);
        qr.setImageBitmap(com.hydrainnit.refract.present.Presenter.qr(Ui.px(this, 110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6));
        box.addView(qr, new LinearLayout.LayoutParams(Ui.px(this, 122), Ui.px(this, 122)));
        TextView info = new TextView(this);
        String pin = com.hydrainnit.refract.present.Presenter.pin(this);
        info.setText("Open on a phone or computer on the same Wi-Fi:\n\n"
                + com.hydrainnit.refract.present.Presenter.address() + "\n\nPairing code\n"
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(theme.text);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPadding(Ui.px(this, 18), 0, 0, 0);
        box.addView(info);
        dialog("Send to this TV").setView(box).setPositiveButton("Done", null).show();
    }

    void chooseTypes() {
        String[] keys = {"both", "movie", "tv"};
        String[] names = {"Movies & Series", "Movies", "TV Series"};
        int cur = java.util.Arrays.asList(keys).indexOf(Suggestions.types(this));
        dialog("Content").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TYPES, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
        }).show();
    }

    void chooseProviders() {
        int n = Suggestions.PROVIDERS.size();
        String[] names = new String[n];
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.providers(this);
        for (int i = 0; i < n; i++) {
            Suggestions.Provider p = Suggestions.PROVIDERS.get(i);
            names[i] = p.name + (Suggestions.installed(this, p.pkg) ? "" : "  (not installed)");
            on[i] = cur.contains(String.valueOf(p.id));
        }
        dialog("Streaming services").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(String.valueOf(Suggestions.PROVIDERS.get(i).id));
                    if (sel.isEmpty()) {
                        Toast.makeText(this, "Keep at least one service", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_PROVIDERS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void chooseLanguages() {
        int n = Suggestions.LANG_CODES.length;
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.languages(this);
        for (int i = 0; i < n; i++) on[i] = cur.contains(Suggestions.LANG_CODES[i]);
        dialog(Suggestions.source(this).equals(Suggestions.SRC_TMDB) ? "Languages (none = any)" : "Languages (TMDB source only)")
                .setMultiChoiceItems(Suggestions.LANG_NAMES, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Done", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(Suggestions.LANG_CODES[i]);
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_LANGS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Cancel", null).show();
    }

    void chooseAdvance() {
        int[] secs = {0, 6, 8, 12};
        String[] names = {"Off", "Every 6 s", "Every 8 s", "Every 12 s"};
        int cur = 0;
        for (int i = 0; i < secs.length; i++) if (secs[i] == Suggestions.advanceSeconds(this)) cur = i;
        dialog("Auto-advance").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putInt(Suggestions.K_ADVANCE, secs[w]).apply();
            refreshAll();
            d.dismiss();
        }).show();
    }
}
