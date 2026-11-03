package fr.smp.core;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Empêche de sortir les items des menus /boutique et gère les clics. */
public final class BoutiqueGuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof BoutiqueGui gui) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                gui.handleClick(event.getRawSlot());
            }
        } else if (top.getHolder() instanceof BoutiqueCategoryGui gui) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                gui.handleClick(event.getRawSlot());
            }
        } else if (top.getHolder() instanceof BoutiqueConfirmGui gui) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                gui.handleClick(event.getRawSlot());
            }
        } else if (top.getHolder() instanceof BoutiqueKeysGui gui) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                gui.handleClick(event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BoutiqueGui
                || event.getView().getTopInventory().getHolder() instanceof BoutiqueCategoryGui
                || event.getView().getTopInventory().getHolder() instanceof BoutiqueConfirmGui
                || event.getView().getTopInventory().getHolder() instanceof BoutiqueKeysGui) {
            event.setCancelled(true);
        }
    }
}
