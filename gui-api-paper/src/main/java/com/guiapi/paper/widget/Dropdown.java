package com.guiapi.paper.widget;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A dropdown list. Clicking expands the option list; selecting an option notifies the server. */
public class Dropdown extends Widget {
    private final List<String> options = new ArrayList<>();
    private int selected;
    private int rowHeight = 16;
    private Consumer<GuiActionEvent> selectHandler;

    public Dropdown(String id, int x, int y, int width, List<String> options) {
        super(id, x, y, width, 18);
        if (options != null) this.options.addAll(options);
    }

    public List<String> options() { return options; }
    public int selected() { return selected; }
    public int rowHeight() { return rowHeight; }

    /** 收起时的高度，默认 18；字号较大时可以一起调高。 */
    public Dropdown height(int px) {
        size(width(), Math.max(12, px));
        return this;
    }

    /** 展开后每一行的高度，默认 16。 */
    public Dropdown rowHeight(int px) {
        this.rowHeight = Math.max(10, px);
        return this;
    }

    public Dropdown options(List<String> values) {
        this.options.clear();
        if (values != null) this.options.addAll(values);
        if (selected >= options.size()) selected = 0;
        return this;
    }

    public Dropdown selected(int selected) { this.selected = selected; return this; }

    /**
     * Called on the main server thread with the chosen option
     * (see {@link GuiActionEvent#valueAsInt()} and {@link GuiActionEvent#valueAsString()}).
     */
    public Dropdown onSelect(Consumer<GuiActionEvent> handler) { this.selectHandler = handler; return this; }
    public Consumer<GuiActionEvent> selectHandler() { return selectHandler; }

    @Override protected String type() { return "dropdown"; }

    @Override protected void writeProperties(JsonObject json) {
        JsonArray arr = new JsonArray();
        for (String option : options) arr.add(option);
        json.add("options", arr);
        json.addProperty("selected", selected);
        json.addProperty("rowHeight", rowHeight);
    }
}
