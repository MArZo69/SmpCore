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

/** Confirmation d'achat d'un grade avec des LureCoins. */
public final class BoutiqueConfirmGui implements InventoryHolder {

    static final int SLOT_CANCEL = 11;
    static final int SLOT_TARGET = 13;
    static final int SLOT_CONFIRM = 15;
    static final int SLOT_BALANCE = 22;
    static final int SLOT_CLOSE = 26;

    private static final int SIZE = 27;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private final RankupManager.Step target;
    private Inventory inventory;
    private BoutiqueManager.GradeState state;

    public BoutiqueConfirmGui(SmpCore plugin, Player player, RankupManager.Step target) {
        this.plugin = plugin;
        this.player = player;
        this.target = target;
    }

    public void open() {
        state = plugin.boutique().gradeState(player, target);
        if (state == null) {
            Messages messages = plugin.messages();
            if (plugin.rankup().steps().size() < 2) {
                messages.send(player, "config-error");
            } else {
                messages.send(player, "no-luckperms");
            }
            return;
        }

        Messages messages = plugin.messages();
        inventory = Bukkit.createInventory(this, SIZE, messages.raw("boutique.confirm-title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        inventory.setItem(SLOT_TARGET, targetItem(messages, state));
        inventory.setItem(SLOT_CANCEL, GuiItems.item(Material.RED_CONCRETE,
                messages.raw("boutique.confirm-cancel"),
                List.of(messages.raw("boutique.confirm-cancel-lore"))));
        inventory.setItem(SLOT_CONFIRM, confirmItem(messages, state));
        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.SUNFLOWER,
                messages.raw("boutique.balance",
                        "amount", plugin.coins().format(state.coins()),
                        "currency", plugin.coins().currencyName()),
                messages.rawList("boutique.balance-lore")));
        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("boutique.close"), List.of()));

        player.openInventory(inventory);
    }

    private ItemStack targetItem(Messages messages, BoutiqueManager.GradeState state) {
        Material material;
        List<Component> lore = new ArrayList<>();

        if (state.owned()) {
            material = Material.LIME_DYE;
            lore.add(messages.raw("rankup-gui.already-owned"));
        } else if (state.locked()) {
            material = Material.GRAY_DYE;
            addPriceLore(messages, state, lore);
            lore.add(messages.raw("boutique.grade-locked"));
            lore.add(messages.raw("boutique.grade-locked-lore",
                    "previous", state.previous().display()));
        } else {
            material = Material.DIAMOND;
            addPriceLore(messages, state, lore);
            if (state.available() && !state.canAfford()) {
                lore.add(messages.raw("boutique.grade-missing",
                        "missing", plugin.coins().format(state.missing()),
                        "currency", plugin.coins().currencyName()));
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

    private void addPriceLore(Messages messages, BoutiqueManager.GradeState state, List<Component> lore) {
        if (!state.available()) {
            lore.add(messages.raw("boutique.grade-unavailable"));
            return;
        }
        lore.add(messages.raw("boutique.grade-price",
                "price", plugin.coins().format(state.price()),
                "currency", plugin.coins().currencyName()));
    }

    private ItemStack confirmItem(Messages messages, BoutiqueManager.GradeState state) {
        if (state.owned()) {
            return GuiItems.item(Material.GRAY_CONCRETE,
                    messages.raw("rankup-gui.already-owned"), List.of());
        }
        if (state.locked()) {
            return GuiItems.item(Material.GRAY_CONCRETE,
                    messages.raw("boutique.grade-locked"),
                    List.of(messages.raw("boutique.grade-locked-lore",
                            "previous", state.previous().display())));
        }
        if (!state.available()) {
            return GuiItems.item(Material.GRAY_CONCRETE,
                    messages.raw("boutique.grade-unavailable"), List.of());
        }

        List<Component> lore = new ArrayList<>();
        lore.add(messages.raw("boutique.confirm-target",
                "target", state.target().display(),
                "price", plugin.coins().format(state.price()),
                "currency", plugin.coins().currencyName()));
        if (!state.canAfford()) {
            lore.add(messages.raw("boutique.confirm-missing",
                    "missing", plugin.coins().format(state.missing()),
                    "currency", plugin.coins().currencyName()));
        }

        return GuiItems.item(Material.LIME_CONCRETE,
                messages.raw("boutique.confirm-validate"), lore);
    }

    void handleClick(int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_CANCEL) {
            new BoutiqueCategoryGui(plugin, player, BoutiqueCategoryGui.Category.GRADES).open();
            return;
        }
        if (slot != SLOT_CONFIRM) {
            return;
        }

        // On relit le solde et le grade au moment du clic.
        state = plugin.boutique().gradeState(player, target);
        if (state == null) {
            player.closeInventory();
            return;
        }
        if (!state.next()) {
            new BoutiqueConfirmGui(plugin, player, target).open();
            return;
        }
        if (!state.available()) {
            plugin.messages().send(player, "boutique.grade-unavailable");
            return;
        }
        if (!state.canAfford()) {
            plugin.messages().send(player, "coins.not-enough",
                    "target", state.target().display(),
                    "missing", plugin.coins().format(state.missing()),
                    "currency", plugin.coins().currencyName());
            return;
        }

        player.closeInventory();
        plugin.boutique().buyGrade(player, state.target(), () -> {
            if (player.isOnline()) {
                new BoutiqueCategoryGui(plugin, player, BoutiqueCategoryGui.Category.GRADES).open();
            }
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
