package com.hydrainnit.refract;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Now Playing, left of the status pill: a glass capsule (after the iPhone's Dynamic Island) with
 * the album art and a small waveform in the art's colour. It appears only while an app plays
 * audio in the background and Home is on screen.
 *
 * The waveform is an animated level indicator, not an analysis of the audio (that would need
 * the microphone permission for the output mix). It redraws about 15 times a second only while
 * music plays and the pill is visible, and stops completely on pause, so a quiet Home still draws
 * no frames. With Reduce Motion on it shows still bars.
 */
final class NowPlayingPill extends LinearLayout {
    final ImageView art;
    final Wave wave;
    final MainActivity.LiquidGlass glass;
    final float u;
    final boolean light;

    /**
     * {@code glass}: the same Liquid Glass as the status pill (the pre-blurred backdrop drawn as a
     * shader with gradient tint and rim; no live blur or translucent layers), dark or light frost
     * to match the appearance.
     */
    NowPlayingPill(Context c, float unit, int height, boolean light, MainActivity.LiquidGlass glass) {
        super(c);
        u = unit;
        this.light = light;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        // The art sits inset from the capsule's edge by the same margin on every side.
        int as = height - Math.round(16 * unit);
        int pad = (height - as) / 2;
        setPadding(pad, pad, Math.round(16 * unit), pad);
        this.glass = glass;
        setBackground(glass);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 6 * u);
            }
        });
        art.setClipToOutline(true);
        art.setBackgroundColor(light ? 0xFFE5E5EA : 0xFF2C2C2E);
        addView(art, new LayoutParams(as, as));
        wave = new Wave(c, unit);
        LayoutParams wl = new LayoutParams(Math.round(30 * unit), Math.round(20 * unit));
        wl.leftMargin = Math.round(12 * unit);
        addView(wave, wl);

        setOnFocusChangeListener((v, has) -> {
            glass.setLit(has);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
    }

    /** New track: its artwork (already scaled off the UI thread) and the colour the waveform takes. */
    void setTrack(Bitmap cover, int tint) {
        art.setImageBitmap(cover);
        wave.color = tint;
        wave.invalidate();
    }

    void setPlaying(boolean playing, boolean still) {
        wave.still = still;
        wave.setPlaying(playing);
    }

    /** The art's average colour, adjusted so the bars read on the pill (computed off the UI thread). */
    static int tintOf(Bitmap b, boolean light) {
        if (b == null) return light ? 0xFF248A3D : 0xFF30D158;
        Bitmap s = Bitmap.createScaledBitmap(b, 6, 6, true);
        long r = 0, g = 0, bl = 0;
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                int p = s.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
            }
        }
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / 36), (int) (g / 36), (int) (bl / 36), hsv);
        if (light) {
            // On white glass: a deeper shade of the art's colour.
            hsv[1] = Math.max(hsv[1], 0.55f);
            hsv[2] = Math.min(Math.max(hsv[2], 0.45f), 0.7f);
        } else {
            hsv[1] = Math.max(hsv[1], 0.35f);
            hsv[2] = Math.max(hsv[2], 0.85f);
        }
        return Color.HSVToColor(hsv);
    }

    /** Five rounded bars easing toward new heights; draws nothing extra while paused or hidden. */
    static final class Wave extends View {
        static final int BARS = 5;
        static final long FRAME_MS = 66;
        final float u;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final float[] level = {0.35f, 0.6f, 0.9f, 0.55f, 0.3f}, target = new float[BARS];
        final java.util.Random rnd = new java.util.Random();
        int color = 0xFF30D158, tick;
        boolean playing, still, running;

        final Runnable frame = new Runnable() {
            @Override public void run() {
                running = false;
                if (!playing || still || !isShown()) return;
                if (faded()) {
                    // Hidden by a fade (an app opening, the shelf scrolled up): check back slowly, draw nothing.
                    running = true;
                    postDelayed(this, 400);
                    return;
                }
                if (tick++ % 3 == 0) {
                    for (int i = 0; i < BARS; i++) {
                        float centre = 1f - Math.abs(i - 2) * 0.18f;  // taller in the middle
                        target[i] = (0.25f + rnd.nextFloat() * 0.75f) * centre;
                    }
                }
                for (int i = 0; i < BARS; i++) level[i] += (target[i] - level[i]) * 0.45f;
                invalidate();
                schedule();
            }
        };

        Wave(Context c, float unit) {
            super(c);
            u = unit;
        }

        void setPlaying(boolean on) {
            playing = on;
            if (!on || still) {
                // Paused: bars settle low and the loop ends.
                for (int i = 0; i < BARS; i++) level[i] = 0.22f + (i == 2 ? 0.1f : 0f);
                removeCallbacks(frame);
                running = false;
                invalidate();
            } else {
                schedule();
            }
        }

        boolean faded() {
            float a = 1f;
            for (View v = this; v != null && a > 0.01f; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                a *= v.getAlpha();
            }
            return a <= 0.01f;
        }

        /** Navigating: no waveform frames for a moment, so every frame goes to moving focus. */
        void hold(long ms) {
            if (!playing || still) return;
            removeCallbacks(frame);
            running = true;
            postDelayed(frame, ms);
        }

        void schedule() {
            if (running) return;
            running = true;
            postDelayed(frame, FRAME_MS);
        }

        @Override
        public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            if (visible && playing) schedule();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(frame);
            running = false;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float gap = w / (BARS * 2f - 1), bw = gap;
            p.setColor(color);
            for (int i = 0; i < BARS; i++) {
                float bh = Math.max(bw, h * level[i]);
                float x = i * 2 * gap;
                r.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f);
                c.drawRoundRect(r, bw / 2f, bw / 2f, p);
            }
        }
    }
}
