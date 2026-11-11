package com.guiapi.paper;

import com.google.gson.JsonElement;
import com.guiapi.paper.widget.Widget;
import org.bukkit.entity.Player;

/** Context passed to widget event handlers. Always fired on the main server thread. */
public final class GuiActionEvent {
    private final Player player;
    private final GuiWindow window;
    private final Widget widget;
    private final String action;
    private final JsonElement value;

    public GuiActionEvent(Player player, GuiWindow window, Widget widget, String action, JsonElement value) {
        this.player = player;
        this.window = window;
        this.widget = widget;
        this.action = action;
        this.value = value;
    }

    public Player player() { return player; }
    public GuiWindow window() { return window; }
    public Widget widget() { return widget; }
    /** "click", "change", "toggle", "select", "close" or "esc". */
    public String action() { return action; }
    public JsonElement rawValue() { return value; }

    public double valueAsDouble() { return value == null ? 0 : value.getAsDouble(); }
    public boolean valueAsBoolean() { return value != null && value.getAsBoolean(); }
    public int valueAsInt() { return value == null ? 0 : value.getAsInt(); }
    public String valueAsString() { return value == null ? "" : value.getAsString(); }
}
