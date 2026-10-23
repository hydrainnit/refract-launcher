package com.hydrainnit.refract;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * First-time setup, in the style of Apple TV's own setup: one question per page, a large title
 * and a short line of help at the top, choices as a centred list (white when focused), OK moves
 * on and Back goes back. Pages: Welcome (in many languages), appearance (Light or Dark), your
 * profile, background photo, Home Screen content (incl. the Movies theme), optional API keys,
 * weather, presentation, home screen & permissions, done. Shown once; Settings \u2192 Home \u2192
 * Run Setup Again repeats it. Every page follows the chosen appearance as soon as it is picked.
 */
public class SetupActivity extends Activity {
    static final String K_DONE = "setup_done";

    FrameLayout root;
    ImageView backdrop;
    LinearLayout page;
    View dim;
    int index = 0, weatherStep, permsStep;
    Theme theme;
    final Handler handler = new Handler(Looper.getMainLooper());
    final java.util.concurrent.ExecutorService decoder = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** The photo being previewed (newest request wins) and the category opened on the background page. */
    String previewId;
    String lookCategory;

    interface Step {
        void build();
    }

    final List<Step> steps = new ArrayList<>();

    static boolean done(Activity a) {
        return a.getSharedPreferences("setup", MODE_PRIVATE).getBoolean(K_DONE, false);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        theme = Theme.of(this);
        root = new FrameLayout(this);
        root.setBackground(theme.pageBackground());
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim = new View(this);
        paintDim();
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        page.setPadding(0, u(54), 0, u(28));
        root.addView(page, plp);
        setContentView(root);

        steps.add(this::hello);
        steps.add(this::appearance);
        steps.add(this::profile);
        steps.add(this::look);
        steps.add(this::homeContent);
        steps.add(this::apiKeys);
        weatherStep = steps.size();
        steps.add(this::weather);
        steps.add(this::presentation);
        permsStep = steps.size();
        steps.add(this::homeAndPermissions);
        steps.add(this::finish_);
        if (getIntent().getBooleanExtra("skip", false)) {  // adb: --ez skip true marks setup as done
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
            finish();
            return;
        }
        show(getIntent().getIntExtra("step", 0), 0);  // adb: --ei step N opens a page directly
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.getBooleanExtra("skip", false)) {  // adb hook, also when setup is already open
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
            finish();
        } else if (i.hasExtra("step")) {
            show(i.getIntExtra("step", 0), 0);
        }
    }

        int u(float v) {
        return Ui.px(this, v);
    }

    // ---------------------------------------------------------------- navigation

    void show(int i, int dir) {
        handler.removeCallbacksAndMessages(null);
        index = Math.max(0, Math.min(i, steps.size() - 1));
        page.animate().cancel();
        page.removeAllViews();
        steps.get(index).build();
        // New page: slide and fade. Same page re-drawn after a switch: a quick fade only.
        page.setAlpha(dir == 0 ? 0.6f : 0f);
        page.setTranslationX(u(30) * dir);
        page.animate().alpha(1f).translationX(0).setDuration(dir == 0 ? 120 : 320).start();
    }

    void next() {
        show(index + 1, 1);
    }

    @Override
    public void onBackPressed() {
        if (lookCategory != null && index == 3) {  // a category's photos: Back returns to the categories
            lookCategory = null;
            show(index, 0);
            return;
        }
        if (index > 0) show(index - 1, -1);
        // The first page has nowhere to go back to: setup stays until it is finished.
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (index == permsStep) show(permsStep, 0);  // refresh the ticks
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (index == permsStep) show(permsStep, 0);
    }

    /** The veil over the preview photo: darkens it in Dark, frosts it in Light, so text stays legible. */
    void paintDim() {
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, theme.light
                ? new int[]{0xB3F4F4F7, 0x80F4F4F7, 0xCCF4F4F7} : new int[]{0x99000000, 0x66000000, 0xB3000000}));
    }

    // ---------------------------------------------------------------- building blocks

    TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.text);
        t.setTypeface(Ui.font(this, "display"));
        t.setLetterSpacing(-0.02f);
        Ui.textSize(t, 34);
        t.setGravity(Gravity.CENTER);
        page.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    TextView subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.textDim);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 14);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(520), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = u(10);
        lp.bottomMargin = u(26);
        page.addView(t, lp);
        return t;
    }

    LinearLayout list() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        if (page.findViewWithTag("list") == null) l.setTag("list");
        page.addView(l, new LinearLayout.LayoutParams(u(440), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** A choice row: title, optional detail and a tick; white with dark text when focused. */
    View option(LinearLayout list, String label, String detail, boolean checked, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(u(18), u(8), u(16), u(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(12));
        int rest = theme.light ? 0x99FFFFFF : 0x26FFFFFF;
        bg.setColor(rest);
        r.setBackground(bg);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(theme.text);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        texts.addView(l);
        TextView d = null;
        if (detail != null && !detail.isEmpty()) {
            d = new TextView(this);
            d.setText(detail);
            d.setTextColor(theme.textDim);
            d.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(d, 12);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tick = new TextView(this);
        tick.setText(checked ? "\u2713" : "");
        tick.setTextColor(theme.tick);
        tick.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(tick, 17);
        r.addView(tick);
        TextView dRef = d;
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? theme.rowFocused : rest);
            l.setTextColor(has ? theme.onFocused : theme.text);
            if (dRef != null) dRef.setTextColor(has ? theme.onFocusedDim : theme.textDim);
            tick.setTextColor(has ? 0xFF1E9E48 : theme.tick);
            v.setElevation(has && theme.light ? u(8) : 0);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(180).start();
        });
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                d == null ? u(46) : u(56));
        lp.bottomMargin = u(8);
        list.addView(r, lp);
        if (list.getChildCount() == 1) r.requestFocus();
        return r;
    }

    void dots() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i < steps.size() - 1; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == index ? theme.text : theme.textFaint);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(6), u(6));
            lp.leftMargin = lp.rightMargin = u(4);
            d.addView(dot, lp);
        }
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        page.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Previews a bundled photo behind the page; decoded off the UI thread, newest request wins. */
    void setBackdrop(Wallpapers.Photo p) {
        if (p == null) return;
        previewId = p.id;
        int w = Math.min(1280, getResources().getDisplayMetrics().widthPixels);
        decoder.execute(() -> {
            if (!p.id.equals(previewId)) return;
            Bitmap b = Wallpapers.decode(this, p, w);
            handler.post(() -> {
                if (b == null || !p.id.equals(previewId) || isFinishing()) return;
                backdrop.setImageDrawable(new BitmapDrawable(getResources(), b));  // the old preview is dropped
                backdrop.animate().alpha(1f).setDuration(300).start();
            });
        });
    }

    // ---------------------------------------------------------------- pages

    /** Welcome, then Get Started. */
    void hello() {
        backdrop.animate().alpha(0f).setDuration(300).start();
        View top = new View(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.refract_mark);
        logo.setElevation(u(10));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(u(84), u(84));
        glp.bottomMargin = u(18);
        page.addView(logo, glp);
        logo.setAlpha(0f);
        logo.setScaleX(0.9f);
        logo.setScaleY(0.9f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start();
        TextView hi = title("Welcome");
        Ui.textSize(hi, 64);
        hi.setAlpha(0f);
        hi.setTranslationY(u(12));
        hi.animate().alpha(1f).translationY(0).setDuration(700).start();
        // "Welcome" in Indian and world languages, fading from one to the next.
        String[] words = {"Welcome", "\u0938\u094d\u0935\u093e\u0917\u0924 \u0939\u0948", "\u09b8\u09cd\u09ac\u09be\u0997\u09a4\u09ae", "\u0bb5\u0bb0\u0bb5\u0bc7\u0bb1\u0bcd\u0b95\u0bbf\u0bb1\u0bcb\u0bae\u0bcd", "\u0c38\u0c4d\u0c35\u0c3e\u0c17\u0c24\u0c02", "\u0938\u094d\u0935\u093e\u0917\u0924 \u0906\u0939\u0947", "\u0ab8\u0acd\u0ab5\u0abe\u0a97\u0aa4 \u0a9b\u0ac7", "\u0cb8\u0ccd\u0cb5\u0cbe\u0c97\u0ca4", "\u0d38\u0d4d\u0d35\u0d3e\u0d17\u0d24\u0d02", "\u0a1c\u0a40 \u0a06\u0a07\u0a06\u0a02 \u0a28\u0a42\u0a70", "Bienvenue", "Bienvenido", "Willkommen", "\u3088\u3046\u3053\u305d", "Benvenuto", "Bem-vindo", "\u6b22\u8fce"};
        int[] k = {0};
        Runnable[] cycle = new Runnable[1];
        cycle[0] = () -> {
            k[0] = (k[0] + 1) % words.length;
            hi.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                hi.setText(words[k[0]]);
                hi.animate().alpha(1f).setDuration(450).start();
            }).start();
            handler.postDelayed(cycle[0], 2200);
        };
        handler.postDelayed(cycle[0], 2400);
        TextView s = subtitle("Let\u2019s set up Refract Launcher.");
        Ui.textSize(s, 17);
        LinearLayout l = list();
        option(l, "Get Started", null, false, this::next);
        View mid = new View(this);
        page.addView(mid, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView by = new TextView(this);
        by.setText("Made by Hydrainnit");
        by.setTextColor(theme.textFaint);
        by.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(by, 13);
        by.setGravity(Gravity.CENTER);
        page.addView(by, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * Light or Dark, with a live preview of each: a miniature Home (status pill, glass dock, app
     * cards) drawn from the theme's own tokens. Focusing one restyles the page at once.
     */
    void appearance() {
        title("Choose Your Appearance");
        subtitle("Light is frosted white glass with dark text; Dark is smoked glass with white text. "
                + "You can change it any time in Settings \u2192 Appearance.");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        page.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View dark = previewCard(row, false), light = previewCard(row, true);
        LinearLayout l = list();
        ((LinearLayout.LayoutParams) l.getLayoutParams()).topMargin = u(26);
        option(l, "Continue", null, false, this::next);
        (theme.light ? light : dark).requestFocus();
        dots();
    }

    View previewCard(LinearLayout row, boolean light) {
        Theme t = Theme.of(light);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setClipChildren(false);
        int w = u(220), h = u(124);
        FrameLayout shot = new FrameLayout(this);
        GradientDrawable frame = new GradientDrawable(GradientDrawable.Orientation.TL_BR, light
                ? new int[]{0xFFE9D9C4, 0xFFB9C9DD} : new int[]{0xFF1B2333, 0xFF07080C});
        frame.setCornerRadius(u(14));
        shot.setBackground(frame);
        shot.setClipToOutline(true);
        // status pill
        View pill = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(u(6));
        pg.setColor(light ? 0xB3FFFFFF : 0x80000000);
        pg.setStroke(1, light ? 0xCCFFFFFF : 0x33FFFFFF);
        pill.setBackground(pg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(u(70), u(12), Gravity.TOP | Gravity.END);
        plp.topMargin = plp.rightMargin = u(9);
        shot.addView(pill, plp);
        TextView clock = new TextView(this);
        clock.setText("9:41");
        clock.setTextColor(t.clockTop);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        Ui.textSize(clock, 30);
        clock.setShadowLayer(u(4), 0, u(1), t.clockShadow);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        clp.topMargin = u(18);
        shot.addView(clock, clp);
        // glass dock with app cards
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(u(7), 0, u(7), 0);
        GradientDrawable dg = new GradientDrawable();
        dg.setCornerRadius(u(8));
        dg.setColor(light ? 0x99FFFFFF : 0x59000000);
        dg.setStroke(1, light ? 0xCCFFFFFF : 0x26FFFFFF);
        dock.setBackground(dg);
        int[] apps = {0xFFE50914, 0xFF1A98FF, 0xFFF2F2F2, 0xFF00A8E1};
        for (int c : apps) {
            View a = new View(this);
            GradientDrawable ag = new GradientDrawable();
            ag.setCornerRadius(u(3));
            ag.setColor(c);
            a.setBackground(ag);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, u(22), 1f);
            alp.leftMargin = alp.rightMargin = u(3);
            dock.addView(a, alp);
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(34), Gravity.BOTTOM);
        dlp.leftMargin = dlp.rightMargin = dlp.bottomMargin = u(9);
        shot.addView(dock, dlp);
        col.addView(shot, new LinearLayout.LayoutParams(w, h));
        TextView name = new TextView(this);
        name.setText((theme.light == light ? "\u2713  " : "") + (light ? "Light" : "Dark"));
        name.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(name, 15);
        name.setTextColor(theme.text);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = u(10);
        col.addView(name, nlp);
        View ring = shot;
        col.setOnFocusChangeListener((v, has) -> {
            frame.setStroke(has ? u(3) : 0, theme.light ? 0xFF111114 : Color.WHITE);
            ring.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? u(10) : 0).setDuration(200).start();
            if (has && theme.light != light) {
                // Live: the whole page switches as soon as an appearance is focused.
                Theme.set(this, light);
                theme = Theme.of(light);
                root.setBackground(theme.pageBackground());
                paintDim();
                handler.post(() -> {
                    show(index, 0);
                    View again = page.findViewWithTag(light ? "prev_light" : "prev_dark");
                    if (again != null) again.requestFocus();
                });
            }
        });
        col.setTag(light ? "prev_light" : "prev_dark");
        col.setOnClickListener(v -> next());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = u(22);
        row.addView(col, lp);
        return col;
    }

    void profile() {
        title("Create Your Profile");
        subtitle("Your name and an avatar. Each profile keeps its own apps, background and Home Screen. Up to three people can have one.");
        Profiles.Profile p = Profiles.current(this);
        LinearLayout l = list();
        option(l, "Name", p.name, false, () -> {
            EditText in = new EditText(this);
            in.setSingleLine(true);
            in.setText(p.name);
            in.setSelection(in.getText().length());
            int pad = u(14);
            FrameLayout box = new FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(in);
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Your name").setView(box)
                    .setPositiveButton("OK", (d, w) -> {
                        String n = in.getText().toString().trim();
                        if (!n.isEmpty()) {
                            p.name = n;
                            Profiles.update(this, p);
                        }
                        show(index, 0);
                    }).setNegativeButton("Cancel", null).show();
            in.requestFocus();
        });
        // Avatar: a row of emoji, the current one ringed.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        List<String> choices = new ArrayList<>(Profiles.freeEmojis(this, p.id));
        if (!choices.contains(p.emoji)) choices.add(0, p.emoji);
        for (int i = 0; i < Math.min(8, choices.size()); i++) {
            String e = choices.get(i);
            TextView t = new TextView(this);
            t.setText(e);
            Ui.textSize(t, 26);
            t.setGravity(Gravity.CENTER);
            t.setFocusable(true);
            t.setClickable(true);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(e.equals(p.emoji) ? 0x59FFFFFF : 0x1FFFFFFF);
            t.setBackground(g);
            t.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.2f : 1f).scaleY(has ? 1.2f : 1f).setDuration(160).start());
            t.setOnClickListener(v -> {
                p.emoji = e;
                Profiles.update(this, p);
                show(index, 0);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(46), u(46));
            lp.leftMargin = lp.rightMargin = u(5);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = u(6);
        rlp.bottomMargin = u(14);
        page.addView(row, rlp);
        LinearLayout l2 = list();
        option(l2, "Continue", null, false, this::next);
        dots();
    }

    /**
     * The background photo behind your apps: categories first, then that category's photos.
     * Each previews behind the page as you move to it (the Light/Dark-matched photo first).
     */
    void look() {
        Wallpapers.Photo cur = Wallpapers.selected(this);
        if (lookCategory == null) {
            title("Choose a Background");
            subtitle("Photographs from Pexels, chosen to work in Light and Dark. Move through the list to preview.");
            setBackdrop(cur != null ? cur : Wallpapers.defaultPhoto(this));
            String curCat = cur == null ? "" : cur.category;
            LinearLayout l = list();
            for (Wallpapers.Category c : Wallpapers.categories(this)) {
                List<Wallpapers.Photo> in = Wallpapers.inCategory(this, c.id);
                if (in.isEmpty()) continue;
                Wallpapers.Photo lead = in.get(0);
                for (Wallpapers.Photo p : in) if (p.theme.equals(theme.light ? "light" : "dark")) { lead = p; break; }
                Wallpapers.Photo first = lead;
                View r = option(l, c.name, in.size() == 1 ? "1 photo" : in.size() + " photos", c.id.equals(curCat), () -> {
                    lookCategory = c.id;
                    show(index, 0);
                });
                View.OnFocusChangeListener base = r.getOnFocusChangeListener();
                r.setOnFocusChangeListener((v, has) -> {
                    base.onFocusChange(v, has);
                    if (has) setBackdrop(c.id.equals(curCat) && cur != null ? cur : first);
                });
                if (c.id.equals(curCat)) r.requestFocus();
            }
            scrollable();
        } else {
            title(Wallpapers.categoryName(this, lookCategory));
            subtitle("OK chooses the photo. Back returns to the categories.");
            LinearLayout l = list();
            for (Wallpapers.Photo p : Wallpapers.inCategory(this, lookCategory)) {
                boolean on = cur != null && cur.id.equals(p.id);
                View r = option(l, p.title, p.credit(), on, () -> {
                    Wallpapers.select(this, p);
                    lookCategory = null;
                    next();
                });
                View.OnFocusChangeListener base = r.getOnFocusChangeListener();
                r.setOnFocusChangeListener((v, has) -> {
                    base.onFocusChange(v, has);
                    if (has) setBackdrop(p);
                });
                if (on) r.requestFocus();
            }
            scrollable();
        }
        dots();
    }

    /** Puts the page's list in a scroll view when it is taller than the space left. */
    void scrollable() {
        View v = page.findViewWithTag("list");
        if (!(v instanceof LinearLayout) || ((LinearLayout) v).getChildCount() <= 6) return;
        View had = getCurrentFocus();
        int at = page.indexOfChild(v);
        page.removeView(v);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, u(8), 0, u(8));
        sv.addView(v, new FrameLayout.LayoutParams(u(440), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        // The page doesn't clip (focused rows grow); this frame keeps the scrolled list inside its space.
        FrameLayout clip = new FrameLayout(this);
        clip.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(clip, at, new LinearLayout.LayoutParams(u(470), 0, 3f));
        if (had != null) had.requestFocus();  // moving the list into the scroll view cleared it
    }

    /** This profile's Home Screen: recommendations and/or news on the top shelf, as two switches. */
    void homeContent() {
        backdrop.animate().alpha(0.5f).setDuration(300).start();
        title("Your Home Screen");
        subtitle("What appears at the top of Home, behind your apps. Choose any, all or none.");
        boolean recs = Suggestions.showRecs(this), news = Suggestions.showNews(this);
        LinearLayout l = list();
        option(l, "Recommendations", "Popular movies and series on your streaming services", recs, () -> {
            Suggestions.setShelf(this, !recs, news);
            show(index, 0);
            focusRow(0);
        });
        option(l, "News", "India, your city and the world, from Indian newsrooms", news, () -> {
            Suggestions.setShelf(this, recs, !news);
            show(index, 0);
            focusRow(1);
        });
        boolean cw = Profiles.homePrefs(this).getBoolean("cw_enabled", true);
        option(l, "Continue Watching", "What you were watching, mixed into the carousel", cw, () -> {
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", !cw).apply();
            show(index, 0);
            focusRow(2);
        });
        boolean movies = Movies.homeTheme(this);
        option(l, "Movies Home Theme", "Featured films from TMDB behind your apps, with logo, rating and date (needs a TMDB key)",
                movies, () -> {
                    Movies.setHomeTheme(this, !movies);
                    show(index, 0);
                    focusRow(3);
                });
        option(l, "Continue", recs || news || cw || movies ? null : "Just your background for now", false, this::next);
        dots();
    }

    /** After re-building a page, put focus back on the row that was pressed. */
    void focusRow(int i) {
        View v = page.findViewWithTag("list");
        if (v instanceof LinearLayout && i < ((LinearLayout) v).getChildCount()) ((LinearLayout) v).getChildAt(i).requestFocus();
    }

    /**
     * Optional keys, entered right here: TMDB (movies, artwork, ratings) and Pexels (more
     * photographs, while the API is available). Both can be skipped; nothing in Refract needs them.
     * Keys are hidden while typed unless Show is pressed, saved encrypted with the TV's keystore
     * and never shown in full again. TMDB is checked with one request; Pexels is saved unverified
     * because its API is paused, so a check would fail for reasons that have nothing to do with the key.
     */
    void apiKeys() {
        title("Content & API");
        subtitle("Optional. A free TMDB key adds movie artwork, ratings and the Movies tab. Keys stay on this TV, "
                + "encrypted, and can be changed in Settings \u2192 API & Content Services.");
        LinearLayout l = list();
        keyField(l, Secrets.TMDB, "TMDB API key or read access token");
        keyField(l, Secrets.PEXELS, "Pexels API key (saved, not checked while the API is paused)");
        option(l, "Continue", Secrets.has(this, Secrets.TMDB) || Secrets.has(this, Secrets.PEXELS) ? null : "Skip for now", false, this::next);
        dots();
    }

    void keyField(LinearLayout list, String name, String hint) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint(Secrets.has(this, name) ? Secrets.masked(Secrets.get(this, name)) + "  (saved)" : hint);
        in.setHintTextColor(theme.textFaint);
        in.setTextColor(theme.text);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        in.setTypeface(Ui.font(this, "regular"));  // after the input type, which resets it to monospace
        Ui.textSize(in, 13);
        // The keyboard opens on OK, not as soon as the remote moves onto the field.
        in.setShowSoftInputOnFocus(false);
        in.setOnClickListener(v -> ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(in, 0));
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        in.setPadding(u(14), 0, u(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(12));
        int rest = theme.light ? 0x99FFFFFF : 0x26FFFFFF;
        bg.setColor(rest);
        in.setBackground(bg);
        in.setOnFocusChangeListener((v, has) -> {
            bg.setStroke(has ? u(2) : 0, theme.light ? 0xFF111114 : Color.WHITE);
        });
        r.addView(in, new LinearLayout.LayoutParams(0, u(46), 1f));
        TextView show = smallButton(Secrets.has(this, name) ? "Remove" : "Show");
        boolean[] visible = {false};
        show.setOnClickListener(v -> {
            if (Secrets.has(this, name) && in.getText().length() == 0) {
                Secrets.put(this, name, "");
                show(index, 0);
                return;
            }
            visible[0] = !visible[0];
            in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD));
            in.setTypeface(Ui.font(this, "regular"));
            in.setSelection(in.getText().length());
            show.setText(visible[0] ? "Hide" : "Show");
        });
        in.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (e.length() > 0 && show.getText().equals("Remove")) show.setText("Show");
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u(46));
        slp.leftMargin = u(8);
        r.addView(show, slp);
        TextView save = smallButton("Save");
        TextView status = new TextView(this);
        save.setOnClickListener(v -> {
            String val = in.getText().toString().trim();
            if (val.isEmpty()) return;
            Secrets.put(this, name, val);
            in.setText("");
            in.setHint(Secrets.masked(val) + "  (saved)");
            if (name.equals(Secrets.TMDB)) {
                status.setText("Checking with TMDB\u2026");
                new Thread(() -> {
                    String st = Suggestions.testKey(this);
                    handler.post(() -> status.setText(Secrets.describe(st)));
                }).start();
            } else {
                status.setText(Secrets.describe(Secrets.ST_UNVERIFIED));
            }
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u(46));
        vlp.leftMargin = u(8);
        r.addView(save, vlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(46));
        list.addView(r, lp);
        status.setTextColor(theme.textDim);
        status.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(status, 12);
        String st = Secrets.status(this, name);
        status.setText(Secrets.has(this, name) ? Secrets.describe(st) : "");
        status.setPadding(u(14), u(4), 0, 0);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stl.bottomMargin = u(10);
        list.addView(status, stl);
        if (list.getChildCount() == 2) in.requestFocus();
    }

    TextView smallButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(b, 13);
        b.setTextColor(theme.text);
        b.setPadding(u(16), 0, u(16), 0);
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(12));
        int rest = theme.light ? 0x99FFFFFF : 0x26FFFFFF;
        bg.setColor(rest);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? theme.rowFocused : rest);
            b.setTextColor(has ? theme.onFocused : theme.text);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(160).start();
        });
        return b;
    }

    void weather() {
        title("Weather");
        subtitle("A small weather card in the top-left corner of Home, with a 5-day forecast.");
        LinearLayout l = list();
        String city = Weather.place(this);
        option(l, "Use My Location", city.isEmpty() ? "Found from your internet connection" : "Looks like " + city,
                Weather.enabled(this) && !Weather.manual(this), () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
                    Weather.setPlace(this, null);
                    next();
                });
        option(l, "Choose a City\u2026", Weather.manual(this) ? Weather.place(this) : "Search by name", Weather.manual(this), () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, true).apply();
            Intent i = new Intent(this, SettingsActivity.class).putExtra("page", SettingsActivity.WEATHER);
            startActivity(i);
        });
        option(l, "Don't Show Weather", null, !Weather.enabled(this), () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, false).apply();
            Weather.changed(this);
            next();
        });
        if (city.isEmpty()) Weather.refresh(this, true, () -> {
            if (index == weatherStep) show(weatherStep, 0);  // show the detected city
        });
        dots();
    }

    /** Presentation is for the whole TV: switch it on and choose its options here, once. */
    void presentation() {
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(com.hydrainnit.refract.present.PresentIcon.banner(u(90) * 2, u(54) * 2));
        icon.setClipToOutline(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(u(9));
        icon.setBackground(shape);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(u(90), u(54));
        ilp.bottomMargin = u(10);
        page.addView(icon, ilp);
        title("Presentation");
        subtitle("Images, videos and webpages sent from any phone or computer on the same Wi-Fi. For everyone on this TV.");
        boolean on = com.hydrainnit.refract.present.Presenter.enabled(this);
        LinearLayout l = list();
        option(l, "Use Presentation", on ? "Adds a Presentation card to Home" : "Off", on, () -> {
            com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_ENABLED, !on);
            show(index, 0);
            focusRow(0);
        });
        if (on) {
            boolean boot = com.hydrainnit.refract.present.Presenter.startOnBoot(this),
                    awake = com.hydrainnit.refract.present.Presenter.keepAwake(this);
            option(l, "Start with the TV", "Play as soon as the TV turns on (signage)", boot, () -> {
                com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_BOOT, !boot);
                show(index, 0);
                focusRow(1);
            });
            option(l, "Keep Screen Awake", "No screensaver or sleep while presenting", awake, () -> {
                com.hydrainnit.refract.present.Presenter.set(this, com.hydrainnit.refract.present.Presenter.KEY_AWAKE, !awake);
                show(index, 0);
                focusRow(2);
            });
        }
        option(l, "Continue", null, false, this::next);
        dots();
    }

    boolean askedLocation;

    void homeAndPermissions() {
        // Ask right away: Android's own prompt for location (the Wi-Fi name in Control Center).
        if (!Perms.hasLocation(this) && !askedLocation) {
            askedLocation = true;
            handler.postDelayed(() -> Perms.requestLocation(this), 600);
        }
        title("Finish Setting Up");
        subtitle("Refract works best as the home screen. Allow these once; anything this TV doesn't support is skipped.");
        LinearLayout l = list();
        boolean home = Perms.isDefaultHome(this);
        option(l, "Make Refract the Home Screen", home ? "The Home button opens Refract" : "Choose Refract when asked", home,
                () -> Perms.requestDefaultHome(this));
        if (!Perms.hasLocation(this)) {
            option(l, "Show the Wi-Fi Name", "In Control Center (needs location access)", false,
                    () -> Perms.requestLocation(this));
        }
        if (Perms.listenerSupported(this) && !Perms.hasListener(this)) {
            option(l, "Continue Watching", "Remember what you play in each app", false, () -> Perms.requestListener(this));
        }
        if (Perms.adminSupported(this) && !Perms.hasAdmin(this)) {
            option(l, "Power Off & Sleep Timer", "Let Refract turn the screen off", false, () -> Perms.requestAdmin(this));
        }
        option(l, "Continue", null, false, this::next);
        dots();
    }

    void finish_() {
        backdrop.animate().alpha(0f).setDuration(300).start();
        View top = new View(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));
        int cs = u(64);
        Bitmap cb = Bitmap.createBitmap(cs, cs, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cc = new android.graphics.Canvas(cb);
        android.graphics.Paint cp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        cp.setColor(0xFF30D158);
        cc.drawCircle(cs / 2f, cs / 2f, cs / 2f, cp);
        cp.setColor(Color.WHITE);
        cp.setStyle(android.graphics.Paint.Style.STROKE);
        cp.setStrokeWidth(cs * 0.09f);
        cp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        cp.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        android.graphics.Path tick = new android.graphics.Path();
        tick.moveTo(cs * 0.29f, cs * 0.52f);
        tick.lineTo(cs * 0.44f, cs * 0.67f);
        tick.lineTo(cs * 0.72f, cs * 0.37f);
        cc.drawPath(tick, cp);
        ImageView check = new ImageView(this);
        check.setImageBitmap(cb);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(u(64), u(64));
        clp.bottomMargin = u(18);
        page.addView(check, clp);
        title("You're All Set");
        subtitle("Change any of this later in Settings, from the gear in Control Center.");
        LinearLayout l = list();
        option(l, "Go to Home Screen", null, false, () -> {
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).apply();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        View bottom = new View(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 1.4f));
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        super.onDestroy();
    }
}
