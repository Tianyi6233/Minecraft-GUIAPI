package com.guiapi.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A widget description received from the server. Properties are read from the backing JSON. */
public final class WidgetModel {
    private final JsonObject json;

    public WidgetModel(JsonObject json) {
        this.json = json;
    }

    public String type() { return str("type"); }
    public String id() { return str("id"); }
    public String tooltip() { return str("tooltip"); }

    /** x/y < 0 means: center of the content area plus the given offset. */
    public int x() { return num("x"); }
    public int y() { return num("y"); }
    public int w() { return num("w"); }
    public int h() { return num("h"); }

    public String str(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    public boolean has(String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    public int num(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }

    public double dbl(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
    }

    public boolean bool(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    public List<String> stringList(String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = json.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) out.add(item.getAsString());
        }
        return out;
    }

    /** Merges changed properties from an update_widget message. */
    public void merge(JsonObject props) {
        for (String key : props.keySet()) {
            json.add(key, props.get(key));
        }
    }

    public JsonArray rawOptions() {
        JsonElement e = json.get("options");
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
}
