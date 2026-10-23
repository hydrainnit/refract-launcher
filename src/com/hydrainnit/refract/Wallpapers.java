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
 * Photographic wallpapers. The APK ships only the starter set's metadata (assets/wallpapers/
 * index.json: title, category, photographer, credit links); the pictures themselves are
 * downloaded on the TV, straight from Pexels' image CDN (no API key, not the paused API), at the
 * quality chosen in Settings (1080p or 4K), and kept in files/wallpapers/. A photo that has not
 * been downloaded yet and no internet: callers get null and show Refract's gradients instead.
 * A remote Pexels API source uses the same Photo model and is off by default: Pexels API access
 * is paused, so nothing here calls the API unless PEXELS_API_PAUSED is false, the user saved a
 * key and switched remote wallpapers on.
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
    /** Photo quality: "1080" (1920x1080, ~0.4 MB a photo) or "2160" (3840x2160, ~1.5 MB). */
    static final String K_QUALITY = "photo_quality";

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
     * Decodes one photo no wider than maxWidth (a power-of-two sample of the downloaded file),
     * off the UI thread, downloading it first if needed. Null when it is not on disk and there
     * is no internet: show the gradient instead. The caller owns the bitmap.
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
        File f = local(c, p, true);
        if (f == null) throw new java.io.FileNotFoundException(p.id);
        return new FileInputStream(f);
    }

    // ------------------------------------------------------------------ on-device download

    static String quality(Context c) {
        return c.getSharedPreferences("wallpapers", Context.MODE_PRIVATE).getString(K_QUALITY, "1080");
    }

    static void setQuality(Context c, String q) {
        c.getSharedPreferences("wallpapers", Context.MODE_PRIVATE).edit().putString(K_QUALITY, q).apply();
    }

    static File dir(Context c, String q) {
        File d = new File(new File(c.getFilesDir(), "wallpapers"), q);
        d.mkdirs();
        return d;
    }

    private static String pexelsId(Photo p) {
        int dot = p.file.lastIndexOf('.');
        return dot > 0 ? p.file.substring(0, dot) : p.file;
    }

    /** True when this photo can be shown without the internet (at either quality). */
    static boolean downloaded(Context c, Photo p) {
        return p.remote() || new File(dir(c, "1080"), pexelsId(p) + ".jpg").exists()
                || new File(dir(c, "2160"), pexelsId(p) + ".jpg").exists();
    }

    /**
     * The photo's file at the chosen quality, downloading it first when {@code fetch} (off the UI
     * thread only). Offline, the other quality is used if it is on disk; otherwise null.
     */
    static File local(Context c, Photo p, boolean fetch) {
        String q = quality(c), other = q.equals("2160") ? "1080" : "2160";
        String name = pexelsId(p) + ".jpg";
        File want = new File(dir(c, q), name);
        if (want.exists()) return want;
        if (fetch && download(c, p, q, want)) {
            new File(dir(c, other), name).delete();  // the other quality is no longer needed
            return want;
        }
        File alt = new File(dir(c, other), name);
        return alt.exists() ? alt : null;
    }

    private static final Object FETCH = new Object();

    /** One photo from images.pexels.com, cropped to 16:9 by the CDN; written atomically. */
    private static boolean download(Context c, Photo p, String q, File to) {
        if (!online(c)) return false;
        synchronized (FETCH) {  // one download at a time; a second caller then finds the file
            if (to.exists()) return true;
            String id = pexelsId(p);
            int w = q.equals("2160") ? 3840 : 1920, h = w * 9 / 16;
            File tmp = new File(to.getPath() + ".part");
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL("https://images.pexels.com/photos/" + id + "/pexels-photo-" + id
                        + ".jpeg?auto=compress&cs=tinysrgb&fit=crop&w=" + w + "&h=" + h).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(20000);
                if (con.getResponseCode() != 200) return false;
                try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[32768];
                    for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
                }
                return tmp.length() > 0 && tmp.renameTo(to);
            } catch (Exception e) {
                return false;
            } finally {
                if (con != null) con.disconnect();
                tmp.delete();
            }
        }
    }

    static boolean online(Context c) {
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * While online, fetches every photo Home and the screensaver can show: the background, and
     * for a photo screensaver all the photos in its category (or its one photo), so the whole
     * rotation still works when the internet is gone. Photos already on disk cost nothing. Off
     * the UI thread.
     */
    static void prefetch(Context c) {
        if (!online(c)) return;
        Photo sel = selected(c);
        if (sel != null && !sel.remote()) local(c, sel, true);
        Ambient.Settings s = Ambient.Settings.load(c);
        List<Photo> want = new ArrayList<>();
        if (s.mode.equals(Ambient.SINGLE)) {
            Photo p = byId(c, s.singleId);
            if (p != null) want.add(p);
        } else if (s.mode.equals(Ambient.PHOTOS)) {
            want.addAll(inCategory(c, s.category));
            if (want.isEmpty()) want.addAll(all(c));  // the screensaver falls back to every photo too
        }
        for (Photo p : want) {
            if (!p.remote() && !online(c)) return;
            if (!p.remote()) local(c, p, true);
        }
    }

    /** Settings: space used by downloaded photos, in bytes. */
    static long bytesOnDisk(Context c) {
        long n = 0;
        for (String q : new String[]{"1080", "2160"}) {
            File[] fs = dir(c, q).listFiles();
            if (fs != null) for (File f : fs) n += f.length();
        }
        return n;
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
