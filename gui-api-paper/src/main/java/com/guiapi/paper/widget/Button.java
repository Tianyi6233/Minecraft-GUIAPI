package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/** A clickable button. The server binds the action via {@link #onClick(Consumer)}. */
public class Button extends Widget {
    private String text;
    private Consumer<GuiActionEvent> clickHandler;
    private Integer bgColor;
    private Integer hoverColor;
    private Integer textColor;
    private int radius;

    public Button(String id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height);
        this.text = text;
    }

    public String text() { return text; }
    public Button text(String text) { this.text = text; return this; }

    /** Optional button background color ("#RRGGBB" / "#AARRGGBB"). */
    public Button backgroundColor(String hex) { this.bgColor = Panel.parseColor(hex); return this; }
    public Button backgroundColor(int argb) { this.bgColor = argb; return this; }

    public Button hoverColor(String hex) { this.hoverColor = Panel.parseColor(hex); return this; }
    public Button hoverColor(int argb) { this.hoverColor = argb; return this; }

    public Button textColor(String hex) { this.textColor = Panel.parseColor(hex); return this; }
    public Button textColor(int argb) { this.textColor = argb; return this; }

    /** Corner radius in pixels (0 = square). */
    public Button radius(int radius) { this.radius = Math.max(0, radius); return this; }

    /** Bind the server-side action executed when a client clicks this button. */
    public Button onClick(Consumer<GuiActionEvent> handler) { this.clickHandler = handler; return this; }
    public Consumer<GuiActionEvent> clickHandler() { return clickHandler; }

    @Override protected String type() { return "button"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("text", text);
        if (bgColor != null) json.addProperty("bgColor", bgColor);
        if (hoverColor != null) json.addProperty("hoverColor", hoverColor);
        if (textColor != null) json.addProperty("textColor", textColor);
        if (radius > 0) json.addProperty("radius", radius);
    }
}
