package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/** A checkbox with a label. */
public class Checkbox extends Widget {
    private String label;
    private boolean checked;
    private Consumer<GuiActionEvent> toggleHandler;

    public Checkbox(String id, int x, int y, String label, boolean checked) {
        super(id, x, y, 0, 12);
        this.label = label;
        this.checked = checked;
    }

    public String label() { return label; }
    public boolean checked() { return checked; }

    public Checkbox label(String label) { this.label = label; return this; }
    public Checkbox checked(boolean checked) { this.checked = checked; return this; }

    /** Called on the main server thread with the new state (see {@link GuiActionEvent#valueAsBoolean()}). */
    public Checkbox onToggle(Consumer<GuiActionEvent> handler) { this.toggleHandler = handler; return this; }
    public Consumer<GuiActionEvent> toggleHandler() { return toggleHandler; }

    @Override protected String type() { return "checkbox"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("label", label);
        json.addProperty("checked", checked);
    }
}
