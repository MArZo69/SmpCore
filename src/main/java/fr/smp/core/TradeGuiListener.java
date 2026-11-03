package fr.smp.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Gère les clics, le glisser-déposer et la fermeture du GUI /trade. */
public final class TradeGuiListener implements Listener {

    private final SmpCore plugin;

    public TradeGuiListener(SmpCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        TradeManager manager = plugin.trade();
        TradeManager.Session session = gui.session();
        if (!manager.isActive(session)) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        boolean top = raw < event.getView().getTopInventory().getSize();

        if (!top) {
            // Inventaire du joueur : on bloque seulement les transferts rapides vers le haut.
            if (event.isShiftClick()
                    || event.getClick() == ClickType.NUMBER_KEY
                    || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
            }
            return;
        }

        if (manager.isOwnOfferSlot(session, player.getUniqueId(), raw)) {
            if (event.isShiftClick()
                    || event.getClick() == ClickType.NUMBER_KEY
                    || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }
            manager.offerChanged(session);
            Bukkit.getScheduler().runTask(plugin, () -> manager.render(session));
            return;
        }

        event.setCancelled(true);
        manager.click(player, session, raw);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        TradeManager manager = plugin.trade();
        TradeManager.Session session = gui.session();
        if (!manager.isActive(session)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize && !manager.isOwnOfferSlot(session, player.getUniqueId(), raw)) {
                event.setCancelled(true);
                return;
            }
        }

        manager.offerChanged(session);
        Bukkit.getScheduler().runTask(plugin, () -> manager.render(session));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGui gui)) {
            return;
        }
        TradeManager manager = plugin.trade();
        TradeManager.Session session = gui.session();
        if (!manager.isActive(session)) {
            return;
        }
        Player closer = event.getPlayer() instanceof Player player ? player : null;
        if (closer != null && manager.isAwaitingInput(closer.getUniqueId())) {
            // Le joueur ferme volontairement le menu pour saisir un montant dans le chat.
            return;
        }
        manager.cancel(session, closer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.trade().onQuit(event.getPlayer());
    }
}
