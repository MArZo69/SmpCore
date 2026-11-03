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

/** Menu principal de /rankup : tous les grades, du plus petit au plus grand. */
public final class RankupGui implements InventoryHolder {

    static final int SLOT_COINS_BALANCE = 21;
    static final int SLOT_BALANCE = 23;
    static final int SLOT_CLOSE = 26;

    private static final int SIZE = 27;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private final Map<Integer, RankupManager.Step> slots = new HashMap<>();
    private Inventory inventory;

    public RankupGui(SmpCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Ouvre le menu des grades. */
    public void open() {
        RankupManager.MenuState state = plugin.rankup().menuState(player);
        if (state == null) {
            return;
        }

        Messages messages = plugin.messages();
        slots.clear();
        inventory = Bukkit.createInventory(this, SIZE, messages.raw("rankup-gui.title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        List<RankupManager.Step> steps = state.steps();
        List<Integer> layout = layoutSlots(steps.size());
        for (int i = 0; i < layout.size() && i < steps.size(); i++) {
            int slot = layout.get(i);
            RankupManager.Step step = steps.get(i);
            inventory.setItem(slot, gradeItem(messages, state, i, step));
            slots.put(slot, step);
        }

        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.EMERALD,
                messages.raw("rankup-gui.balance",
                        "balance", Money.format(state.balance()), "symbol", state.symbol()),
                List.of()));

        inventory.setItem(SLOT_COINS_BALANCE, GuiItems.item(Material.AMETHYST_SHARD,
            messages.raw("rankup-gui.balance-coins",
                "balance", plugin.coins().format(plugin.coins().get(player.getUniqueId())),
                "currency", plugin.coins().currencyName()),
            List.of()));

        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("rankup-gui.close"), List.of()));

        player.openInventory(inventory);
    }

    /** Appelé par le listener quand le joueur clique dans le menu. */
    void handleClick(int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        RankupManager.Step step = slots.get(slot);
        if (step != null) {
            new RankupConfirmGui(plugin, player, step).open();
        }
    }

    private ItemStack gradeItem(Messages messages, RankupManager.MenuState state,
                                int index, RankupManager.Step step) {
        Material material;
        List<Component> lore = new ArrayList<>();

        if (index == state.currentIndex()) {
            material = Material.NETHER_STAR;
            lore.add(messages.raw("rankup-gui.current-grade"));
            if (state.max()) {
                lore.add(messages.raw("rankup-gui.max"));
            }
        } else if (index < state.currentIndex()) {
            material = Material.LIME_DYE;
            lore.add(messages.raw("rankup-gui.owned"));
        } else {
            material = index == state.currentIndex() + 1 ? Material.DIAMOND : Material.GRAY_DYE;
            if (!state.pending(step)) {
                lore.add(messages.raw("rankup-gui.price",
                        "price", Money.format(step.price()), "symbol", state.symbol()));
            }

            if (index == state.currentIndex() + 1) {
                if (!state.pending(step)) {
                    if (state.balance() < step.price()) {
                        lore.add(messages.raw("rankup-gui.missing-lore",
                                "missing", Money.format(step.price() - state.balance()),
                                "symbol", state.symbol()));
                    } else {
                        lore.add(messages.raw("rankup-gui.click"));
                    }
                }
            } else {
                lore.add(messages.raw("rankup-gui.locked"));
                lore.add(messages.raw("rankup-gui.locked-lore",
                        "previous", state.steps().get(index - 1).display()));
            }
        }

        if (state.pending(step)) {
            if (index == state.currentIndex() + 1) {
                lore.add(messages.raw("rankup-gui.pending-ready"));
            } else if (index > state.currentIndex() + 1) {
                lore.add(messages.raw("rankup-gui.pending-wait",
                        "previous", state.steps().get(index - 1).display()));
            } else {
                lore.add(messages.raw("rankup-gui.pending-owned"));
            }
        }

        long coinPrice = plugin.boutique().coinPrice(step);
        if (coinPrice > 0) {
            lore.add(messages.raw("rankup-gui.price-coins",
                    "price", plugin.coins().format(coinPrice),
                    "currency", plugin.coins().currencyName()));
        }

        if (!step.advantages().isEmpty()) {
            lore.add(messages.raw("rankup-gui.advantages"));
            for (String advantage : step.advantages()) {
                lore.add(messages.component(advantage));
            }
        }

        return GuiItems.item(material,
                messages.raw("rankup-gui.grade-name", "grade", step.display()), lore);
    }

    /**
     * 5 grades -> une seule rangée centrée au milieu du coffre.
     * Plus de 9 grades -> on remplit aussi les autres rangées, en gardant le solde et la barrière.
     */
    private static List<Integer> layoutSlots(int count) {
        List<Integer> result = new ArrayList<>();
        if (count <= 9) {
            int start = 9 + (9 - count) / 2;
            for (int i = 0; i < count; i++) {
                result.add(start + i);
            }
            return result;
        }
        for (int slot = 0; slot < SIZE && result.size() < count; slot++) {
            if (slot == SLOT_COINS_BALANCE || slot == SLOT_BALANCE || slot == SLOT_CLOSE) {
                continue;
            }
            result.add(slot);
        }
        return result;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
