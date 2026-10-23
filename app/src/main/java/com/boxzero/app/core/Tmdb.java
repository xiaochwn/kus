package com.boxzero.app.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * TMDB (The Movie Database) metadata scraping.
 * <p>
 * Used to fill in missing posters / synopses / ratings on the detail page.
 * Needs a user-supplied v3 API key (free at themoviedb.org).
 */
public class Tmdb {

    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p";

    public static class Result {
        /** "movie" or "tv" */
        public String mediaType = "";
        public long id;
        public String title = "";
        public String originalTitle = "";
        public String overview = "";
        public String posterPath = "";
        public String backdropPath = "";
        public double voteAverage;
        public String date = "";
    }

    public static String posterUrl(String path) {
        return path == null || path.isEmpty() ? "" : IMG + "/w500" + path;
    }

    public static String backdropUrl(String path) {
        return path == null || path.isEmpty() ? "" : IMG + "/w1280" + path;
    }

    /** Search movies + TV. Returns up to ~10 candidates, movies/tv only. */
    public static List<Result> search(String apiKey, String query, String year) throws Exception {
        StringBuilder url = new StringBuilder(API + "/search/multi?api_key=")
                .append(URLEncoder.encode(apiKey, "UTF-8"))
                .append("&language=zh-CN&include_adult=false&page=1&query=")
                .append(URLEncoder.encode(query, "UTF-8"));
        if (year != null && !year.isEmpty()) {
            url.append("&year=").append(URLEncoder.encode(year, "UTF-8"));
        }
        JSONObject root = new JSONObject(httpGet(url.toString()));
        JSONArray arr = root.optJSONArray("results");
        List<Result> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length() && out.size() < 10; i++) {
            JSONObject o = arr.getJSONObject(i);
            String type = o.optString("media_type", "");
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            Result r = new Result();
            r.mediaType = type;
            r.id = o.optLong("id");
            if ("movie".equals(type)) {
                r.title = o.optString("title", "");
                r.originalTitle = o.optString("original_title", "");
                r.date = o.optString("release_date", "");
            } else {
                r.title = o.optString("name", "");
                r.originalTitle = o.optString("original_name", "");
                r.date = o.optString("first_air_date", "");
            }
            r.overview = o.optString("overview", "");
            r.posterPath = o.optString("poster_path", "");
            r.backdropPath = o.optString("backdrop_path", "");
            r.voteAverage = o.optDouble("vote_average", 0);
            out.add(r);
        }
        return out;
    }

    /** Full detail for one item (richer overview, etc.). */
    public static Result detail(String apiKey, String mediaType, long id) throws Exception {
        String url = API + "/" + mediaType + "/" + id
                + "?api_key=" + URLEncoder.encode(apiKey, "UTF-8")
                + "&language=zh-CN";
        JSONObject o = new JSONObject(httpGet(url));
        Result r = new Result();
        r.mediaType = mediaType;
        r.id = id;
        if ("movie".equals(mediaType)) {
            r.title = o.optString("title", "");
            r.originalTitle = o.optString("original_title", "");
            r.date = o.optString("release_date", "");
        } else {
            r.title = o.optString("name", "");
            r.originalTitle = o.optString("original_name", "");
            r.date = o.optString("first_air_date", "");
        }
        r.overview = o.optString("overview", "");
        r.posterPath = o.optString("poster_path", "");
        r.backdropPath = o.optString("backdrop_path", "");
        r.voteAverage = o.optDouble("vote_average", 0);
        return r;
    }

    private static String httpGet(String url) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "BoxZero/1.0").build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("TMDB HTTP " + resp.code());
            }
            return resp.body().string();
        }
    }
}
