package fr.smp.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Catégorie Clés de /boutique : LureBox et SpawnerBox, par paquets de 1/3/5. */
public final class BoutiqueKeysGui implements InventoryHolder {

    static final int SLOT_BACK = 45;
    static final int SLOT_BALANCE = 49;
    static final int SLOT_CLOSE = 53;

    private static final int SIZE = 54;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private final Map<Integer, KeySlot> slots = new HashMap<>();
    private Inventory inventory;

    public BoutiqueKeysGui(SmpCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Messages messages = plugin.messages();
        slots.clear();

        inventory = Bukkit.createInventory(this, SIZE, messages.raw("boutique.title-keys"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        List<BoutiqueManager.KeyOffer> offers = plugin.boutique().keyOffers();
        long coins = plugin.coins().get(player.getUniqueId());

        if (offers.isEmpty()) {
            inventory.setItem(22, GuiItems.item(Material.CHEST,
                    messages.raw("boutique.key-empty"), List.of()));
        } else {
            for (int keyIndex = 0; keyIndex < offers.size(); keyIndex++) {
                BoutiqueManager.KeyOffer offer = offers.get(keyIndex);
                int base = 11 + keyIndex * 4;
                for (int packageIndex = 0; packageIndex < offer.packages().size(); packageIndex++) {
                    int slot = base + packageIndex * 9;
                    if (slot < 0 || slot >= SIZE
                            || slot == SLOT_BACK || slot == SLOT_BALANCE || slot == SLOT_CLOSE) {
                        continue;
                    }
                    BoutiqueManager.KeyPackage pack = offer.packages().get(packageIndex);
                    inventory.setItem(slot, keyItem(messages, offer, pack, coins));
                    slots.put(slot, new KeySlot(offer, pack));
                }
            }
        }

        inventory.setItem(SLOT_BACK, GuiItems.item(Material.ARROW,
                messages.raw("boutique.back"),
                List.of(messages.raw("boutique.back-lore"))));
        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.SUNFLOWER,
                messages.raw("boutique.balance",
                        "amount", plugin.coins().format(coins),
                        "currency", plugin.coins().currencyName()),
                messages.rawList("boutique.balance-lore")));
        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("boutique.close"), List.of()));

        player.openInventory(inventory);
    }

    private ItemStack keyItem(Messages messages, BoutiqueManager.KeyOffer offer,
                              BoutiqueManager.KeyPackage pack, long coins) {
        List<Component> lore = new ArrayList<>();
        lore.add(messages.raw("boutique.key-price",
                "price", plugin.coins().format(pack.price()),
                "currency", plugin.coins().currencyName()));
        if (pack.discount() > 0) {
            lore.add(messages.raw("boutique.key-promo",
                    "percent", Integer.toString(pack.discount())));
        }
        if (coins >= pack.price()) {
            lore.add(messages.raw("boutique.key-buy"));
        } else {
            lore.add(messages.raw("boutique.key-missing",
                    "missing", plugin.coins().format(pack.price() - coins),
                    "currency", plugin.coins().currencyName()));
        }

        return GuiItems.item(offer.material(),
                messages.raw("boutique.key-name",
                        "key", offer.display(),
                        "qty", Integer.toString(pack.quantity())),
                lore,
                offer.glow());
    }

    void handleClick(int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            new BoutiqueGui(plugin, player).open();
            return;
        }

        KeySlot keySlot = slots.get(slot);
        if (keySlot != null) {
            plugin.boutique().buyKey(player, keySlot.offer(), keySlot.pack(), () -> {
                if (player.isOnline()) {
                    new BoutiqueKeysGui(plugin, player).open();
                }
            });
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private record KeySlot(BoutiqueManager.KeyOffer offer, BoutiqueManager.KeyPackage pack) {
    }
}
