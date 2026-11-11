package com.guiapi.paper.widget;

import com.google.gson.JsonObject;

/** A static text label. */
public class Label extends Widget {
    private String text;
    private int color = 0xFFFFFFFF; // ARGB
    private boolean centered;
    private float scale = 1.0f;

    public Label(String id, int x, int y, String text) {
        super(id, x, y, 0, 9);
        this.text = text;
    }

    public String text() { return text; }
    public int color() { return color; }
    public boolean centered() { return centered; }
    public float scale() { return scale; }

    public Label text(String text) { this.text = text; return this; }

    /** Accepts "#RRGGBB" or "#AARRGGBB". */
    public Label color(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        long v = Long.parseLong(s, 16);
        this.color = s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
        return this;
    }

    public Label color(int argb) { this.color = argb; return this; }
    public Label centered(boolean centered) { this.centered = centered; return this; }
    public Label scale(float scale) { this.scale = scale; return this; }

    @Override protected String type() { return "label"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("text", text);
        json.addProperty("color", color);
        json.addProperty("center", centered);
        json.addProperty("scale", scale);
    }
}
