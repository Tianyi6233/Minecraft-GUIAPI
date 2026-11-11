package com.guiapi.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** A window description received from the server. */
public final class WindowModel {
    public final String id;
    public final String title;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final boolean draggable;
    public final boolean fullscreen;
    public final int bgColor;
    public final int borderColor;
    public final int borderWidth;
    public final int titleBarColor;
    public final int titleColor;
    public final boolean titleBar;
    public final int titleHeight;
    public final int dim;
    public final boolean alwaysOnTop;
    /** When false, Esc / the title-bar close button do not close the window; an "esc" event is sent. */
    public final boolean closeOnEsc;
    public final String style;
    /** Optional font id for text in this window (empty = vanilla default font). */
    public final String font;
    public final List<WidgetModel> widgets = new ArrayList<>();

    public WindowModel(JsonObject json) {
        this.id = opt(json, "id", "window");
        this.title = opt(json, "title", "");
        this.x = optInt(json, "x", -1);
        this.y = optInt(json, "y", -1);
        this.width = optInt(json, "w", 260);
        this.height = optInt(json, "h", 160);
        this.draggable = !json.has("draggable") || json.get("draggable").getAsBoolean();
        this.fullscreen = json.has("fullscreen") && json.get("fullscreen").getAsBoolean();
        this.bgColor = optInt(json, "bgColor", 0xF018181C);
        this.borderColor = optInt(json, "borderColor", 0xFF000000);
        this.borderWidth = optInt(json, "borderWidth", 1);
        this.titleBarColor = optInt(json, "titleBarColor", 0xFF2D2F38);
        this.titleColor = optInt(json, "titleColor", 0xFFFFFFFF);
        this.titleBar = !json.has("titleBar") || json.get("titleBar").getAsBoolean();
        this.titleHeight = optInt(json, "titleHeight", 16);
        this.dim = optInt(json, "dim", 0);
        this.alwaysOnTop = json.has("alwaysOnTop") && json.get("alwaysOnTop").getAsBoolean();
        this.closeOnEsc = !json.has("closeOnEsc") || json.get("closeOnEsc").getAsBoolean();
        this.style = opt(json, "style", "default");
        this.font = opt(json, "font", "");
        JsonArray arr = json.has("widgets") && json.get("widgets").isJsonArray()
                ? json.getAsJsonArray("widgets") : new JsonArray();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) widgets.add(new WidgetModel(e.getAsJsonObject()));
        }
    }

    public WidgetModel widget(String id) {
        for (WidgetModel w : widgets) if (w.id().equals(id)) return w;
        return null;
    }

    private static String opt(JsonObject json, String key, String def) {
        return json.has(key) ? json.get(key).getAsString() : def;
    }

    private static int optInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }
}
