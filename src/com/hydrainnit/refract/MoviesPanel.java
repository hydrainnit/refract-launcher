package com.hydrainnit.refract;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Movies tab: a full-screen featured backdrop with the film's title logo (or its title in
 * type), TMDB rating, release date, genres, runtime and overview, and rows of posters under it
 * (Trending, Popular, Top Rated, Coming Soon and a few genres). Moving across the posters changes
 * the featured film after a short pause, so scrolling quickly doesn't fetch every backdrop. OK
 * looks the film up in Search, which lists the apps that have it.
 *
 * It shares the Movies Home Theme's model and caches (Movies, Suggestions.image), keeps one
 * backdrop decoded at a time, caps posters at 1/14 of the heap and stops all work when closed.
 */
final class MoviesPanel {
    static final long FEATURE_DELAY_MS = 300;

    final MainActivity a;
    final FrameLayout layer;
    final ImageView backdrop;
    final ImageView logo;
    final TextView title, meta, overview;
    final LinearLayout rows;
    final ScrollView scroll;
    final ExecutorService net = Executors.newSingleThreadExecutor(), img = Executors.newFixedThreadPool(2);
    final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 14)) {
        @Override protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    final List<View> cards = new ArrayList<>();
    Suggestions.Item featured, pending;
    int gen;
    boolean closed;
    View firstCard;

    final Runnable feature = () -> {
        if (pending != null) feature(pending);
    };

    MoviesPanel(MainActivity a) {
        this.a = a;
        layer = new FrameLayout(a);
        layer.setClickable(true);
        layer.setBackgroundColor(0xFF07080B);

        backdrop = new ImageView(a);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layer.addView(backdrop, a.match());
        // Two veils: dark from the left behind the text, and from the bottom behind the rows.
        View side = new View(a);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF207080B, 0xA607080B, 0x0007080B}));
        layer.addView(side, new FrameLayout.LayoutParams(a.screenW * 2 / 3, ViewGroup.LayoutParams.MATCH_PARENT));
        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF07080B, 0xE607080B, 0x0007080B}));
        layer.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.screenH * 3 / 5, Gravity.BOTTOM));

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(a.dockOuter, a.dp(40), 0, 0);
        TextView tab = a.text(13, Ui.font(a, "semibold"), 0.7f);
        tab.setText("MOVIES");
        tab.setLetterSpacing(0.18f);
        info.addView(tab);
        logo = new ImageView(a);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setMaxWidth(a.dp(280));
        logo.setMaxHeight(a.dp(96));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = a.dp(14);
        info.addView(logo, llp);
        title = a.text(34, Ui.font(a, "display"), 1f);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(a.dp(420));
        info.addView(title, llp);
        meta = a.text(13, Ui.font(a, "medium"), 0.9f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = a.dp(10);
        info.addView(meta, mlp);
        overview = a.text(13.5f, Ui.font(a, "regular"), 0.82f);
        overview.setMaxLines(3);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setMaxWidth(a.dp(400));
        overview.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = a.dp(8);
        info.addView(overview, olp);
        layer.addView(info, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(true);
        rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setPadding(0, 0, 0, a.dp(30));
        scroll.addView(rows);
        layer.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.screenH * 47 / 100, Gravity.BOTTOM));

        if (!Movies.configured(a)) {
            noKey();
        } else {
            title.setText("Loading movies\u2026");
            load();
        }
    }

    /** Without a TMDB key there is nothing to show: say so, and offer the place to add one. */
    void noKey() {
        title.setText("Movies need a TMDB key");
        overview.setText("Add your free TMDB API key in Settings \u2192 API & Content Services to see trending, "
                + "popular and upcoming movies with their artwork, ratings and release dates.");
        TextView open = a.text(14, Ui.font(a, "semibold"), 1f);
        open.setText("Open Settings");
        open.setShadowLayer(0, 0, 0, 0);
        open.setGravity(Gravity.CENTER);
        open.setFocusable(true);
        open.setClickable(true);
        open.setPadding(a.dp(22), 0, a.dp(22), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(18));
        bg.setColor(0x33FFFFFF);
        open.setBackground(bg);
        open.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF7FFFFFF : 0x33FFFFFF);
            open.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(180).start();
        });
        open.setOnClickListener(v -> {
            a.closeMovies();
            a.returningFromSettings = true;
            a.startActivity(new Intent(a, SettingsActivity.class).putExtra("page", "api"));
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(36),
                Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = a.dockOuter;
        lp.bottomMargin = a.screenH / 3;
        layer.addView(open, lp);
        firstCard = open;
    }

    /** Collections one after another: cached rows appear at once, the rest as TMDB answers. */
    void load() {
        int g = ++gen;
        net.execute(() -> {
            boolean any = false;
            String error = null;
            for (String[] c : Movies.COLLECTIONS) {
                if (closed || g != gen) return;
                List<Suggestions.Item> items;
                try {
                    items = Movies.load(a, c[0]);
                } catch (Exception e) {
                    error = Suggestions.TmdbException.describe(e);
                    continue;
                }
                if (items == null || items.isEmpty()) continue;
                any = true;
                List<Suggestions.Item> it = items;
                a.handler.post(() -> {
                    if (!closed && g == gen) addRow(c[1], it);
                });
            }
            if (!any) {
                String why = error;
                a.handler.post(() -> {
                    if (closed || g != gen) return;
                    title.setText("Movies aren't available right now");
                    overview.setText(why == null ? "TMDB returned no movies." : why);
                });
            }
        });
    }

    void addRow(String name, List<Suggestions.Item> items) {
        TextView h = a.text(14, Ui.font(a, "semibold"), 0.85f);
        h.setText(name);
        h.setPadding(a.dockOuter, a.dp(10), 0, a.dp(2));
        rows.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(a.dockOuter, a.dp(10), a.dockOuter, a.dp(12));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row);
        int w = a.dp(92), ht = a.dp(138);
        List<View> made = new ArrayList<>();
        for (Suggestions.Item it : items) {
            View card = card(it, w, ht);
            card.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ht);
            lp.rightMargin = a.dp(14);
            row.addView(card, lp);
            made.add(card);
        }
        made.get(0).setNextFocusLeftId(made.get(0).getId());
        View last = made.get(made.size() - 1);
        last.setNextFocusRightId(last.getId());
        cards.addAll(made);
        rows.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ht + a.dp(22)));
        if (firstCard == null) {
            firstCard = made.get(0);
            firstCard.requestFocus();
            feature(items.get(0));
        }
    }

    /** A 2:3 poster; the title in type until the poster arrives (or when TMDB has none). */
    View card(Suggestions.Item it, int w, int h) {
        FrameLayout c = new FrameLayout(a);
        c.setFocusable(true);
        c.setClickable(true);
        c.setBackgroundColor(0x26FFFFFF);
        c.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(8));
            }
        });
        c.setClipToOutline(true);
        TextView fallback = a.text(11.5f, Ui.font(a, "semibold"), 0.85f);
        fallback.setText(it.title);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(a.dp(6), 0, a.dp(6), 0);
        c.addView(fallback, a.match());
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        c.addView(iv, a.match());
        c.setPivotY(h);
        c.setPivotX(w / 2f);
        c.setContentDescription(it.title);
        String path = it.poster == null || it.poster.isEmpty() ? null : it.poster;
        if (path != null) poster(iv, path, w, fallback);
        c.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? a.dp(10) : 0)
                    .setDuration(200).setInterpolator(MainActivity.LIFT).start();
            if (has) {
                pending = it;
                a.handler.removeCallbacks(feature);
                a.handler.postDelayed(feature, FEATURE_DELAY_MS);
            }
        });
        c.setOnClickListener(v -> {
            a.closeMovies();
            a.showSearch(it.title);
        });
        return c;
    }

    void poster(ImageView iv, String path, int width, View fallback) {
        iv.setTag(path);
        Bitmap mem = posters.get(path);
        if (mem != null) {
            iv.setImageBitmap(mem);
            fallback.setVisibility(View.GONE);
            return;
        }
        img.execute(() -> {
            if (closed || !path.equals(iv.getTag())) return;
            Bitmap b = Suggestions.image(a, path, width > a.dp(92) ? "w342" : "w185", width);
            if (b == null) return;
            a.handler.post(() -> {
                if (closed) return;
                posters.put(path, b);
                if (!path.equals(iv.getTag())) return;
                iv.setImageBitmap(b);
                iv.setAlpha(0f);
                iv.animate().alpha(1f).setDuration(160).start();
                fallback.setVisibility(View.GONE);
            });
        });
    }

    /** Shows a film at the top: text at once, then its backdrop, logo and runtime as they arrive. */
    void feature(Suggestions.Item it) {
        if (closed || it == featured) return;
        featured = it;
        title.setText(it.title);
        title.setVisibility(View.VISIBLE);
        logo.setImageDrawable(null);
        logo.setVisibility(View.GONE);
        meta.setText(Movies.meta(it));
        overview.setText(it.overview);
        net.execute(() -> {
            if (closed || featured != it) return;
            String lp = Movies.logoFor(a, it);
            Bitmap lb = lp.isEmpty() ? null : Suggestions.image(a, lp, "original", a.dp(280));
            a.handler.post(() -> {
                if (closed || featured != it || lb == null) return;
                logo.setImageBitmap(lb);
                logo.setVisibility(View.VISIBLE);
                title.setVisibility(View.GONE);
            });
            Movies.details(a, it);
            a.handler.post(() -> {
                if (!closed && featured == it) meta.setText(Movies.meta(it));
            });
        });
        img.execute(() -> {
            if (closed || featured != it) return;
            Bitmap b = Suggestions.image(a, it.backdrop, "original", a.lowRam ? Math.min(1280, a.screenW) : a.screenW);
            a.handler.post(() -> {
                if (closed || featured != it || b == null) return;
                backdrop.setImageBitmap(b);  // the previous backdrop is dropped, not kept
                backdrop.setAlpha(0.4f);
                backdrop.animate().alpha(1f).setDuration(350).start();
            });
        });
    }

    void focusFirst() {
        if (firstCard != null) firstCard.requestFocus();
        else layer.requestFocus();
    }

    boolean dispatch(KeyEvent ev) {
        int k = ev.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (ev.getAction() == KeyEvent.ACTION_UP) a.closeMovies();
            return true;
        }
        // Up from the first row stays in the panel (the featured text has nothing to focus).
        View f = a.getCurrentFocus();
        if (k == KeyEvent.KEYCODE_DPAD_UP && f != null && cards.indexOf(f) >= 0
                && rows.indexOfChild((View) f.getParent().getParent()) == 1) {
            return true;
        }
        return false;
    }

    /** Stops work and frees images; the layer itself is removed by MainActivity. */
    void release() {
        closed = true;
        a.handler.removeCallbacks(feature);
        net.shutdownNow();
        img.shutdownNow();
        posters.evictAll();
        backdrop.setImageDrawable(null);
    }
}
