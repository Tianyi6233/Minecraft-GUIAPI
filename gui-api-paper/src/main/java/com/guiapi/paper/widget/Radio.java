package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/**
 * A radio button. Radio buttons sharing the same {@link #group()} inside one window
 * are mutually exclusive (handled automatically by the client).
 */
public class Radio extends Widget {
    private String label;
    private String group;
    private boolean checked;
    private Consumer<GuiActionEvent> toggleHandler;

    public Radio(String id, int x, int y, String label, String group, boolean checked) {
        super(id, x, y, 0, 12);
        this.label = label;
        this.group = group;
        this.checked = checked;
    }

    public String label() { return label; }
    public String group() { return group; }
    public boolean checked() { return checked; }

    public Radio label(String label) { this.label = label; return this; }
    public Radio group(String group) { this.group = group; return this; }
    public Radio checked(boolean checked) { this.checked = checked; return this; }

    /** Called on the main server thread when this radio becomes selected. */
    public Radio onToggle(Consumer<GuiActionEvent> handler) { this.toggleHandler = handler; return this; }
    public Consumer<GuiActionEvent> toggleHandler() { return toggleHandler; }

    @Override protected String type() { return "radio"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("label", label);
        json.addProperty("group", group);
        json.addProperty("checked", checked);
    }
}
