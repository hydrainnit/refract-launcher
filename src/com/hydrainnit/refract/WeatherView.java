package com.hydrainnit.refract;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * Weather on Home, in Apple Weather's style. At rest it is a glass pill the height of the status
 * pill and level with it: icon, temperature, then the condition over the place and high/low. Its
 * width follows the text (with the place shortened, never touching the edge). Focused, the same
 * glass grows from the pill's corner into a card: place, large temperature, condition and
 * high/low, the next six hours and a 5-day list with temperature range bars.
 *
 * The view is laid out once at the card's size; the morph only changes what is drawn (the glass
 * outline and the two layers' opacity), so it never triggers a layout pass. Nothing animates once
 * it has settled.
 */
final class WeatherView extends View {
    /** Design units: the pill matches the status pill (44); the card is the expanded size. */
    static final float PILL_H = 44, PILL_MAX_W = 300, CARD_W = 340, CARD_H = 372;

    final float u;
    Weather.Data data;
    boolean located;
    /** Text and hairline colour: white on dark glass, near-black on Light-appearance glass. */
    int ink = 0xFFFFFFFF;
    /** 0 = pill, 1 = card. */
    float progress;
    MainActivity.LiquidGlass glass;
    private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Typeface regular, medium, semibold;
    private final RectF r = new RectF();
    private final Rect box = new Rect();
    private final Path arrow = new Path();
    private android.animation.ValueAnimator morph;
    /** The pill's laid-out text and width, computed when the data changes (not per frame). */
    private String pillTemp = "", pillTop = "", pillSub = "";
    private float pillW;

    WeatherView(Context c, float unit) {
        super(c);
        this.u = unit;
        regular = Ui.font(c, "regular");
        medium = Ui.font(c, "medium");
        semibold = Ui.font(c, "semibold");
        setWillNotDraw(false);
    }

    void setData(Weather.Data d) {
        data = d;
        layoutPill();
        invalidate();
    }

    float pillHeight() {
        return PILL_H * u;
    }

    float pillWidth() {
        return pillW;
    }

    /** Pill text and width: the place is shortened with an ellipsis so the text keeps its margins. */
    private void layoutPill() {
        Weather.Data d = data;
        float pad = 14 * u, is = 26 * u, gap = 8 * u, endPad = 18 * u;
        if (d == null) {
            pillTemp = "";
            pillTop = "Weather";
            pillSub = "";
            size(12, medium);
            pillW = pad + p.measureText("Getting the weather…") + endPad;
            return;
        }
        pillTemp = d.temp + "°";
        size(20, regular);
        float tw = p.measureText(pillTemp);
        pillTop = Weather.describe(d.code, d.day);
        String hl = "H:" + d.max + "° L:" + d.min + "°";
        size(11.5f, semibold);
        float topW = p.measureText(pillTop);
        size(11.5f, medium);
        float hlW = p.measureText(hl);
        float fixed = pad + is + gap + tw + 10 * u + endPad;
        float textMax = PILL_MAX_W * u - fixed;
        String place = d.place;
        if (!place.isEmpty()) {
            float room = textMax - hlW - p.measureText("  ");
            place = room > 24 * u ? TextUtils.ellipsize(place, p, room, TextUtils.TruncateAt.END).toString() : "";
        }
        pillSub = place.isEmpty() ? hl : place + "  " + hl;
        float subW = p.measureText(pillSub);
        if (topW > textMax) {
            size(11.5f, semibold);
            pillTop = TextUtils.ellipsize(pillTop, p, textMax, TextUtils.TruncateAt.END).toString();
            topW = textMax;
        }
        pillW = fixed + Math.min(textMax, Math.max(topW, subW));
    }

    /** Grows into the card (focused) or back into the pill; draw-only, about a quarter second. */
    void expand(boolean open) {
        float to = open ? 1f : 0f;
        if (morph != null) morph.cancel();
        if (progress == to) return;
        morph = android.animation.ValueAnimator.ofFloat(progress, to);
        morph.setDuration(open ? 280 : 200);
        morph.setInterpolator(MainActivity.EASE);
        morph.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        morph.start();
    }

    boolean expanded() {
        return progress > 0.5f;
    }

    /** Focus moves by the pill's outline, not the card-sized view behind it. */
    @Override
    public void getFocusedRect(Rect out) {
        out.set(0, 0, Math.round(pillW), Math.round(pillHeight()));
    }

    @Override
    protected void onDraw(Canvas c) {
        float e = progress;
        float w = lerp(pillW, CARD_W * u, e), h = lerp(pillHeight(), CARD_H * u, e);
        float rad = lerp(pillHeight() / 2f, 26 * u, e);
        if (glass != null) {
            glass.r = rad;
            box.set(0, 0, Math.round(w), Math.round(h));
            if (!box.equals(glass.getBounds())) glass.setBounds(box);
            glass.draw(c);
        }
        float pillA = Math.max(0f, 1f - e * 2.5f), cardA = Math.max(0f, (e - 0.35f) / 0.65f);
        if (pillA > 0) drawPill(c, pillA);
        if (cardA > 0 && data != null) {
            c.save();
            c.clipRect(0, 0, w, h);
            drawCard(c, cardA);
            c.restore();
        }
    }

    private void drawPill(Canvas c, float a) {
        float h = pillHeight(), pad = 14 * u, is = 26 * u;
        if (data == null) {
            text(c, "Getting the weather…", pad, h / 2f + 4 * u, 12, medium, 0.7f * a, Paint.Align.LEFT);
            return;
        }
        icon(c, data.code, data.day, pad, (h - is) / 2f, is, a);
        float tx = pad + is + 8 * u;
        float tw = text(c, pillTemp, tx, h / 2f + 7 * u, 20, regular, a, Paint.Align.LEFT);
        float cx = tx + tw + 10 * u;
        text(c, pillTop, cx, h / 2f - 2 * u, 11.5f, semibold, a, Paint.Align.LEFT);
        text(c, pillSub, cx, h / 2f + 12 * u, 11.5f, medium, 0.72f * a, Paint.Align.LEFT);
    }

    /** The card, laid out like the Apple Weather widget in the reference. */
    private void drawCard(Canvas c, float a) {
        Weather.Data d = data;
        float w = CARD_W * u, pad = 18 * u, x0 = pad, x1 = w - pad;
        // Header: place (with the location arrow when it was found automatically), large temperature;
        // condition icon, condition and high/low on the right.
        size(16, semibold);
        String place = TextUtils.ellipsize(d.place.isEmpty() ? "My Location" : d.place, p, w - 2 * pad - 130 * u,
                TextUtils.TruncateAt.END).toString();
        float pw = text(c, place, x0, pad + 15 * u, 16, semibold, a, Paint.Align.LEFT);
        if (located) drawArrow(c, x0 + pw + 6 * u, pad + 4 * u, 11 * u, a);
        text(c, d.temp + "°", x0 - 2 * u, pad + 78 * u, 56, regular, a, Paint.Align.LEFT);
        icon(c, d.code, d.day, x1 - 26 * u, pad - 2 * u, 26 * u, a);
        text(c, Weather.describe(d.code, d.day), x1, pad + 52 * u, 14.5f, semibold, a, Paint.Align.RIGHT);
        text(c, "H:" + d.max + "° L:" + d.min + "°", x1, pad + 70 * u, 14, semibold, a, Paint.Align.RIGHT);

        // Next hours.
        float y = pad + 92 * u;
        hairline(c, x0, x1, y, a);
        int hn = Math.min(6, d.hours.size());
        float hw = (x1 - x0) / Math.max(1, hn);
        for (int i = 0; i < hn; i++) {
            Weather.Hour hr = d.hours.get(i);
            float hx = x0 + hw * (i + 0.5f);
            text(c, hr.label, hx, y + 22 * u, 12, semibold, 0.72f * a, Paint.Align.CENTER);
            icon(c, hr.code, hr.day, hx - 12 * u, y + 31 * u, 24 * u, a);
            text(c, hr.temp + "°", hx, y + 76 * u, 15, semibold, a, Paint.Align.CENTER);
        }

        // 5-day list with range bars coloured from cool to hot.
        y += 90 * u;
        hairline(c, x0, x1, y, a);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Weather.Day day : d.days) {
            lo = Math.min(lo, day.min);
            hi = Math.max(hi, day.max);
        }
        lo = Math.min(lo, d.temp);
        hi = Math.max(hi, d.temp);
        float span = Math.max(1, hi - lo);
        float row = 31 * u, by0 = x0 + 166 * u, by1 = x1 - 46 * u;
        boolean f = d.windUnit.contains("mp");
        int bars = Math.round(255 * a);
        for (int i = 0; i < Math.min(5, d.days.size()); i++) {
            Weather.Day day = d.days.get(i);
            float ry = y + 8 * u + row * i, base = ry + row / 2f + 5 * u;
            text(c, day.label, x0, base, 15, semibold, a, Paint.Align.LEFT);
            icon(c, day.code, true, x0 + 72 * u, ry + 3 * u, 25 * u, a);
            text(c, day.min + "°", by0 - 12 * u, base, 15, semibold, 0.45f * a, Paint.Align.RIGHT);
            text(c, day.max + "°", x1, base, 15, semibold, a, Paint.Align.RIGHT);
            float cy = ry + row / 2f, th = 5 * u;
            bar.setShader(null);
            bar.setColor((ink & 0xFFFFFF) | 0x22000000);
            bar.setAlpha(Math.round(0x22 * a));
            r.set(by0, cy - th / 2, by1, cy + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, bar);
            float s0 = by0 + (by1 - by0) * (day.min - lo) / span, s1 = by0 + (by1 - by0) * (day.max - lo) / span;
            bar.setColor(0xFFFFFFFF);
            bar.setAlpha(bars);
            bar.setShader(new LinearGradient(s0, 0, Math.max(s1, s0 + 1), 0, WeatherView.tempColor(day.min, f),
                    WeatherView.tempColor(day.max, f), Shader.TileMode.CLAMP));
            r.set(s0, cy - th / 2, Math.max(s1, s0 + th), cy + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, bar);
        }
        bar.setShader(null);
    }

    /** The small location arrow after the place, as in Apple Weather. */
    private void drawArrow(Canvas c, float x, float y, float s, float a) {
        arrow.reset();
        arrow.moveTo(x + s, y);
        arrow.lineTo(x, y + s * 0.45f);
        arrow.lineTo(x + s * 0.5f, y + s * 0.52f);
        arrow.lineTo(x + s * 0.58f, y + s);
        arrow.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink);
        p.setAlpha(Math.round(255 * a));
        c.drawPath(arrow, p);
    }

    /** Apple Weather's palette: blue (cold) -> teal -> green -> yellow -> orange -> red (hot). */
    static int tempColor(int t, boolean fahrenheit) {
        float cdeg = fahrenheit ? (t - 32) * 5f / 9f : t;
        float[] at = {-5, 8, 16, 22, 28, 35};
        int[] col = {0xFF5E9BFF, 0xFF56D6E4, 0xFF8FDB6A, 0xFFF7D74A, 0xFFFF9F2E, 0xFFFF5A3C};
        if (cdeg <= at[0]) return col[0];
        for (int i = 1; i < at.length; i++) {
            if (cdeg <= at[i]) {
                float k = (cdeg - at[i - 1]) / (at[i] - at[i - 1]);
                return mix(col[i - 1], col[i], k);
            }
        }
        return col[col.length - 1];
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private final Paint iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private void icon(Canvas c, int code, boolean day, float x, float y, float size, float a) {
        Bitmap b = WeatherIcons.get(code, day, Math.round(size));
        iconPaint.setAlpha(Math.round(255 * a));
        c.drawBitmap(b, x, y, iconPaint);
    }

    private void hairline(Canvas c, float x0, float x1, float y, float a) {
        p.setShader(null);
        p.setColor(ink);
        p.setAlpha(Math.round(0x40 * a));
        p.setStrokeWidth(Math.max(1, 0.8f * u));
        c.drawLine(x0, y, x1, y, p);
    }

    private void size(float sp, android.graphics.Typeface tf) {
        p.setTypeface(tf);
        p.setTextSize(sp * u);
    }

    private float text(Canvas c, String s, float x, float y, float sz, android.graphics.Typeface tf, float alpha, Paint.Align al) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        size(sz, tf);
        p.setTextAlign(al);
        p.setColor(ink);
        p.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        c.drawText(s, x, y, p);
        p.setTextAlign(Paint.Align.LEFT);
        return p.measureText(s);
    }
}
