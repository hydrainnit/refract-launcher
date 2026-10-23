package com.hydrainnit.refract;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * One clock for every place Refract shows the time: the screensaver, setup previews and the status
 * pill. Styles: "cinematic" (oversized, light weight, the time melting into the photo), "centered"
 * (large ambient digits), "minimal" (small, in a corner), "stacked" (hours above minutes),
 * "pill" (status-row size, plain colour) and "analog" (a minimal dial).
 *
 * The digits are filled with a glass gradient (translucent white with a cool highlight in Light,
 * silver to charcoal in Dark) and a soft shadow so they stay legible over any photo. It redraws
 * once a minute, or once a second only when seconds are shown, and re-reads the time zone and
 * 12/24-hour setting when the system announces a change.
 */
final class ClockView extends View {
    static final String CINEMATIC = "cinematic", CENTERED = "centered", MINIMAL = "minimal", STACKED = "stacked",
            PILL = "pill", ANALOG = "analog";
    static final String[] STYLES = {CINEMATIC, CENTERED, MINIMAL, STACKED, ANALOG};
    static final String[] STYLE_NAMES = {"Cinematic", "Centred", "Minimal", "Stacked", "Analog"};
    static final String[] FONTS = {"thin", "light", "inter", "display", "serif"};
    static final String[] FONT_NAMES = {"Thin", "Light", "Inter", "Inter Display", "Serif"};

    String style = CINEMATIC;
    /** Size in design units for the main digits (scaled by the style's own base size). */
    float scale = 1f;
    float letterSpacing = -0.02f;
    float opacity = 0.92f;
    boolean seconds, showDate, gradient = true;
    /** null = follow the TV's 12/24-hour setting. */
    Boolean force24;
    int plainColor = 0xFFFFFFFF;
    Theme theme;

    private final float u;
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint dial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int shaderH = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            requestLayout();
            invalidate();
            long now = System.currentTimeMillis();
            long step = seconds ? 1000 : 60000;
            handler.postDelayed(this, step - now % step + 20);
        }
    };

    private final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            requestLayout();
            invalidate();
        }
    };

    ClockView(Context c, Theme theme) {
        super(c);
        this.theme = theme;
        this.u = Ui.unit(c);
        setFont("thin");
        date.setTypeface(Ui.font(c, "medium"));
    }

    void setFont(String f) {
        Typeface t;
        switch (f) {
            case "light": t = Typeface.create("sans-serif-light", Typeface.NORMAL); break;
            case "inter": t = Ui.font(getContext(), "regular"); break;
            case "display": t = Ui.font(getContext(), "display"); break;
            case "serif": t = Typeface.create(Typeface.SERIF, Typeface.NORMAL); break;
            default: t = Typeface.create("sans-serif-thin", Typeface.NORMAL); break;
        }
        digits.setTypeface(t);
        shaderH = -1;
        requestLayout();
        invalidate();
    }

    /** Applies the screensaver's saved clock settings. */
    void configure(Ambient.Settings s) {
        style = s.clockStyle;
        scale = s.clockSize / 100f;
        letterSpacing = s.clockSpacing / 100f;
        opacity = s.clockOpacity / 100f;
        seconds = s.clockSeconds;
        showDate = s.clockDate;
        force24 = s.clock24.equals("24") ? Boolean.TRUE : s.clock24.equals("12") ? Boolean.FALSE : null;
        setFont(s.clockFont);
    }

    float baseSize() {
        switch (style) {
            case CINEMATIC: return 210;
            case CENTERED: return 140;
            case STACKED: return 120;
            case MINIMAL: return 52;
            case PILL: return 16;
            default: return 0;
        }
    }

    boolean is24() {
        return force24 != null ? force24 : android.text.format.DateFormat.is24HourFormat(getContext());
    }

    String timeText() {
        String pattern = is24() ? (seconds ? "H:mm:ss" : "H:mm") : (seconds ? "h:mm:ss" : "h:mm");
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(Calendar.getInstance().getTime());
    }

    String dateText() {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime());
    }

    private void setupPaints() {
        digits.setTextSize(baseSize() * scale * u);
        digits.setLetterSpacing(letterSpacing);
        int shadow = theme.clockShadow;
        digits.setShadowLayer(style.equals(PILL) ? 6 * u : 18 * u * scale, 0, 2 * u, shadow);
        date.setTextSize(Math.max(12, baseSize() * scale * 0.11f) * u);
        date.setColor(gradient ? theme.clockTop : plainColor);
        date.setShadowLayer(6 * u, 0, u, shadow);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setupPaints();
        int w, h;
        if (style.equals(ANALOG)) {
            w = h = Math.round(220 * scale * u);
        } else if (style.equals(STACKED)) {
            String[] parts = timeText().split(":");
            Paint.FontMetrics fm = digits.getFontMetrics();
            float line = (fm.descent - fm.ascent) * 0.82f;
            float tw = 0;
            for (String p : parts) tw = Math.max(tw, digits.measureText(p));
            w = Math.round(tw + 8 * u);
            h = Math.round(line * parts.length + (showDate ? date.getTextSize() * 1.8f : 0));
        } else {
            Paint.FontMetrics fm = digits.getFontMetrics();
            w = Math.round(Math.max(digits.measureText(timeText()), showDate ? date.measureText(dateText()) : 0) + 8 * u);
            h = Math.round(fm.descent - fm.ascent + (showDate && !style.equals(PILL) ? date.getTextSize() * 1.8f : 0));
        }
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        setupPaints();
        int a = Math.round(255 * opacity);
        if (style.equals(ANALOG)) {
            drawAnalog(c, a);
            return;
        }
        Paint.FontMetrics fm = digits.getFontMetrics();
        float cx = getWidth() / 2f;
        digits.setTextAlign(Paint.Align.CENTER);
        date.setTextAlign(Paint.Align.CENTER);
        if (gradient) {
            if (shaderH != getHeight()) {
                digits.setShader(new LinearGradient(0, 0, 0, getHeight(), theme.clockTop, theme.clockBottom, Shader.TileMode.CLAMP));
                shaderH = getHeight();
            }
            digits.setColor(0xFFFFFFFF);
        } else {
            digits.setShader(null);
            digits.setColor(plainColor);
        }
        digits.setAlpha(a);
        float y = -fm.ascent;
        if (style.equals(STACKED)) {
            float line = (fm.descent - fm.ascent) * 0.82f;
            for (String p : timeText().split(":")) {
                c.drawText(p, cx, y, digits);
                y += line;
            }
            y -= line;
        } else {
            c.drawText(timeText(), cx, y, digits);
        }
        if (showDate && !style.equals(PILL)) {
            date.setAlpha(Math.round(a * 0.9f));
            c.drawText(dateText(), cx, y + fm.descent + date.getTextSize() * 1.25f, date);
        }
    }

    /** A minimal dial: a soft glass ring, twelve ticks and two hands in the clock gradient colours. */
    private void drawAnalog(Canvas c, int a) {
        float s = Math.min(getWidth(), getHeight()), r = s / 2f - 4 * u, cx = getWidth() / 2f, cy = getHeight() / 2f;
        dial.setShader(null);
        dial.setStyle(Paint.Style.STROKE);
        dial.setStrokeCap(Paint.Cap.ROUND);
        dial.setColor(theme.clockTop);
        dial.setAlpha(Math.round(a * 0.45f));
        dial.setStrokeWidth(2 * u);
        c.drawCircle(cx, cy, r, dial);
        dial.setAlpha(a);
        for (int i = 0; i < 12; i++) {
            double t = Math.PI * 2 * i / 12;
            float in = i % 3 == 0 ? r * 0.82f : r * 0.88f;
            c.drawLine(cx + (float) Math.sin(t) * in, cy - (float) Math.cos(t) * in,
                    cx + (float) Math.sin(t) * r * 0.94f, cy - (float) Math.cos(t) * r * 0.94f, dial);
        }
        Calendar k = Calendar.getInstance();
        double min = k.get(Calendar.MINUTE) + k.get(Calendar.SECOND) / 60.0, hr = k.get(Calendar.HOUR) + min / 60.0;
        dial.setStrokeWidth(6 * u);
        double ht = Math.PI * 2 * hr / 12, mt = Math.PI * 2 * min / 60;
        c.drawLine(cx, cy, cx + (float) Math.sin(ht) * r * 0.5f, cy - (float) Math.cos(ht) * r * 0.5f, dial);
        dial.setStrokeWidth(4 * u);
        dial.setColor(theme.clockBottom);
        dial.setAlpha(a);
        c.drawLine(cx, cy, cx + (float) Math.sin(mt) * r * 0.78f, cy - (float) Math.cos(mt) * r * 0.78f, dial);
        if (seconds) {
            double st = Math.PI * 2 * k.get(Calendar.SECOND) / 60;
            dial.setStrokeWidth(1.5f * u);
            dial.setColor(0xFFFF9F0A);
            c.drawLine(cx, cy, cx + (float) Math.sin(st) * r * 0.84f, cy - (float) Math.cos(st) * r * 0.84f, dial);
        }
        dial.setStyle(Paint.Style.FILL);
        dial.setColor(theme.clockTop);
        c.drawCircle(cx, cy, 5 * u, dial);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(timeChanged, f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
        try {
            getContext().unregisterReceiver(timeChanged);
        } catch (Exception ignored) {
            // not registered
        }
    }
}
