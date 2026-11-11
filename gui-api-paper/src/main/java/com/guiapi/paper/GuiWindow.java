package com.guiapi.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.guiapi.paper.widget.Button;
import com.guiapi.paper.widget.Checkbox;
import com.guiapi.paper.widget.Dropdown;
import com.guiapi.paper.widget.ImageWidget;
import com.guiapi.paper.widget.ItemWidget;
import com.guiapi.paper.widget.Label;
import com.guiapi.paper.widget.Panel;
import com.guiapi.paper.widget.Radio;
import com.guiapi.paper.widget.Scrollbar;
import com.guiapi.paper.widget.TextField;
import com.guiapi.paper.widget.Slider;
import com.guiapi.paper.widget.Widget;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A window that can be drawn inside the client. Build it with the addXxx(...) methods,
 * bind actions to interactive widgets, then show it with {@link GuiApi#openWindow}.
 */
public final class GuiWindow {
    private final String id;
    private String title = "";
    private int x = -1; // -1 = centered
    private int y = -1;
    private int width = 260;
    private int height = 160;
    private boolean draggable = true;
    private boolean fullscreen;
    private int bgColor = 0xF018181C;
    private int borderColor = 0xFF000000;
    private int borderWidth = 1;
    private int titleBarColor = 0xFF2D2F38;
    private int titleColor = 0xFFFFFFFF;
    private boolean titleBar = true;
    private int titleHeight = 16;
    private int dim = 0;
    private boolean alwaysOnTop;
    /** When false, Esc and the title-bar close button do not close the window; an "esc" event is sent instead. */
    private boolean closeOnEsc = true;
    private Consumer<GuiActionEvent> escHandler;
    private String style = "default"; // "default" or "minecraft"
    /** Optional font id for this window's text, e.g. "guiapi:sysfont" (empty = client default). */
    private String font = "";
    /** 本窗口的点击音效；clickSoundSet 为 true 时覆盖 GuiApi 的全局设置。 */
    private boolean clickSoundSet;
    private String clickSound;
    private float clickSoundVolume = 0.7f;
    private final Map<String, Widget> widgets = new LinkedHashMap<>();

    public GuiWindow(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public String title() { return title; }
    public int posX() { return x; }
    public int posY() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean draggable() { return draggable; }
    public boolean fullscreen() { return fullscreen; }

    public GuiWindow title(String title) { this.title = title; return this; }

    /** Position of the top-left corner. Use (-1, -1) to center the window (default). */
    public GuiWindow position(int x, int y) { this.x = x; this.y = y; return this; }

    public GuiWindow size(int width, int height) { this.width = width; this.height = height; return this; }

    /** Whether the window can be dragged by its title bar (default true). Ignored in fullscreen. */
    public GuiWindow draggable(boolean draggable) { this.draggable = draggable; return this; }

    /** Fullscreen windows cover the whole screen and cannot be dragged. */
    public GuiWindow fullscreen(boolean fullscreen) { this.fullscreen = fullscreen; return this; }

    /** Window panel background. Supports "#RRGGBB" / "#AARRGGBB"; "#00000000" = fully transparent. */
    public GuiWindow backgroundColor(String hex) { this.bgColor = parseColor(hex); return this; }
    public GuiWindow backgroundColor(int argb) { this.bgColor = argb; return this; }

    public GuiWindow borderColor(String hex) { this.borderColor = parseColor(hex); return this; }
    public GuiWindow borderColor(int argb) { this.borderColor = argb; return this; }

    /** Border thickness in pixels. 0 = no border. */
    public GuiWindow borderWidth(int width) { this.borderWidth = width; return this; }

    public GuiWindow titleBarColor(String hex) { this.titleBarColor = parseColor(hex); return this; }
    public GuiWindow titleBarColor(int argb) { this.titleBarColor = argb; return this; }

    public GuiWindow titleColor(String hex) { this.titleColor = parseColor(hex); return this; }
    public GuiWindow titleColor(int argb) { this.titleColor = argb; return this; }

    /** Hide the built-in title bar (drag + close button) if false. Default true. */
    public GuiWindow titleBar(boolean visible) { this.titleBar = visible; return this; }

    /** Height of the built-in title bar in pixels. Default 16. */
    public GuiWindow titleHeight(int height) { this.titleHeight = height; return this; }

    /** Dark overlay behind the window, 0-255 alpha. 0 = no dim (default). */
    public GuiWindow dim(int alpha) { this.dim = Math.max(0, Math.min(255, alpha)); return this; }

    /**
     * Keeps this window above every other window, even when the player clicks a window
     * underneath it. Use it for modal messages / confirmations. Default false.
     */
    public GuiWindow alwaysOnTop(boolean alwaysOnTop) { this.alwaysOnTop = alwaysOnTop; return this; }
    public boolean alwaysOnTop() { return alwaysOnTop; }

    /**
     * When false, pressing Esc (or clicking the title-bar close button) does not close the
     * window. The client sends an {@code "esc"} event instead, so the server can decide what
     * to do — e.g. show a confirmation dialog. Default true.
     */
    public GuiWindow closeOnEsc(boolean closeOnEsc) { this.closeOnEsc = closeOnEsc; return this; }
    public boolean closeOnEsc() { return closeOnEsc; }

    /** Handler for the {@code "esc"} event (only used when {@link #closeOnEsc} is false). */
    public GuiWindow onEscape(Consumer<GuiActionEvent> handler) { this.escHandler = handler; return this; }
    public Consumer<GuiActionEvent> escHandler() { return escHandler; }

    /** UI style: "default" (custom colors) or "minecraft" (vanilla-like). Default "default". */
    public GuiWindow style(String style) { this.style = style; return this; }
    public String style() { return style; }

    /**
     * Font id used for every piece of text in this window (title, labels, buttons, tooltips).
     * Empty keeps the client default. Minecraft renders fonts through resource packs, so the
     * id must exist on the client — GuiApi ships {@code guiapi:sysfont}, built from the
     * player's own Windows system font.
     */
    public GuiWindow font(String font) { this.font = font == null ? "" : font; return this; }
    public String font() { return font; }

    /**
     * 只对本窗口生效的点击音效，覆盖 {@link GuiApi#setClickSound} 的全局设置。
     * 传 {@code null} 表示本窗口静音（即使全局开启了音效）。
     */
    public GuiWindow clickSound(String soundId, float volume) {
        this.clickSoundSet = true;
        this.clickSound = soundId;
        this.clickSoundVolume = volume;
        return this;
    }

    public boolean clickSoundSet() { return clickSoundSet; }
    public String clickSound() { return clickSound; }
    public float clickSoundVolume() { return clickSoundVolume; }

    static int parseColor(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        long v = Long.parseLong(s, 16);
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    public Collection<Widget> widgets() { return widgets.values(); }

    public Widget widget(String id) { return widgets.get(id); }

    public GuiWindow add(Widget widget) {
        widgets.put(widget.id(), widget);
        return this;
    }

    public Label addLabel(String id, int x, int y, String text) {
        Label label = new Label(id, x, y, text);
        add(label);
        return label;
    }

    public ImageWidget addImage(String id, int x, int y, int width, int height, String imageId) {
        ImageWidget image = new ImageWidget(id, x, y, width, height, imageId);
        add(image);
        return image;
    }

    /** Adds a vanilla item display. The material can be a Bukkit Material name or vanilla id. */
    public ItemWidget addItem(String id, int x, int y, int width, int height, String material) {
        ItemWidget item = new ItemWidget(id, x, y, width, height, material);
        add(item);
        return item;
    }

    /** Adds a vanilla item display from a Bukkit Material. */
    public ItemWidget addItem(String id, int x, int y, int width, int height, org.bukkit.Material material) {
        ItemWidget item = new ItemWidget(id, x, y, width, height, material.name());
        add(item);
        return item;
    }

    public Panel addPanel(String id, int x, int y, int width, int height) {
        Panel panel = new Panel(id, x, y, width, height);
        add(panel);
        return panel;
    }

    public TextField addTextField(String id, int x, int y, int width, int height) {
        TextField field = new TextField(id, x, y, width, height);
        add(field);
        return field;
    }

    public Button addButton(String id, int x, int y, int width, int height, String text) {
        Button button = new Button(id, x, y, width, height, text);
        add(button);
        return button;
    }

    public Slider addSlider(String id, int x, int y, int width) {
        Slider slider = new Slider(id, x, y, width);
        add(slider);
        return slider;
    }

    /** 垂直滚动条，{@code value} 为 0–100 的百分比。 */
    public Scrollbar addScrollbar(String id, int x, int y, int width, int height) {
        Scrollbar scrollbar = new Scrollbar(id, x, y, width, height);
        add(scrollbar);
        return scrollbar;
    }

    public Checkbox addCheckbox(String id, int x, int y, String label, boolean checked) {
        Checkbox checkbox = new Checkbox(id, x, y, label, checked);
        add(checkbox);
        return checkbox;
    }

    public Radio addRadio(String id, int x, int y, String label, String group, boolean checked) {
        Radio radio = new Radio(id, x, y, label, group, checked);
        add(radio);
        return radio;
    }

    public Dropdown addDropdown(String id, int x, int y, int width, List<String> options) {
        Dropdown dropdown = new Dropdown(id, x, y, width, options);
        add(dropdown);
        return dropdown;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("title", title);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("w", width);
        json.addProperty("h", height);
        json.addProperty("draggable", draggable);
        json.addProperty("fullscreen", fullscreen);
        json.addProperty("bgColor", bgColor);
        json.addProperty("borderColor", borderColor);
        json.addProperty("borderWidth", borderWidth);
        json.addProperty("titleBarColor", titleBarColor);
        json.addProperty("titleColor", titleColor);
        json.addProperty("titleBar", titleBar);
        json.addProperty("titleHeight", titleHeight);
        json.addProperty("dim", dim);
        json.addProperty("alwaysOnTop", alwaysOnTop);
        json.addProperty("closeOnEsc", closeOnEsc);
        json.addProperty("style", style);
        if (!font.isEmpty()) json.addProperty("font", font);
        JsonArray arr = new JsonArray();
        for (Widget widget : widgets.values()) arr.add(widget.toJson());
        json.add("widgets", arr);
        return json;
    }
}
