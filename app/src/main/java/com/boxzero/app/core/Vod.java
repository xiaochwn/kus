package com.boxzero.app.core;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Video item. Field names follow the ecosystem JSON contract:
 * homeVideoContent/categoryContent/searchContent return {"list":[Vod...]},
 * detailContent returns {"list":[Vod with vod_play_from / vod_play_url]}.
 */
public class Vod {

    @SerializedName("vod_id")
    public String vodId = "";

    @SerializedName("vod_name")
    public String vodName = "";

    @SerializedName("vod_pic")
    public String vodPic = "";

    @SerializedName("vod_remarks")
    public String vodRemarks = "";

    @SerializedName("vod_content")
    public String vodContent = "";

    @SerializedName("vod_play_from")
    public String vodPlayFrom = "";

    @SerializedName("vod_play_url")
    public String vodPlayUrl = "";

    public static class Src {
        public String name = "";
        public final List<Ep> eps = new ArrayList<>();
    }

    public static class Ep {
        public String name = "";
        public String url = "";
    }

    /** Parses "from1$$$from2" / "ep1$url1#ep2$url2$$$..." into sources. */
    public List<Src> parseSources() {
        List<Src> out = new ArrayList<>();
        if (vodPlayFrom == null || vodPlayUrl == null) return out;
        String[] froms = vodPlayFrom.split("\\$\\$\\$");
        String[] urlGroups = vodPlayUrl.split("\\$\\$\\$", -1);
        for (int i = 0; i < froms.length; i++) {
            Src src = new Src();
            src.name = froms[i];
            if (i < urlGroups.length) {
                for (String ep : urlGroups[i].split("#")) {
                    if (ep.isEmpty()) continue;
                    String[] kv = ep.split("\\$", 2);
                    Ep e = new Ep();
                    if (kv.length > 1) {
                        e.name = kv[0];
                        e.url = kv[1];
                    } else {
                        e.name = "第" + (src.eps.size() + 1) + "集";
                        e.url = kv[0];
                    }
                    src.eps.add(e);
                }
            }
            out.add(src);
        }
        return out;
    }
}
