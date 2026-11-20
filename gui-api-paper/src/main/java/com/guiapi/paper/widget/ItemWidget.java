package com.guiapi.paper.widget;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Displays a vanilla Minecraft item inside the window.
 * The client renders the item icon, amount, and tooltip.
 */
public class ItemWidget extends Widget {
    private String material;
    private int amount = 1;
    private String displayName;
    private String nameKey;
    private List<String> lore;
    private List<String> enchants;
    private boolean enchanted;
    private Integer customModelData;
    private String itemModel;
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

    public Integer customModelData() { return customModelData; }
    public String itemModel() { return itemModel; }
    public String nameKey() { return nameKey; }

    /**
     * Custom model data, used by resource packs that give a vanilla item a different look.
     * {@code null} resets it. Clients older than the version that reads this field simply
     * fall back to the plain material.
     */
    public ItemWidget customModelData(Integer customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    /**
     * Item model component id, e.g. {@code "farmersdelight:item/tomato"}. This is the modern
     * (1.21.4+) way of restyling a vanilla item and takes precedence over custom model data.
     */
    public ItemWidget itemModel(String itemModel) {
        this.itemModel = itemModel == null || itemModel.isEmpty() ? null : itemModel;
        return this;
    }

    /**
     * Translation key for the item name, e.g. {@code "item.farmersdelight.tomato"}. When set,
     * the client resolves it against its own language (including server resource packs), so
     * custom items show the name the player is used to instead of the base material name.
     */
    public ItemWidget nameKey(String nameKey) {
        this.nameKey = nameKey == null || nameKey.isEmpty() ? null : nameKey;
        return this;
    }

    /**
     * Copies the full look of a real {@link ItemStack}: material, amount, name, lore,
     * enchantments, glint, custom model data and item model. Use it whenever the widget should
     * mirror an item the player actually owns, so resource-pack restyled items keep their look.
     */
    public ItemWidget from(ItemStack stack) {
        if (stack == null) return this;
        material(stack.getType());
        amount(stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        Component name = null;
        if (meta.hasItemName()) name = meta.itemName();
        else if (meta.hasDisplayName()) name = meta.displayName();
        if (name instanceof TranslatableComponent translatable
                && translatable.args().isEmpty() && translatable.children().isEmpty()) {
            nameKey(translatable.key());
            displayName(null);
        } else if (name != null) {
            nameKey(null);
            displayName(PlainTextComponentSerializer.plainText().serialize(name));
        }

        if (meta.hasLore()) {
            List<String> lines = new ArrayList<>();
            for (Component line : meta.lore()) {
                lines.add(PlainTextComponentSerializer.plainText().serialize(line));
            }
            lore(lines);
        }

        List<String> enchantLines = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            enchantLines.add(entry.getKey().translationKey() + ":" + entry.getValue());
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Map.Entry<Enchantment, Integer> entry : storage.getStoredEnchants().entrySet()) {
                enchantLines.add(entry.getKey().translationKey() + ":" + entry.getValue());
            }
        }
        if (!enchantLines.isEmpty()) {
            enchanted(true);
            enchants(enchantLines);
        }

        customModelData(meta.hasCustomModelData() ? meta.getCustomModelData() : null);
        itemModel(meta.hasItemModel() && meta.getItemModel() != null
                ? meta.getItemModel().toString() : null);
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
        if (nameKey != null) json.addProperty("nameKey", nameKey);
        if (customModelData != null) json.addProperty("customModelData", customModelData);
        if (itemModel != null) json.addProperty("itemModel", itemModel);
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
