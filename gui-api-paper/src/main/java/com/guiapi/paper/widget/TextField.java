package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/**
 * Single-line text input. The client captures keyboard input locally and sends the final
 * text back on Enter or when the field loses focus (event action {@code "text"}).
 */
public class TextField extends Widget {
    private String text = "";
    private String placeholder = "";
    private int maxLength = 64;
    private Integer bgColor;
    private Integer borderColor;
    private Integer textColor;
    private boolean live;
    private Consumer<GuiActionEvent> changeHandler;

    public TextField(String id, int x, int y, int width, int height) {
        super(id, x, y, width, height);
    }

    public String text() { return text; }

    public TextField text(String text) { this.text = text == null ? "" : text; return this; }

    public TextField placeholder(String placeholder) { this.placeholder = placeholder; return this; }

    public TextField maxLength(int maxLength) { this.maxLength = Math.max(1, maxLength); return this; }

    public TextField backgroundColor(String hex) { this.bgColor = Panel.parseColor(hex); return this; }
    public TextField backgroundColor(int argb) { this.bgColor = argb; return this; }

    public TextField borderColor(String hex) { this.borderColor = Panel.parseColor(hex); return this; }
    public TextField borderColor(int argb) { this.borderColor = argb; return this; }

    public TextField textColor(String hex) { this.textColor = Panel.parseColor(hex); return this; }
    public TextField textColor(int argb) { this.textColor = argb; return this; }

    /**
     * Sends the text to the server after <b>every</b> keystroke instead of only on Enter/blur.
     * Required for live filtering (e.g. autocompleting player names); keep it off otherwise
     * because it costs one packet per character.
     */
    public TextField live(boolean live) { this.live = live; return this; }

    public boolean live() { return live; }

    /** Called when the player presses Enter or leaves the field. {@code event.valueAsString()} is the new text. */
    public TextField onChange(Consumer<GuiActionEvent> handler) { this.changeHandler = handler; return this; }

    public Consumer<GuiActionEvent> changeHandler() { return changeHandler; }

    @Override
    protected String type() {
        return "text_field";
    }

    @Override
    protected void writeProperties(JsonObject json) {
        json.addProperty("text", text);
        json.addProperty("placeholder", placeholder);
        json.addProperty("maxLength", maxLength);
        if (bgColor != null) json.addProperty("bgColor", bgColor);
        if (borderColor != null) json.addProperty("borderColor", borderColor);
        if (textColor != null) json.addProperty("textColor", textColor);
        if (live) json.addProperty("live", true);
    }
}
