package com.hydrainnit.refract;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Movies from TMDB, shared by the Movies tab, the Movies Home Theme and the movie screensaver:
 * one model (Suggestions.Item with type "movie"), one disk cache per collection, one logo cache
 * and one image cache (Suggestions.image), so the same title is never fetched or stored twice.
 * Everything here blocks; call it off the UI thread. Nothing is invented when TMDB has no data:
 * a missing logo falls back to the title in type, a missing rating or date is left out.
 */
final class Movies {
    private Movies() { }

    /** providerId marking TMDB movie items (not a streaming service, so Item.provider() is null). */
    static final int TMDB_MOVIE = -10;
    static final long MAX_AGE_MS = 6L * 60 * 60 * 1000;
    static final String K_THEME = "home_theme", THEME_MOVIES = "movies", THEME_FEED = "feed", K_ROTATE = "movies_rotate";

    /** Collections: key, row title, TMDB path. */
    static final String[][] COLLECTIONS = {
            {"trending", "Trending This Week", "/trending/movie/week?language=en-US"},
            {"popular", "Popular", "/movie/popular?language=en-US&region=IN"},
            {"top_rated", "Top Rated", "/movie/top_rated?language=en-US"},
            {"upcoming", "Coming Soon", "/movie/upcoming?language=en-US&region=IN"},
            {"action", "Action", "/discover/movie?with_genres=28&sort_by=popularity.desc&language=en-US"},
            {"comedy", "Comedy", "/discover/movie?with_genres=35&sort_by=popularity.desc&language=en-US"},
            {"scifi", "Science Fiction", "/discover/movie?with_genres=878&sort_by=popularity.desc&language=en-US"},
            {"animation", "Animation", "/discover/movie?with_genres=16&sort_by=popularity.desc&language=en-US"}};

    static boolean configured(Context c) {
        return Secrets.has(c, Secrets.TMDB);
    }

    // ------------------------------------------------------------------ Movies Home Theme (per profile)

    static boolean homeTheme(Context c) {
        return THEME_MOVIES.equals(Profiles.homePrefs(c).getString(K_THEME, THEME_FEED));
    }

    /** Only the theme flag changes: news and recommendation settings are left as they were. */
    static void setHomeTheme(Context c, boolean on) {
        Profiles.homePrefs(c).edit().putString(K_THEME, on ? THEME_MOVIES : THEME_FEED).apply();
    }

    /** Seconds between featured movies on Home; 0 = only when you move through them yourself. */
    static int rotateSeconds(Context c) {
        return Profiles.homePrefs(c).getInt(K_ROTATE, 0);
    }

    // ------------------------------------------------------------------ catalog

    static String[] collection(String key) {
        for (String[] k : COLLECTIONS) if (k[0].equals(key)) return k;
        return COLLECTIONS[0];
    }

    static File cacheFile(Context c, String key) {
        return new File(c.getFilesDir(), "movies_" + key + ".json");
    }

    /** Cached list for a collection, or null (missing, or stale when allowStale is false). */
    static List<Suggestions.Item> cached(Context c, String key, boolean allowStale) {
        File f = cacheFile(c, key);
        if (!f.exists()) return null;
        if (!allowStale && System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            JSONArray a = new JSONArray(new String(Suggestions.readAll(in), StandardCharsets.UTF_8));
            List<Suggestions.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(Suggestions.Item.fromJson(a.getJSONObject(i)));
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Fresh cache if there is one, else TMDB; when TMDB fails, the stale cache (or the error). */
    static List<Suggestions.Item> load(Context c, String key) throws Exception {
        List<Suggestions.Item> fresh = cached(c, key, false);
        if (fresh != null) return fresh;
        try {
            return fetch(c, key);
        } catch (Exception e) {
            List<Suggestions.Item> stale = cached(c, key, true);
            if (stale != null) return stale;
            throw e;
        }
    }

    static List<Suggestions.Item> fetch(Context c, String key) throws Exception {
        Map<Integer, String> genres = Suggestions.genres(c, "movie");
        JSONArray results = Suggestions.get(c, collection(key)[2]).optJSONArray("results");
        List<Suggestions.Item> out = new ArrayList<>();
        for (int i = 0; results != null && i < results.length() && out.size() < 20; i++) {
            JSONObject r = results.getJSONObject(i);
            String backdrop = r.optString("backdrop_path", "");
            if (backdrop.isEmpty() || backdrop.equals("null") || r.optBoolean("adult")) continue;
            Suggestions.Item it = new Suggestions.Item();
            it.id = r.getInt("id");
            it.type = "movie";
            it.providerId = TMDB_MOVIE;
            it.title = r.optString("title");
            it.overview = r.optString("overview");
            it.backdrop = backdrop;
            String poster = r.optString("poster_path", "");
            it.poster = poster.equals("null") ? "" : poster;
            it.release = r.optString("release_date", "");
            it.year = it.release.length() >= 4 ? it.release.substring(0, 4) : "";
            it.rating = r.optDouble("vote_average", 0);
            if (r.optInt("vote_count", 0) < 20) it.rating = 0;  // a handful of votes is not a rating
            it.genres = Suggestions.genreNames(r.optJSONArray("genre_ids"), genres);
            it.logo = logos(c).get(it.id);
            out.add(it);
        }
        JSONArray a = new JSONArray();
        for (Suggestions.Item it : out) a.put(it.toJson());
        try (OutputStream os = new FileOutputStream(cacheFile(c, key))) {
            os.write(a.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    // ------------------------------------------------------------------ title logos and details

    private static Map<Integer, String> logoCache;

    private static synchronized Map<Integer, String> logos(Context c) {
        if (logoCache != null) return logoCache;
        logoCache = new HashMap<>();
        File f = new File(c.getFilesDir(), "movie_logos.json");
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                JSONObject o = new JSONObject(new String(Suggestions.readAll(in), StandardCharsets.UTF_8));
                for (java.util.Iterator<String> k = o.keys(); k.hasNext(); ) {
                    String id = k.next();
                    logoCache.put(Integer.parseInt(id), o.getString(id));
                }
            } catch (Exception e) {
                // rebuilt as logos are looked up again
            }
        }
        return logoCache;
    }

    private static synchronized void saveLogos(Context c) {
        try (OutputStream os = new FileOutputStream(new File(c.getFilesDir(), "movie_logos.json"))) {
            JSONObject o = new JSONObject();
            for (Map.Entry<Integer, String> e : logoCache.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
            os.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // not cached: looked up again next time
        }
    }

    /**
     * The movie's title-treatment logo path ("" when TMDB has none), English preferred. Looked
     * up once per movie and remembered on disk.
     */
    static String logoFor(Context c, Suggestions.Item it) {
        if (it.logo != null) return it.logo;
        String known = logos(c).get(it.id);
        if (known != null) return it.logo = known;
        String best = "";
        try {
            JSONArray l = Suggestions.get(c, "/movie/" + it.id + "/images?include_image_language=en,null").optJSONArray("logos");
            for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                if (best.isEmpty() || "en".equals(o.optString("iso_639_1"))) {
                    best = o.optString("file_path");
                    if ("en".equals(o.optString("iso_639_1"))) break;
                }
            }
        } catch (Exception e) {
            return it.logo = "";  // offline: try again another time, don't remember "no logo"
        }
        synchronized (Movies.class) {
            logos(c).put(it.id, best);
            saveLogos(c);
        }
        return it.logo = best;
    }

    private static final Map<Integer, Integer> runtimes = new HashMap<>();

    /** Fills in the runtime (one detail request per movie, kept for this session). */
    static void details(Context c, Suggestions.Item it) {
        if (it.runtime > 0) return;
        synchronized (runtimes) {
            Integer r = runtimes.get(it.id);
            if (r != null) {
                it.runtime = r;
                return;
            }
        }
        try {
            int r = Suggestions.get(c, "/movie/" + it.id + "?language=en-US").optInt("runtime", 0);
            synchronized (runtimes) {
                runtimes.put(it.id, r);
            }
            it.runtime = r;
        } catch (Exception e) {
            // shown without a runtime
        }
    }

    // ------------------------------------------------------------------ formatting

    /** "14 Mar 2025" (or "Coming 14 Mar 2025" for a future date); "" when TMDB has no date. */
    static String releaseLabel(Suggestions.Item it) {
        if (it.release == null || it.release.length() < 10) return it.year == null ? "" : it.year;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date d = in.parse(it.release);
            String s = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(d);
            return d.getTime() > System.currentTimeMillis() ? "Coming " + s : s;
        } catch (Exception e) {
            return it.release;
        }
    }

    /** "TMDB 7.8" when TMDB has a rating with enough votes; "" otherwise. */
    static String ratingLabel(Suggestions.Item it) {
        return it.rating > 0 ? String.format(java.util.Locale.US, "\u2605 %.1f TMDB", it.rating) : "";
    }

    static String runtimeLabel(Suggestions.Item it) {
        if (it.runtime <= 0) return "";
        return it.runtime >= 60 ? (it.runtime / 60) + " h " + (it.runtime % 60) + " min" : it.runtime + " min";
    }

    /** The meta line under a movie's title: rating, release date, genres, runtime (whatever is known). */
    static String meta(Suggestions.Item it) {
        List<String> m = new ArrayList<>();
        String r = ratingLabel(it), d = releaseLabel(it), rt = runtimeLabel(it);
        if (!r.isEmpty()) m.add(r);
        if (!d.isEmpty()) m.add(d);
        if (it.genres != null && !it.genres.isEmpty()) m.add(it.genres);
        if (!rt.isEmpty()) m.add(rt);
        return String.join("   \u00b7   ", m);
    }

    static SharedPreferences prefs(Context c) {
        return Profiles.homePrefs(c);
    }
}
