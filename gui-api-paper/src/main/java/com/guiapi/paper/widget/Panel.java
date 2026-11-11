package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/**
 * A rectangle used for card backgrounds, headers, sidebars, etc.
 *
 * <p>Panels can optionally react to the mouse: {@link #hoverColor(String)} highlights them
 * on hover and {@link #onClick(Consumer)} makes the whole panel clickable, which is handy
 * for menu cards.</p>
 */
public class Panel extends Widget {
    private int bgColor = 0x00000000;
    private int borderColor = 0xFF000000;
    private int borderWidth = 1;
    private int radius;
    private Integer hoverColor;
    private Integer gradTop;
    private Integer gradBottom;
    private Consumer<GuiActionEvent> clickHandler;

    public Panel(String id, int x, int y, int width, int height) {
        super(id, x, y, width, height);
    }

    /** Accepts "#RRGGBB" or "#AARRGGBB" ("#00000000" = fully transparent). */
    public Panel backgroundColor(String hex) {
        this.bgColor = parseColor(hex);
        return this;
    }

    public Panel backgroundColor(int argb) {
        this.bgColor = argb;
        return this;
    }

    public Panel borderColor(String hex) {
        this.borderColor = parseColor(hex);
        return this;
    }

    public Panel borderColor(int argb) {
        this.borderColor = argb;
        return this;
    }

    /** 0 removes the border entirely. */
    public Panel borderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    /** Corner radius in pixels (0 = square). Purely visual; layout stays rectangular. */
    public Panel radius(int radius) {
        this.radius = Math.max(0, radius);
        return this;
    }

    /** Background used while the mouse is over the panel. */
    public Panel hoverColor(String hex) {
        this.hoverColor = parseColor(hex);
        return this;
    }

    public Panel hoverColor(int argb) {
        this.hoverColor = argb;
        return this;
    }

    /** Makes the whole panel clickable. The event action is {@code "click"}. */
    public Panel onClick(Consumer<GuiActionEvent> handler) {
        this.clickHandler = handler;
        return this;
    }

    /**
     * Vertical gradient background (top colour -> bottom colour). Only used when the panel has
     * no corner radius; square panels are the common case for fullscreen backdrops and headers.
     */
    public Panel gradient(String topHex, String bottomHex) {
        this.gradTop = parseColor(topHex);
        this.gradBottom = parseColor(bottomHex);
        return this;
    }

    public Consumer<GuiActionEvent> clickHandler() {
        return clickHandler;
    }

    static int parseColor(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        long v = Long.parseLong(s, 16);
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    @Override
    protected String type() {
        return "panel";
    }

    @Override
    protected void writeProperties(JsonObject json) {
        json.addProperty("bgColor", bgColor);
        json.addProperty("borderColor", borderColor);
        json.addProperty("borderWidth", borderWidth);
        if (radius > 0) json.addProperty("radius", radius);
        if (hoverColor != null) json.addProperty("hoverColor", hoverColor);
        if (gradTop != null) json.addProperty("bgGradTop", gradTop);
        if (gradBottom != null) json.addProperty("bgGradBottom", gradBottom);
        if (clickHandler != null) json.addProperty("clickable", true);
    }
}
