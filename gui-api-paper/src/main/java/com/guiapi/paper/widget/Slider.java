package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/** A horizontal slider. The current value is sent to the server when the player releases the drag. */
public class Slider extends Widget {
    private double min = 0;
    private double max = 100;
    private double step = 1;
    private double value = 0;
    private String label = "";
    private Consumer<GuiActionEvent> changeHandler;

    public Slider(String id, int x, int y, int width) {
        super(id, x, y, width, 20);
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public double value() { return value; }
    public String label() { return label; }

    public Slider min(double min) { this.min = min; return this; }
    public Slider max(double max) { this.max = max; return this; }
    public Slider step(double step) { this.step = step; return this; }
    public Slider value(double value) { this.value = value; return this; }
    public Slider label(String label) { this.label = label; return this; }

    /** Called on the main server thread with the new value (see {@link GuiActionEvent#valueAsDouble()}). */
    public Slider onChange(Consumer<GuiActionEvent> handler) { this.changeHandler = handler; return this; }
    public Consumer<GuiActionEvent> changeHandler() { return changeHandler; }

    @Override protected String type() { return "slider"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("min", min);
        json.addProperty("max", max);
        json.addProperty("step", step);
        json.addProperty("value", value);
        json.addProperty("label", label);
    }
}
