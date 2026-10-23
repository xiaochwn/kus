package com.boxzero.app.core;

import com.google.gson.annotations.SerializedName;

/** One entry of the "sites" array in a TVBox-style config JSON. */
public class Site {

    @SerializedName("key")
    public String key = "";

    @SerializedName("name")
    public String name = "";

    @SerializedName("type")
    public int type = 3;

    @SerializedName("api")
    public String api = "";

    @SerializedName("ext")
    public String ext = "";

    @SerializedName("searchable")
    public int searchable = 1;

    @SerializedName("quickSearch")
    public int quickSearch = 0;

    @SerializedName("changeable")
    public int changeable = 1;

    @SerializedName("filter")
    public String filter = "";

    /** py spider convention shared by the ecosystem: api contains ".py". */
    public boolean isPy() {
        return api != null && api.contains(".py");
    }

    /** jar spider convention: api is a class name like csp_Xxx. */
    public boolean isJar() {
        return api != null && api.startsWith("csp_");
    }

    @Override
    public String toString() {
        return name == null || name.isEmpty() ? key : name;
    }
}
