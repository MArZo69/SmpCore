package fr.smp.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menu principal de /boutique : Grades, Clés, Boosts. */
public final class BoutiqueGui implements InventoryHolder {

    static final int SLOT_GRADES = 11;
    static final int SLOT_KEYS = 13;
    static final int SLOT_BOOSTS = 15;
    static final int SLOT_BALANCE = 22;
    static final int SLOT_CLOSE = 26;

    private static final int SIZE = 27;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private Inventory inventory;

    public BoutiqueGui(SmpCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Messages messages = plugin.messages();
        inventory = Bukkit.createInventory(this, SIZE, messages.raw("boutique.title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        inventory.setItem(SLOT_GRADES, GuiItems.item(Material.DIAMOND,
                messages.raw("boutique.grades"),
                messages.rawList("boutique.grades-lore")));

        inventory.setItem(SLOT_KEYS, GuiItems.item(Material.TRIPWIRE_HOOK,
                messages.raw("boutique.keys"),
                messages.rawList("boutique.keys-lore")));

        inventory.setItem(SLOT_BOOSTS, GuiItems.item(Material.BLAZE_POWDER,
                messages.raw("boutique.boosts"),
                messages.rawList("boutique.boosts-lore")));

        inventory.setItem(SLOT_BALANCE, balanceItem(messages));
        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("boutique.close"), List.of()));

        player.openInventory(inventory);
    }

    ItemStack balanceItem(Messages messages) {
        return GuiItems.item(Material.SUNFLOWER,
                messages.raw("boutique.balance",
                        "amount", plugin.coins().format(plugin.coins().get(player.getUniqueId())),
                        "currency", plugin.coins().currencyName()),
                messages.rawList("boutique.balance-lore"));
    }

    void handleClick(int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_GRADES) {
            new BoutiqueCategoryGui(plugin, player, BoutiqueCategoryGui.Category.GRADES).open();
        } else if (slot == SLOT_KEYS) {
            new BoutiqueKeysGui(plugin, player).open();
        } else if (slot == SLOT_BOOSTS) {
            new BoutiqueCategoryGui(plugin, player, BoutiqueCategoryGui.Category.BOOSTS).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
