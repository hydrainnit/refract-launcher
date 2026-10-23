package com.hydrainnit.refract;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/**
 * Light and Dark appearance: one set of colour tokens shared by Home, Setup, Settings, Movies and
 * the screensaver, so every surface follows the choice made in setup. Dark is near-black glass
 * with white text (the original Refract look); Light is frosted white and ivory glass with dark text.
 * Light is designed on its own rather than inverted: its glass gets more opaque over dark
 * artwork (so dark text stays legible) and focus is a lifted white plate with a shadow.
 */
final class Theme {
    static final String PREFS = "appearance", K_MODE = "mode";
    static final String DARK = "dark", LIGHT = "light";

    final boolean light;
    /** Page backgrounds (Setup, Settings): a soft vertical gradient. */
    final int bgTop, bgBottom;
    /** Text on the page / on glass. */
    final int text, textDim, textFaint;
    /** List rows and tiles: resting and focused plates, and the text on a focused plate. */
    final int row, rowFocused, onFocused, onFocusedDim;
    /** Liquid Glass: resting tint of small controls, the frost of panels (Control Center, menus). */
    final int glassTint, panelTop, panelBottom, rimAlpha;
    /** Glyph colour on glass, and on a focused (white) plate. */
    final int glyph, glyphFocused;
    /** A hairline separator, and the tick for a selected choice. */
    final int hairline, tick;
    /** Glass-gradient clock fill (two stops) and its soft shadow. */
    final int clockTop, clockBottom, clockShadow;
    /** Legibility veil baked into photographs behind the app grid (bottom of the screen). */
    final int veilTop, veilMid, veilBottom;
    /** Label colour under app cards, over the veiled photo. */
    final int label, labelShadow;

    private Theme(boolean light) {
        this.light = light;
        if (light) {
            bgTop = 0xFFF4F4F7;
            bgBottom = 0xFFDCDEE5;
            text = 0xFF111114;
            textDim = 0x99111114;
            textFaint = 0x66111114;
            row = 0x8CFFFFFF;
            rowFocused = 0xFFFFFFFF;
            onFocused = 0xFF111114;
            onFocusedDim = 0x99111114;
            glassTint = 0x8CFFFFFF;
            panelTop = 0xE6F6F6F9;
            panelBottom = 0xD9E1E3E9;
            rimAlpha = 0xE6;
            glyph = 0xFF1C1C1E;
            glyphFocused = 0xFF111114;
            hairline = 0x1F000000;
            tick = 0xFF1E9E48;
            clockTop = 0xF2FFFFFF;
            clockBottom = 0xC7DCE8FF;
            clockShadow = 0x33203050;
            veilTop = 0x00FFFFFF;
            veilMid = 0x26FFFFFF;
            veilBottom = 0x99F2F3F7;
            label = 0xFF111114;
            labelShadow = 0x66FFFFFF;
        } else {
            bgTop = 0xFF0B0B10;
            bgBottom = 0xFF16161D;
            text = 0xFFFFFFFF;
            textDim = 0x99FFFFFF;
            textFaint = 0x66FFFFFF;
            row = 0x14FFFFFF;
            rowFocused = 0xF2FFFFFF;
            onFocused = 0xFF111114;
            onFocusedDim = 0x99111114;
            glassTint = 0x1F000000;
            panelTop = 0xD91F1F25;
            panelBottom = 0xCC0D0D11;
            rimAlpha = 0xFF;
            glyph = 0xFFFFFFFF;
            glyphFocused = 0xFF111114;
            hairline = 0x33FFFFFF;
            tick = 0xFF30D158;
            clockTop = 0xEBF2F3F6;
            clockBottom = 0xB39A9CA6;
            clockShadow = 0x59000000;
            veilTop = 0x14000000;
            veilMid = 0x40000000;
            veilBottom = 0xB3000000;
            label = 0xFFFFFFFF;
            labelShadow = 0x99000000;
        }
    }

    private static final Theme DARK_THEME = new Theme(false), LIGHT_THEME = new Theme(true);

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The TV-wide appearance (chosen in setup; Settings -> Appearance changes it). Dark by default. */
    static Theme of(Context c) {
        return LIGHT.equals(prefs(c).getString(K_MODE, DARK)) ? LIGHT_THEME : DARK_THEME;
    }

    static Theme of(boolean light) {
        return light ? LIGHT_THEME : DARK_THEME;
    }

    static boolean isLight(Context c) {
        return of(c).light;
    }

    static void set(Context c, boolean light) {
        prefs(c).edit().putString(K_MODE, light ? LIGHT : DARK).apply();
    }

    /** Page background for Setup and Settings. */
    GradientDrawable pageBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBottom});
    }

    /** A floating glass panel (Control Center, menus) without a backdrop to sample: tinted gradient + rim. */
    GradientDrawable panel(float radius, float density) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{panelTop, panelBottom});
        g.setCornerRadius(radius);
        g.setStroke(Math.max(1, Math.round(density * 0.6f)), light ? 0x99FFFFFF : 0x2EFFFFFF);
        return g;
    }
}
