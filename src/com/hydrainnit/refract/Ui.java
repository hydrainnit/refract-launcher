package com.hydrainnit.refract;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Resolution-independent sizing. The launcher is designed on a 960x540 grid (tvOS points / 2);
 * every size scales with the connected display's actual resolution, not its declared density,
 * so 720p, 1080p and 4K outputs (and any density setting) get the same proportions.
 */
public final class Ui {
    private Ui() { }

    /** Display size in pixels for the window this context is shown on. */
    static Point screen(Context c) {
        Point p = new Point();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display d = Build.VERSION.SDK_INT >= 30 && c instanceof Activity ? ((Activity) c).getDisplay() : wm.getDefaultDisplay();
        if (d == null) d = wm.getDefaultDisplay();
        d.getRealSize(p);
        if (p.x < p.y) p.set(p.y, p.x);  // TVs are landscape; guard against a transient portrait report
        return p;
    }

    /** Pixels per design unit (1 unit = 1/960 of the screen width, capped by height for odd aspect ratios). */
    static float unit(Context c) {
        Point p = screen(c);
        return Math.min(p.x / 960f, p.y / 540f);
    }

    public static int px(Context c, float units) {
        return Math.round(units * unit(c));
    }

    private static final java.util.Map<String, android.graphics.Typeface> FONTS = new java.util.HashMap<>();

    /**
     * Inter (OFL, bundled): the closest open font to Apple's SF. "display" is Inter Display Bold for
     * large titles; "regular", "medium", "semibold" for UI and body text.
     */
    public static android.graphics.Typeface font(Context c, String weight) {
        android.graphics.Typeface t = FONTS.get(weight);
        if (t == null) {
            String file = weight.equals("display") ? "InterDisplay-Bold" : weight.equals("medium") ? "Inter-Medium"
                    : weight.equals("semibold") ? "Inter-SemiBold" : "Inter-Regular";
            try {
                t = android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts/" + file + ".ttf");
            } catch (RuntimeException e) {
                t = android.graphics.Typeface.DEFAULT;
            }
            FONTS.put(weight, t);
        }
        return t;
    }

    /**
     * Small, heavily blurred copy of an image (box blur, several passes). Used as the backdrop
     * that Liquid Glass surfaces sample; at this size it costs a few milliseconds.
     */
    static android.graphics.Bitmap blur(android.graphics.Bitmap src, int w, int h, int radius, int passes) {
        if (src == null) return null;
        android.graphics.Bitmap b = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
                .copy(android.graphics.Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            boxBlur(px, tmp, w, h, radius, true);
            boxBlur(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** Liquid Glass backdrop: blurred image with boosted saturation and a slight lift, baked once. */
    static android.graphics.Bitmap glassBackdrop(android.graphics.Bitmap src, int w, int h) {
        android.graphics.Bitmap blurred = blur(src, w, h, 3, 3);
        if (blurred == null) return null;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(1.6f);
        cm.postConcat(new android.graphics.ColorMatrix(new float[]{
                1.05f, 0, 0, 0, 14, 0, 1.05f, 0, 0, 14, 0, 0, 1.05f, 0, 14, 0, 0, 0, 1, 0}));
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        new android.graphics.Canvas(out).drawBitmap(blurred, 0, 0, p);
        blurred.recycle();
        return out;
    }

    private static void boxBlur(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int l = 0; l < lines; l++) {
            int ra = 0, ga = 0, ba = 0, n = 0;
            for (int i = -r; i <= r; i++) {
                int k = Math.max(0, Math.min(len - 1, i));
                int c = in[horizontal ? l * w + k : k * w + l];
                ra += (c >> 16) & 0xFF; ga += (c >> 8) & 0xFF; ba += c & 0xFF; n++;
            }
            for (int i = 0; i < len; i++) {
                out[horizontal ? l * w + i : i * w + l] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
                int outI = Math.max(0, Math.min(len - 1, i - r)), inI = Math.max(0, Math.min(len - 1, i + r + 1));
                int co = in[horizontal ? l * w + outI : outI * w + l], ci = in[horizontal ? l * w + inI : inI * w + l];
                ra += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                ga += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                ba += (ci & 0xFF) - (co & 0xFF);
            }
        }
    }

    /** Text sized in design units so type scales with the display like everything else. */
    public static void textSize(TextView t, float units) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * unit(t.getContext()));
    }
}
