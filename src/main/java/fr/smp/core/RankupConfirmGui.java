package fr.smp.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Écran de paiement : choix de monnaie de part et d'autre du grade. */
public final class RankupConfirmGui implements InventoryHolder {

    static final int SLOT_BUY_COINS = 11;
    static final int SLOT_TARGET = 13;
    static final int SLOT_BUY_MONEY = 15;
    static final int SLOT_BALANCE = 22;
    static final int SLOT_CLOSE = 26;

    private static final int SIZE = 27;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private final RankupManager.Step target;
    private Inventory inventory;
    private RankupManager.ConfirmState state;

    public RankupConfirmGui(SmpCore plugin, Player player, RankupManager.Step target) {
        this.plugin = plugin;
        this.player = player;
        this.target = target;
    }

    public void open() {
        state = plugin.rankup().confirmState(player, target);
        if (state == null) {
            return;
        }

        Messages messages = plugin.messages();
        inventory = Bukkit.createInventory(this, SIZE,
                messages.raw("rankup-gui.confirm-title", "target", state.target().display()));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        inventory.setItem(SLOT_TARGET, targetItem(messages, state));

        if (state.pendingTarget()) {
            inventory.setItem(SLOT_BUY_COINS, GuiItems.item(Material.ARROW,
                messages.raw("rankup-gui.cancel"),
                List.of(messages.raw("rankup-gui.cancel-lore"))));
            inventory.setItem(SLOT_BUY_MONEY, confirmItem(messages, state));
        } else {
            inventory.setItem(SLOT_BUY_COINS, buyCoinsItem(messages, state));
            inventory.setItem(SLOT_BUY_MONEY, buyMoneyItem(messages, state));
        }

        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.EMERALD,
                messages.raw("rankup-gui.balance",
                        "balance", Money.format(state.balance()), "symbol", state.symbol()),
                List.of()));

        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("rankup-gui.close"), List.of()));

        player.openInventory(inventory);
    }

    private ItemStack targetItem(Messages messages, RankupManager.ConfirmState state) {
        Material material;
        List<Component> lore = new ArrayList<>();

        if (state.owned()) {
            material = Material.LIME_DYE;
            lore.add(messages.raw("rankup-gui.already-owned"));
            if (state.pendingTarget()) {
                lore.add(messages.raw("rankup-gui.pending-owned"));
            }
        } else if (state.locked()) {
            material = Material.GRAY_DYE;
            if (state.pendingTarget()) {
                lore.add(messages.raw("rankup-gui.pending-wait",
                        "previous", state.previous().display()));
            } else {
                lore.add(messages.raw("rankup-gui.price",
                        "price", Money.format(state.target().price()), "symbol", state.symbol()));
                lore.add(messages.raw("rankup-gui.locked"));
                lore.add(messages.raw("rankup-gui.locked-lore",
                        "previous", state.previous().display()));
            }
        } else {
            material = Material.DIAMOND;
            if (state.pendingTarget()) {
                lore.add(messages.raw("rankup-gui.pending-ready"));
            } else {
                lore.add(messages.raw("rankup-gui.price",
                        "price", Money.format(state.target().price()), "symbol", state.symbol()));
                if (!state.canAfford()) {
                    lore.add(messages.raw("rankup-gui.missing-lore",
                            "missing", Money.format(state.target().price() - state.balance()),
                            "symbol", state.symbol()));
                }
            }
        }

        if (!state.target().advantages().isEmpty()) {
            lore.add(messages.raw("rankup-gui.advantages"));
            for (String advantage : state.target().advantages()) {
                lore.add(messages.component(advantage));
            }
        }

        return GuiItems.item(material,
                messages.raw("rankup-gui.grade-name", "grade", state.target().display()), lore);
    }

    private ItemStack confirmItem(Messages messages, RankupManager.ConfirmState state) {
        if (state.owned()) {
            List<Component> lore = state.pendingTarget()
                    ? List.of(messages.raw("rankup-gui.pending-owned"))
                    : List.of();
            return GuiItems.item(Material.GRAY_CONCRETE,
                    messages.raw("rankup-gui.already-owned"), lore);
        }
        if (state.locked()) {
            if (state.pendingTarget()) {
                return GuiItems.item(Material.GRAY_CONCRETE,
                        messages.raw("rankup-gui.pending-wait-title"),
                        List.of(messages.raw("rankup-gui.pending-wait-lore",
                                "previous", state.previous().display())));
            }
            return GuiItems.item(Material.GRAY_CONCRETE,
                    messages.raw("rankup-gui.locked"),
                    List.of(messages.raw("rankup-gui.locked-lore",
                            "previous", state.previous().display())));
        }

        if (state.pendingTarget()) {
            return GuiItems.item(Material.LIME_CONCRETE,
                    messages.raw("rankup-gui.claim"),
                    List.of(messages.raw("rankup-gui.claim-lore")));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(messages.raw("rankup-gui.validate-lore",
                "target", state.target().display(),
                "price", Money.format(state.target().price()),
                "symbol", state.symbol()));
        if (!state.canAfford()) {
            lore.add(messages.raw("rankup-gui.missing-lore",
                    "missing", Money.format(state.target().price() - state.balance()),
                    "symbol", state.symbol()));
        }

        return GuiItems.item(Material.LIME_CONCRETE,
                messages.raw("rankup-gui.validate"), lore);
    }

    private ItemStack buyCoinsItem(Messages messages, RankupManager.ConfirmState state) {
        List<Component> lore = new ArrayList<>();
        long price = plugin.boutique().coinPrice(state.target());
        long balance = plugin.coins().get(player.getUniqueId());
        if (state.owned()) {
            lore.add(messages.raw("rankup-gui.already-owned"));
        } else if (state.locked()) {
            lore.add(messages.raw("rankup-gui.locked-lore",
                    "previous", state.previous().display()));
        } else if (price <= 0) {
            lore.add(messages.raw("boutique.grade-unavailable"));
        } else {
            lore.add(messages.raw("rankup-gui.price-coins",
                    "price", plugin.coins().format(price),
                    "currency", plugin.coins().currencyName()));
            lore.add(messages.raw("rankup-gui.balance-coins",
                    "balance", plugin.coins().format(balance),
                    "currency", plugin.coins().currencyName()));
        }
        return GuiItems.item(Material.AMETHYST_SHARD,
                messages.raw("rankup-gui.buy-coins"), lore);
    }

    private ItemStack buyMoneyItem(Messages messages, RankupManager.ConfirmState state) {
        if (state.pendingTarget()) {
            return confirmItem(messages, state);
        }
        List<Component> lore = new ArrayList<>();
        if (state.owned()) {
            lore.add(messages.raw("rankup-gui.already-owned"));
        } else if (state.locked()) {
            lore.add(messages.raw("rankup-gui.locked-lore",
                    "previous", state.previous().display()));
        } else {
            lore.add(messages.raw("rankup-gui.price-money",
                    "price", Money.format(state.target().price()), "symbol", state.symbol()));
            lore.add(messages.raw("rankup-gui.balance-money",
                    "balance", Money.format(state.balance()), "symbol", state.symbol()));
        }
        return GuiItems.item(Material.SUNFLOWER,
                messages.raw("rankup-gui.buy-money"), lore);
    }

    void handleClick(int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (state == null || (slot != SLOT_BUY_COINS && slot != SLOT_BUY_MONEY)) {
            return;
        }

        // On relit le solde et le grade au moment du clic (ils ont pu changer depuis l'ouverture).
        state = plugin.rankup().confirmState(player, target);
        if (state == null) {
            player.closeInventory();
            return;
        }
        if (!state.next()) {
            new RankupConfirmGui(plugin, player, target).open();
            return;
        }

        if (state.pendingTarget()) {
            if (slot == SLOT_BUY_COINS) {
                new RankupGui(plugin, player).open();
                return;
            }
            player.closeInventory();
            plugin.rankup().claim(player, state.target(), () -> {
                if (player.isOnline()) {
                    new RankupGui(plugin, player).open();
                }
            });
            return;
        }

        if (slot == SLOT_BUY_COINS) {
            BoutiqueManager.GradeState coinState = plugin.boutique().gradeState(player, state.target());
            if (coinState == null) {
                player.closeInventory();
                return;
            }
            if (!coinState.next()) {
                new RankupConfirmGui(plugin, player, target).open();
                return;
            }
            if (!coinState.available()) {
                plugin.messages().send(player, "boutique.grade-unavailable");
                return;
            }
            if (!coinState.canAfford()) {
                plugin.messages().send(player, "coins.not-enough",
                        "missing", plugin.coins().format(coinState.missing()),
                        "currency", plugin.coins().currencyName());
                return;
            }
            player.closeInventory();
            plugin.boutique().buyGrade(player, coinState.target(), () -> {
                if (player.isOnline()) {
                    new RankupGui(plugin, player).open();
                }
            });
            return;
        }

        if (!state.canAfford()) {
            double missing = state.target().price() - state.balance();
            plugin.messages().send(player, "rankup-not-enough",
                    "next", state.target().display(),
                    "target", state.target().display(),
                    "missing", Money.format(missing), "symbol", state.symbol());
            return;
        }

        player.closeInventory();
        plugin.rankup().confirm(player, state.target(), () -> {
            if (player.isOnline()) {
                new RankupGui(plugin, player).open();
            }
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
