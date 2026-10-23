package com.hydrainnit.refract;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Photographic wallpapers. A starter set of Pexels photos ships in the APK (assets/wallpapers,
 * 1600x900 WebP, ~2.6 MB, with their metadata in index.json) so wallpapers work offline and with
 * no API key. A remote Pexels source uses the same Photo model and is off by default: Pexels API
 * access is paused, so nothing here makes a request unless PEXELS_API_PAUSED is false, the user
 * saved a key and switched remote wallpapers on.
 *
 * Only metadata stays in memory. Pictures stay on disk and are decoded on demand at the size
 * they are shown (inSampleSize), by whoever displays them; this class keeps no bitmaps.
 */
final class Wallpapers {
    private Wallpapers() { }

    /** Pexels has paused API access: no validation, search or download requests while true. */
    static final boolean PEXELS_API_PAUSED = true;

    static final String K_WALL = "wallpaper", K_CATEGORY = "wall_category", PHOTO = "photo:";
    static final String K_REMOTE = "pexels_remote";

    static final class Category {
        final String id, name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Photo {
        String id, file, category, theme, title, photographer, photographerUrl, sourceUrl;
        /** Remote (Pexels API) photos: the image URL; the file is then a disk-cache name. */
        String url = "";

        boolean remote() {
            return !url.isEmpty();
        }

        /** "Photo: Name on Pexels", for the screensaver and the wallpaper browser. */
        String credit() {
            return "Photo: " + photographer + " on Pexels";
        }
    }

    private static List<Photo> photos;
    private static List<Category> categories;

    private static synchronized void load(Context c) {
        if (photos != null) return;
        photos = new ArrayList<>();
        categories = new ArrayList<>();
        try (InputStream in = c.getAssets().open("wallpapers/index.json")) {
            JSONObject o = new JSONObject(new String(Suggestions.readAll(in), StandardCharsets.UTF_8));
            JSONArray cs = o.getJSONArray("categories");
            for (int i = 0; i < cs.length(); i++) {
                categories.add(new Category(cs.getJSONObject(i).getString("id"), cs.getJSONObject(i).getString("name")));
            }
            JSONArray ps = o.getJSONArray("photos");
            for (int i = 0; i < ps.length(); i++) photos.add(fromJson(ps.getJSONObject(i)));
        } catch (Exception e) {
            android.util.Log.w("TvHome", "wallpapers: " + e);
        }
        photos.addAll(remoteCached(c));
    }

    static Photo fromJson(JSONObject p) {
        Photo ph = new Photo();
        ph.id = p.optString("id");
        ph.file = p.optString("file");
        ph.category = p.optString("category");
        ph.theme = p.optString("theme", "dark");
        ph.title = p.optString("title");
        ph.photographer = p.optString("photographer");
        ph.photographerUrl = p.optString("photographer_url");
        ph.sourceUrl = p.optString("source_url");
        ph.url = p.optString("url");
        return ph;
    }

    static List<Category> categories(Context c) {
        load(c);
        return categories;
    }

    static String categoryName(Context c, String id) {
        for (Category k : categories(c)) if (k.id.equals(id)) return k.name;
        return id;
    }

    static List<Photo> all(Context c) {
        load(c);
        return photos;
    }

    static List<Photo> inCategory(Context c, String cat) {
        List<Photo> out = new ArrayList<>();
        for (Photo p : all(c)) if (p.category.equals(cat)) out.add(p);
        return out;
    }

    static Photo byId(Context c, String id) {
        for (Photo p : all(c)) if (p.id.equals(id)) return p;
        return null;
    }

    // ------------------------------------------------------------------ selection (per profile)

    static SharedPreferences home(Context c) {
        return Profiles.homePrefs(c);
    }

    static boolean isPhoto(String style) {
        return style != null && style.startsWith(PHOTO);
    }

    /**
     * The profile's wallpaper: a bundled photo by default (one that suits the appearance), the
     * original Refract gradients still work for profiles that chose one before.
     */
    static String style(Context c) {
        String s = home(c).getString(K_WALL, null);
        if (s != null) return s;
        return PHOTO + defaultPhoto(c).id;
    }

    static Photo selected(Context c) {
        String s = style(c);
        Photo p = isPhoto(s) ? byId(c, s.substring(PHOTO.length())) : null;
        return p != null ? p : null;
    }

    static Photo defaultPhoto(Context c) {
        boolean light = Theme.isLight(c);
        for (Photo p : all(c)) if (p.category.equals("deserts") && p.theme.equals(light ? "light" : "dark")) return p;
        return all(c).get(0);
    }

    static void select(Context c, Photo p) {
        home(c).edit().putString(K_WALL, PHOTO + p.id).putString(K_CATEGORY, p.category).apply();
    }

    /** The category the screensaver cycles through: the selected photo's, unless chosen separately. */
    static String category(Context c) {
        String k = home(c).getString(K_CATEGORY, null);
        if (k != null) return k;
        Photo p = selected(c);
        return p != null ? p.category : "deserts";
    }

    // ------------------------------------------------------------------ decoding

    /**
     * Decodes one photo no wider than maxWidth (a power-of-two sample of the 1600 px file), off the
     * UI thread. The caller owns the bitmap and drops it when it is no longer shown.
     */
    static Bitmap decode(Context c, Photo p, int maxWidth) {
        if (p == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = open(c, p)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inMutable = true;  // so legibility veils can be painted straight into it
            try (InputStream in = open(c, p)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream open(Context c, Photo p) throws Exception {
        if (p.remote()) return new FileInputStream(new File(remoteDir(c), p.file));
        return c.getAssets().open("wallpapers/" + p.file);
    }

    // ------------------------------------------------------------------ optional Pexels source

    /** Remote wallpapers can only turn on when the API is available and a key is saved. */
    static boolean remoteEnabled(Context c) {
        return !PEXELS_API_PAUSED && Secrets.has(c, Secrets.PEXELS)
                && c.getSharedPreferences("wallpapers", Context.MODE_PRIVATE).getBoolean(K_REMOTE, false);
    }

    static File remoteDir(Context c) {
        File d = new File(c.getFilesDir(), "pexels");
        d.mkdirs();
        return d;
    }

    /** Remote photos downloaded earlier (metadata on disk), so they keep working offline. */
    static List<Photo> remoteCached(Context c) {
        List<Photo> out = new ArrayList<>();
        File f = new File(remoteDir(c), "index.json");
        if (!f.exists()) return out;
        try (InputStream in = new FileInputStream(f)) {
            JSONArray a = new JSONArray(new String(Suggestions.readAll(in), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                Photo p = fromJson(a.getJSONObject(i));
                if (new File(remoteDir(c), p.file).exists()) out.add(p);
            }
        } catch (Exception e) {
            // unreadable: start over next time
        }
        return out;
    }

    /**
     * Downloads up to `count` landscape photos for one category from the Pexels API and adds them
     * to the library. Only runs when remoteEnabled(); one request plus one image download per
     * photo, no retries or polling. Returns the Secrets status for the key.
     */
    static String fetchRemote(Context c, Category cat, int count) {
        if (!remoteEnabled(c)) return Secrets.ST_UNAVAILABLE;
        try {
            String url = "https://api.pexels.com/v1/search?orientation=landscape&size=large&per_page=" + count
                    + "&query=" + URLEncoder.encode(cat.name, "UTF-8");
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setRequestProperty("Authorization", Secrets.get(c, Secrets.PEXELS));
            JSONArray found;
            try {
                int code = con.getResponseCode();
                if (code == 401 || code == 403) return mark(c, Secrets.ST_INVALID);
                if (code == 429) return mark(c, Secrets.ST_RATE);
                if (code != 200) return mark(c, Secrets.ST_UNAVAILABLE);
                found = new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8)).optJSONArray("photos");
            } finally {
                con.disconnect();
            }
            List<Photo> added = remoteCached(c);
            for (int i = 0; found != null && i < found.length(); i++) {
                JSONObject j = found.getJSONObject(i);
                Photo p = new Photo();
                p.id = "pexels-" + j.getLong("id");
                if (byId(c, p.id) != null) continue;
                p.file = j.getLong("id") + ".jpg";
                p.category = cat.id;
                p.theme = "dark";
                p.title = j.optString("alt", cat.name);
                p.photographer = j.optString("photographer");
                p.photographerUrl = j.optString("photographer_url");
                p.sourceUrl = j.optString("url");
                p.url = j.getJSONObject("src").optString("large2x");
                HttpURLConnection img = (HttpURLConnection) new URL(p.url).openConnection();
                try (InputStream in = img.getInputStream(); OutputStream os = new FileOutputStream(new File(remoteDir(c), p.file))) {
                    os.write(Suggestions.readAll(in));
                } finally {
                    img.disconnect();
                }
                added.add(p);
            }
            JSONArray out = new JSONArray();
            for (Photo p : added) {
                out.put(new JSONObject().put("id", p.id).put("file", p.file).put("category", p.category).put("theme", p.theme)
                        .put("title", p.title).put("photographer", p.photographer).put("photographer_url", p.photographerUrl)
                        .put("source_url", p.sourceUrl).put("url", p.url));
            }
            try (OutputStream os = new FileOutputStream(new File(remoteDir(c), "index.json"))) {
                os.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
            synchronized (Wallpapers.class) {
                photos = null;  // reload with the new photos
            }
            return mark(c, Secrets.ST_VALID);
        } catch (java.io.IOException e) {
            return mark(c, Secrets.ST_NETWORK);
        } catch (Exception e) {
            return mark(c, Secrets.ST_UNAVAILABLE);
        }
    }

    private static String mark(Context c, String status) {
        Secrets.setStatus(c, Secrets.PEXELS, status);
        return status;
    }
}
