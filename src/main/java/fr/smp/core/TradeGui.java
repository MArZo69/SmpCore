package fr.smp.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI partagé par les deux joueurs d'un échange. Les objets d'offre restent dans leurs slots. */
public final class TradeGui implements InventoryHolder {

    private final SmpCore plugin;
    private final TradeManager.Session session;
    private Inventory inventory;

    public TradeGui(SmpCore plugin, TradeManager.Session session) {
        this.plugin = plugin;
        this.session = session;
    }

    public TradeManager.Session session() {
        return session;
    }

    public Inventory create() {
        inventory = Bukkit.createInventory(this, TradeManager.SIZE,
                plugin.messages().raw("trade.gui-title"));
        render();
        return inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Met à jour les boutons et les informations sans toucher aux objets proposés. */
    public void render() {
        if (inventory == null) {
            return;
        }
        Messages m = plugin.messages();
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        Player onlineA = Bukkit.getPlayer(session.a);
        Player onlineB = Bukkit.getPlayer(session.b);

        ItemStack filler = GuiItems.item(Material.PURPLE_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < TradeManager.SIZE; slot++) {
            if (!isOfferSlot(slot)) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(TradeManager.SLOT_HEAD_A,
                head(session.a, session.nameA, session.moneyA, session.xpA));
        inventory.setItem(TradeManager.SLOT_HEAD_B,
                head(session.b, session.nameB, session.moneyB, session.xpB));

        inventory.setItem(TradeManager.SLOT_STATUS, GuiItems.item(Material.CLOCK,
                status(),
                List.of(m.raw("trade.gui-status-lore"))));

        inventory.setItem(TradeManager.SLOT_MONEY_A, GuiItems.item(Material.GOLD_INGOT,
                m.raw("trade.gui-money", "amount", Money.format(session.moneyA), "symbol", symbol),
                moneyLore(onlineA)));
        inventory.setItem(TradeManager.SLOT_MONEY_B, GuiItems.item(Material.GOLD_INGOT,
                m.raw("trade.gui-money", "amount", Money.format(session.moneyB), "symbol", symbol),
                moneyLore(onlineB)));

        inventory.setItem(TradeManager.SLOT_XP_A, GuiItems.item(Material.EXPERIENCE_BOTTLE,
                m.raw("trade.gui-xp", "levels", Integer.toString(session.xpA)),
                xpLore(onlineA)));
        inventory.setItem(TradeManager.SLOT_XP_B, GuiItems.item(Material.EXPERIENCE_BOTTLE,
                m.raw("trade.gui-xp", "levels", Integer.toString(session.xpB)),
                xpLore(onlineB)));

        inventory.setItem(TradeManager.SLOT_CONFIRM_A, confirmItem(session.confirmA));
        inventory.setItem(TradeManager.SLOT_CONFIRM_B, confirmItem(session.confirmB));

        inventory.setItem(TradeManager.SLOT_CONFIRM_STATUS_A, statusItem(session.nameA, session.confirmA));
        inventory.setItem(TradeManager.SLOT_CONFIRM_STATUS_B, statusItem(session.nameB, session.confirmB));

        inventory.setItem(TradeManager.SLOT_CANCEL, GuiItems.item(Material.BARRIER,
                m.raw("trade.gui-cancel"),
                List.of(m.raw("trade.gui-cancel-lore"))));
    }

    private Component status() {
        Messages m = plugin.messages();
        if (session.confirmA && session.confirmB) {
            return m.raw("trade.gui-status-ready");
        }
        if (session.confirmA) {
            return m.raw("trade.gui-status-one", "player", session.nameA);
        }
        if (session.confirmB) {
            return m.raw("trade.gui-status-one", "player", session.nameB);
        }
        return m.raw("trade.gui-status-waiting");
    }

    private ItemStack confirmItem(boolean confirmed) {
        Messages m = plugin.messages();
        Material material = confirmed ? Material.LIME_DYE : Material.GRAY_DYE;
        return GuiItems.item(material,
                m.raw(confirmed ? "trade.gui-confirmed" : "trade.gui-confirm"),
                List.of(m.raw(confirmed ? "trade.gui-confirm-lore-done" : "trade.gui-confirm-lore")));
    }

    private ItemStack statusItem(String name, boolean confirmed) {
        Messages m = plugin.messages();
        Material material = confirmed ? Material.LIME_DYE : Material.GRAY_DYE;
        return GuiItems.item(material,
                m.raw(confirmed ? "trade.gui-status-confirmed" : "trade.gui-status-pending",
                        "player", name),
                List.of(m.raw(confirmed
                        ? "trade.gui-status-item-lore-confirmed"
                        : "trade.gui-status-item-lore-pending")));
    }

    private List<Component> moneyLore(Player player) {
        Messages m = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.add(m.raw("trade.gui-money-lore"));
        if (player != null && plugin.trade().economyAvailable()) {
            lore.add(m.raw("trade.gui-money-balance",
                    "player", player.getName(),
                    "balance", Money.format(plugin.trade().balance(player)),
                    "symbol", plugin.getConfig().getString("currency-symbol", "$")));
        }
        return lore;
    }

    private List<Component> xpLore(Player player) {
        Messages m = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.add(m.raw("trade.gui-xp-lore"));
        if (player != null) {
            lore.add(m.raw("trade.gui-xp-balance",
                    "player", player.getName(),
                    "levels", Integer.toString(player.getLevel())));
        }
        return lore;
    }

    private ItemStack head(java.util.UUID uuid, String name, double money, int xp) {
        Messages m = plugin.messages();
        List<Component> lore = new ArrayList<>();
        int count = countItems(uuid.equals(session.a) ? TradeManager.SLOTS_A : TradeManager.SLOTS_B);
        lore.add(m.raw("trade.gui-head-offer", "count", Integer.toString(count)));
        lore.add(m.raw("trade.gui-head-money", "amount", Money.format(money),
                "symbol", plugin.getConfig().getString("currency-symbol", "$")));
        lore.add(m.raw("trade.gui-head-xp", "levels", Integer.toString(xp)));
        lore.add(m.raw("trade.gui-offer-hint"));

        ItemStack item = GuiItems.item(Material.PLAYER_HEAD,
                m.raw("trade.gui-head", "player", name), lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }
        return item;
    }

    private int countItems(int[] slots) {
        int total = 0;
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean isOfferSlot(int slot) {
        return in(slot, TradeManager.SLOTS_A) || in(slot, TradeManager.SLOTS_B);
    }

    private boolean in(int slot, int[] slots) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }
}
