package com.guiapi.paper.widget;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Displays a vanilla Minecraft item inside the window.
 * The client renders the item icon, amount, and tooltip.
 */
public class ItemWidget extends Widget {
    private String material;
    private int amount = 1;
    private String displayName;
    private List<String> lore;
    private List<String> enchants;
    private boolean enchanted;
    private Consumer<GuiActionEvent> clickHandler;

    /**
     * @param material Bukkit Material name (e.g. "DIAMOND_SWORD") or vanilla item id
     *                 (e.g. "minecraft:diamond_sword"). Case-insensitive.
     */
    public ItemWidget(String id, int x, int y, int width, int height, String material) {
        super(id, x, y, width, height);
        this.material = material;
    }

    public String material() { return material; }
    public int amount() { return amount; }
    public String displayName() { return displayName; }
    public List<String> lore() { return lore; }
    public List<String> enchants() { return enchants; }
    public boolean enchanted() { return enchanted; }
    public Consumer<GuiActionEvent> clickHandler() { return clickHandler; }

    /** Set the item by Bukkit Material enum. */
    public ItemWidget material(org.bukkit.Material material) {
        this.material = material.name();
        return this;
    }

    /** Set the item by vanilla id string, e.g. "minecraft:diamond_sword". */
    public ItemWidget material(String material) {
        this.material = material;
        return this;
    }

    /** Stack size shown on the icon (1-64). Values outside the range are clamped. */
    public ItemWidget amount(int amount) {
        this.amount = Math.max(1, Math.min(64, amount));
        return this;
    }

    /** Custom display name shown in the tooltip. Supports section sign (§) color codes. */
    public ItemWidget displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /** Tooltip lore lines. Supports section sign (§) color codes. */
    public ItemWidget lore(List<String> lore) {
        this.lore = lore != null ? new ArrayList<>(lore) : null;
        return this;
    }

    /**
     * Enchantments shown in the tooltip, each entry as {@code "<translation key>:<level>"},
     * e.g. {@code "enchantment.minecraft.sharpness:5"}. The client translates the key into
     * the player's own language, so no server-side localisation table is needed.
     */
    public ItemWidget enchants(List<String> enchants) {
        this.enchants = enchants != null ? new ArrayList<>(enchants) : null;
        return this;
    }

    /** Whether to show the enchanted glint overlay. */
    public ItemWidget enchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    /** Binds a click handler. The event value is empty. */
    public ItemWidget onClick(Consumer<GuiActionEvent> handler) {
        this.clickHandler = handler;
        return this;
    }

    @Override protected String type() { return "item"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("material", material);
        json.addProperty("amount", amount);
        if (displayName != null) json.addProperty("displayName", displayName);
        if (lore != null && !lore.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String line : lore) arr.add(line);
            json.add("lore", arr);
        }
        if (enchants != null && !enchants.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String line : enchants) arr.add(line);
            json.add("enchants", arr);
        }
        if (enchanted) json.addProperty("enchanted", true);
    }
}
