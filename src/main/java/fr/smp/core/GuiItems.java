package fr.smp.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Petits utilitaires partagés par les menus. */
final class GuiItems {

    private GuiItems() {
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        return item(material, name, lore, false);
    }

    /** @param glint ajoute l'effet enchanté (surbrillance) sans enchantement réel. */
    static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack filler(Material material) {
        return item(material, Component.text(" "), List.of());
    }
}
