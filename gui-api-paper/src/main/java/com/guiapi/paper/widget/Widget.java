package com.guiapi.paper.widget;

import com.google.gson.JsonObject;

/**
 * Base class of all widgets that can be placed inside a {@link com.guiapi.paper.GuiWindow}.
 * Coordinates are relative to the window's content area (below the title bar).
 */
public abstract class Widget {
    private final String id;
    private int x;
    private int y;
    private int width;
    private int height;
    private String tooltip;
    private Boolean visible;
    private Double xPct;
    private Double yPct;
    private Double wPct;
    private Double hPct;
    private Double aspect;
    private String dragGroup;
    private Integer glowColor;
    private Boolean hoverLift;
    private Integer animMs;
    private String animEase;

    protected Widget(String id, int x, int y, int width, int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String id() { return id; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public String tooltip() { return tooltip; }

    public Widget position(int x, int y) { this.x = x; this.y = y; return this; }
    public Widget size(int width, int height) { this.width = width; this.height = height; return this; }
    public Widget tooltip(String tooltip) { this.tooltip = tooltip; return this; }

    /**
     * Hides the widget without removing it, so it can be shown again with
     * {@code updateWidget(..., Map.of("visible", true))}. Hidden widgets are neither drawn
     * nor clickable.
     */
    public Widget visible(boolean visible) { this.visible = visible; return this; }

    /**
     * Percentage-based layout for responsive fullscreen windows.
     * The final coordinate is {@code percent * windowWidth / 100 + x} (an offset is still applied).
     */
    public Widget xPercent(double percent) { this.xPct = percent; return this; }

    public Widget yPercent(double percent) { this.yPct = percent; return this; }

    public Widget widthPercent(double percent) { this.wPct = percent; return this; }

    public Widget heightPercent(double percent) { this.hPct = percent; return this; }

    /**
     * Keeps a fixed width/height ratio: the height is derived from the current width.
     * Cards are 160x225, so {@code aspect(160.0 / 225.0)} keeps them undistorted at any
     * resolution and works together with {@link #widthPercent(double)}.
     */
    public Widget aspect(double widthOverHeight) {
        if (widthOverHeight > 0) this.aspect = widthOverHeight;
        return this;
    }

    /**
     * Puts the widget into a horizontally draggable group. All widgets sharing the same group
     * name move together when the player drags one of them with the mouse. Groups that fit
     * inside the window do not move. Used for card hands, film strips and other wide rows.
     */
    public Widget dragGroup(String group) {
        this.dragGroup = group == null || group.isBlank() ? null : group;
        return this;
    }

    /**
     * Draws a soft glow around the widget in the given colour. Used for the selected card,
     * the current player's seat and other "look at me" highlights.
     */
    public Widget glow(String hex) {
        this.glowColor = Panel.parseColor(hex);
        return this;
    }

    /**
     * Lifts the widget a few pixels while the mouse hovers it, so cards feel clickable.
     * Purely visual; the click area stays where the widget is placed.
     */
    public Widget hoverLift(boolean lift) {
        this.hoverLift = lift;
        return this;
    }

    /**
     * 定时补间动画：控件位置变化时，用 {@code ms} 毫秒从旧位置滑到新位置，而不是瞬移。
     * 典型用法是让平底锅从自己飞向对手、手牌收纳成牌堆后再换位。
     *
     * @param ms   动画时长（毫秒），{@code <= 0} 表示恢复默认的平滑跟随
     * @param ease {@code linear}（匀速）、{@code in}（先慢后快）、
     *             {@code out}（先快后慢，默认，适合"扔出去"）、{@code inOut}（两端慢，适合换位）
     */
    public Widget anim(int ms, String ease) {
        this.animMs = ms;
        this.animEase = ease == null || ease.isBlank() ? "out" : ease;
        return this;
    }

    public Widget anim(int ms) {
        return anim(ms, "out");
    }

    /** Widget type name written into the protocol JSON. */
    protected abstract String type();

    /** Subclasses add their own properties here. */
    protected abstract void writeProperties(JsonObject json);

    public final JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type());
        json.addProperty("id", id);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("w", width);
        json.addProperty("h", height);
        if (tooltip != null) json.addProperty("tooltip", tooltip);
        if (visible != null) json.addProperty("visible", visible);
        if (xPct != null) json.addProperty("xPct", xPct);
        if (yPct != null) json.addProperty("yPct", yPct);
        if (wPct != null) json.addProperty("wPct", wPct);
        if (hPct != null) json.addProperty("hPct", hPct);
        if (aspect != null) json.addProperty("aspect", aspect);
        if (dragGroup != null) json.addProperty("dragGroup", dragGroup);
        if (glowColor != null) json.addProperty("glowColor", glowColor);
        if (hoverLift != null) json.addProperty("hoverLift", hoverLift);
        if (animMs != null) {
            json.addProperty("animMs", animMs);
            json.addProperty("animEase", animEase);
        }
        writeProperties(json);
        return json;
    }
}
