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

/** Catégorie de la boutique : Grades (fonctionnel), Clés et Boosts (bientôt). */
public final class BoutiqueCategoryGui implements InventoryHolder {

    public enum Category {
        GRADES, KEYS, BOOSTS
    }

    static final int SLOT_BACK = 18;
    static final int SLOT_BALANCE = 22;
    static final int SLOT_CLOSE = 26;
    static final int SLOT_COMING_SOON = 13;

    private static final int SIZE = 27;
    private static final Material FILLER = Material.PURPLE_STAINED_GLASS_PANE;

    private final SmpCore plugin;
    private final Player player;
    private final Category category;
    private final Map<Integer, RankupManager.Step> gradeSlots = new HashMap<>();
    private Inventory inventory;

    public BoutiqueCategoryGui(SmpCore plugin, Player player, Category category) {
        this.plugin = plugin;
        this.player = player;
        this.category = category;
    }

    public void open() {
        Messages messages = plugin.messages();
        gradeSlots.clear();

        inventory = Bukkit.createInventory(this, SIZE, messages.raw(titleKey()));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(FILLER));
        }

        if (category == Category.GRADES) {
            List<RankupManager.Step> steps = plugin.rankup().steps();
            if (steps.size() < 2) {
                messages.send(player, "config-error");
                return;
            }
            int current = plugin.rankup().currentIndex(player);
            if (current < 0) {
                messages.send(player, "no-luckperms");
                return;
            }

            long coins = plugin.coins().get(player.getUniqueId());
            List<Integer> layout = layoutSlots(steps.size());
            for (int i = 0; i < layout.size() && i < steps.size(); i++) {
                int slot = layout.get(i);
                RankupManager.Step step = steps.get(i);
                inventory.setItem(slot, gradeItem(messages, steps, i, current, coins, step));
                gradeSlots.put(slot, step);
            }
        } else {
            Material material = category == Category.KEYS ? Material.TRIPWIRE_HOOK : Material.BLAZE_POWDER;
            inventory.setItem(SLOT_COMING_SOON, GuiItems.item(material,
                    messages.raw("boutique.coming-soon"),
                    messages.rawList("boutique.coming-soon-lore")));
        }

        inventory.setItem(SLOT_BACK, GuiItems.item(Material.ARROW,
                messages.raw("boutique.back"),
                List.of(messages.raw("boutique.back-lore"))));

        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.SUNFLOWER,
                messages.raw("boutique.balance",
                        "amount", plugin.coins().format(plugin.coins().get(player.getUniqueId())),
                        "currency", plugin.coins().currencyName()),
                messages.rawList("boutique.balance-lore")));

        inventory.setItem(SLOT_CLOSE, GuiItems.item(Material.BARRIER,
                messages.raw("boutique.close"), List.of()));

        player.openInventory(inventory);
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
        RankupManager.Step step = gradeSlots.get(slot);
        if (step != null) {
            new BoutiqueConfirmGui(plugin, player, step).open();
        }
    }

    private ItemStack gradeItem(Messages messages, List<RankupManager.Step> steps,
                                int index, int current, long coins, RankupManager.Step step) {
        long price = plugin.boutique().coinPrice(step);
        Material material;
        List<Component> lore = new ArrayList<>();

        if (index == current) {
            material = Material.NETHER_STAR;
            lore.add(messages.raw("rankup-gui.current-grade"));
            if (index >= steps.size() - 1) {
                lore.add(messages.raw("rankup-gui.max"));
            }
        } else if (index < current) {
            material = Material.LIME_DYE;
            lore.add(messages.raw("rankup-gui.owned"));
        } else {
            material = index == current + 1 ? Material.DIAMOND : Material.GRAY_DYE;
            if (price <= 0) {
                lore.add(messages.raw("boutique.grade-unavailable"));
            } else {
                lore.add(messages.raw("boutique.grade-price",
                        "price", plugin.coins().format(price),
                        "currency", plugin.coins().currencyName()));
            }

            if (index == current + 1) {
                if (price > 0) {
                    if (coins < price) {
                        lore.add(messages.raw("boutique.grade-missing",
                                "missing", plugin.coins().format(price - coins),
                                "currency", plugin.coins().currencyName()));
                    } else {
                        lore.add(messages.raw("boutique.grade-buy"));
                    }
                }
            } else {
                lore.add(messages.raw("boutique.grade-locked"));
                lore.add(messages.raw("boutique.grade-locked-lore",
                        "previous", steps.get(index - 1).display()));
            }
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

    private String titleKey() {
        return switch (category) {
            case GRADES -> "boutique.title-grades";
            case KEYS -> "boutique.title-keys";
            case BOOSTS -> "boutique.title-boosts";
        };
    }

    /** 5 grades -> une seule rangée centrée au milieu du coffre. */
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
            if (slot == SLOT_BACK || slot == SLOT_BALANCE || slot == SLOT_CLOSE) {
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
