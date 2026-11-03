package fr.smp.core;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Empêche de sortir les items des menus et gère les clics sur les boutons. */
public final class RankupGuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RankupGui gui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0) {
                gui.handleClick(slot);
            }
        } else if (event.getInventory().getHolder() instanceof RankupConfirmGui gui) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0) {
                gui.handleClick(slot);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RankupGui
                || event.getInventory().getHolder() instanceof RankupConfirmGui) {
            event.setCancelled(true);
        }
    }
}
