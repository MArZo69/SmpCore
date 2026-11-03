package fr.smp.core;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /rankup : le joueur paie (économie Vault = Essentials) puis passe au grade suivant (LuckPerms).
 * Le grade du joueur est lu dans LuckPerms : on regarde ses groupes directs, on garde le plus haut de la liste.
 * Les groupes hors liste (helpeur, moderateur, owner...) ne sont jamais touchés.
 */
public final class RankupManager {

    /** Un grade de la progression. */
    public record Step(String id, String display, double price, List<String> advantages) {
    }

    /** Données du menu principal : tous les grades + le grade actuel du joueur. */
    public record MenuState(List<Step> steps, int currentIndex, double balance, String symbol,
                            Set<String> pending) {
        public Step current() {
            return steps.get(currentIndex);
        }

        public boolean max() {
            return currentIndex >= steps.size() - 1;
        }

        public Step next() {
            return max() ? null : steps.get(currentIndex + 1);
        }

        public boolean pending(Step step) {
            return pending.contains(step.id());
        }
    }

    /** Données de l'écran de confirmation d'un grade choisi. */
    public record ConfirmState(List<Step> steps, int currentIndex, int targetIndex,
                               double balance, String symbol, Set<String> pending) {
        public Step current() {
            return steps.get(currentIndex);
        }

        public Step target() {
            return steps.get(targetIndex);
        }

        public Step previous() {
            return targetIndex > 0 ? steps.get(targetIndex - 1) : current();
        }

        /** Le grade est déjà possédé (c'est le grade actuel ou un grade inférieur). */
        public boolean owned() {
            return targetIndex <= currentIndex;
        }

        /** C'est bien le prochain grade de la progression. */
        public boolean next() {
            return targetIndex == currentIndex + 1;
        }

        /** Le joueur doit d'abord acheter un autre grade. */
        public boolean locked() {
            return targetIndex > currentIndex + 1;
        }

        /** Ce grade a été acheté sur la boutique et attend d'être récupéré. */
        public boolean pendingTarget() {
            return pending.contains(target().id());
        }

        public boolean canAfford() {
            return balance >= target().price();
        }
    }

    private final SmpCore plugin;
    private volatile List<Step> steps = List.of();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private Map<String, List<String>> defaultAdvantages = Map.of();

    public RankupManager(SmpCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defaultAdvantages = loadDefaultAdvantages();
        List<Step> list = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("rankup.grades")) {
            Object id = raw.get("id");
            if (id == null) {
                continue;
            }
            Object display = raw.get("display");
            Object price = raw.get("price");
            List<String> advantages = new ArrayList<>();
            Object rawAdvantages = raw.get("advantages");
            boolean hasAdvantagesConfig = rawAdvantages instanceof List<?>;
            if (rawAdvantages instanceof List<?> lines) {
                for (Object line : lines) {
                    if (line != null && !line.toString().isBlank()) {
                        advantages.add(line.toString());
                    }
                }
            }
            if (!hasAdvantagesConfig) {
                advantages = new ArrayList<>(defaultAdvantages.getOrDefault(
                        id.toString().toLowerCase(Locale.ROOT), List.of()));
            }
            list.add(new Step(
                    id.toString().toLowerCase(Locale.ROOT),
                    display != null ? display.toString() : id.toString(),
                    price instanceof Number n ? n.doubleValue() : 0.0,
                    List.copyOf(advantages)
            ));
        }
        if (list.size() < 2) {
            plugin.getLogger().warning("rankup.grades doit contenir au moins 2 grades.");
        }
        if (list.size() > 25) {
            plugin.getLogger().warning("rankup.grades contient plus de 25 grades : le menu n'affichera que les 25 premiers.");
        }
        steps = List.copyOf(list);

        LuckPerms luckPerms = luckPerms();
        if (luckPerms != null) {
            for (Step step : list) {
                if (luckPerms.getGroupManager().getGroup(step.id()) == null) {
                    plugin.getLogger().warning("Grade rankup introuvable dans LuckPerms : " + step.id());
                }
            }
        }
    }

    /** Avantages fournis dans le config.yml du jar, utilisés si le fichier du serveur est ancien. */
    private Map<String, List<String>> loadDefaultAdvantages() {
        InputStream stream = plugin.getResource("config.yml");
        if (stream == null) {
            return Map.of();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            Map<String, List<String>> result = new HashMap<>();
            for (Map<?, ?> raw : defaults.getMapList("rankup.grades")) {
                Object id = raw.get("id");
                Object advantages = raw.get("advantages");
                if (id == null || !(advantages instanceof List<?> lines)) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (Object line : lines) {
                    if (line != null && !line.toString().isBlank()) {
                        values.add(line.toString());
                    }
                }
                if (!values.isEmpty()) {
                    result.put(id.toString().toLowerCase(Locale.ROOT), List.copyOf(values));
                }
            }
            return Map.copyOf(result);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de lire les avantages par défaut : " + e.getMessage());
            return Map.of();
        }
    }

    public int stepCount() {
        return steps.size();
    }

    public List<Step> steps() {
        return steps;
    }

    /** Index du grade actuel du joueur, ou -1 si LuckPerms est indisponible. */
    public int currentIndex(Player player) {
        User user = user(player.getUniqueId());
        return user == null ? -1 : currentIndex(user, steps);
    }

    /**
     * Applique un grade LuckPerms sans paiement.
     * Utilisé par la boutique LureCoin après le débit des coins.
     * Les callbacks sont exécutés sur le thread principal.
     */
    public void applyGrade(Player player, Step target, Runnable onSuccess, Runnable onError) {
        LuckPerms luckPerms = luckPerms();
        User user = luckPerms == null ? null : luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null || indexOf(steps, target.id()) < 0) {
            onError.run();
            return;
        }

        Set<String> trackIds = new HashSet<>();
        for (Step step : steps) {
            trackIds.add(step.id());
        }

        luckPerms.getUserManager().modifyUser(player.getUniqueId(), editable -> {
            for (String id : trackIds) {
                editable.data().remove(InheritanceNode.builder(id).build());
            }
            editable.data().add(InheritanceNode.builder(target.id()).build());
        }).whenComplete((ignored, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().severe("Achat de grade echoue pour " + player.getName() + " : " + error);
                onError.run();
                return;
            }
            if (plugin.getConfig().getBoolean("rankup.announce", true)) {
                plugin.messages().broadcast("rankup-announce",
                        "player", player.getName(), "next", target.display());
            }
            onSuccess.run();
        }));
    }

    // ------------------------------------------------------------------
    //  /rankup  : affiche le prix du grade suivant
    // ------------------------------------------------------------------
    public void info(Player player) {
        Messages messages = plugin.messages();
        List<Step> list = steps;
        if (list.size() < 2) {
            messages.send(player, "config-error");
            return;
        }
        Economy economy = economy();
        if (economy == null) {
            messages.send(player, "no-economy");
            return;
        }
        User user = user(player.getUniqueId());
        if (user == null) {
            messages.send(player, "no-luckperms");
            return;
        }

        int index = currentIndex(user, list);
        Step current = list.get(index);
        if (index >= list.size() - 1) {
            messages.send(player, "rankup-max", "current", current.display());
            return;
        }
        Step next = list.get(index + 1);
        double balance = economy.getBalance(player);
        String symbol = symbol();

        messages.send(player, "rankup-info-current", "current", current.display());
        messages.send(player, "rankup-info-next", "next", next.display(),
                "price", Money.format(next.price()), "symbol", symbol);
        messages.send(player, "rankup-info-balance", "balance", Money.format(balance), "symbol", symbol);
        if (balance < next.price()) {
            messages.send(player, "rankup-info-missing",
                    "missing", Money.format(next.price() - balance), "symbol", symbol);
        } else {
            messages.send(player, "rankup-info-confirm");
        }
    }

    // ------------------------------------------------------------------
    //  Menu /rankup
    // ------------------------------------------------------------------

    /** Prépare le menu principal. Renvoie null (et prévient le joueur) en cas de problème. */
    public MenuState menuState(Player player) {
        Messages messages = plugin.messages();
        List<Step> list = steps;
        if (list.size() < 2) {
            messages.send(player, "config-error");
            return null;
        }
        Economy economy = economy();
        if (economy == null) {
            messages.send(player, "no-economy");
            return null;
        }
        User user = user(player.getUniqueId());
        if (user == null) {
            messages.send(player, "no-luckperms");
            return null;
        }

        int index = currentIndex(user, list);
        return new MenuState(list, index, economy.getBalance(player), symbol(),
                plugin.pending().get(player.getUniqueId()));
    }

    /** Prépare l'écran de confirmation pour un grade précis. */
    public ConfirmState confirmState(Player player, Step target) {
        Messages messages = plugin.messages();
        List<Step> list = steps;
        if (list.size() < 2) {
            messages.send(player, "config-error");
            return null;
        }
        Economy economy = economy();
        if (economy == null) {
            messages.send(player, "no-economy");
            return null;
        }
        User user = user(player.getUniqueId());
        if (user == null) {
            messages.send(player, "no-luckperms");
            return null;
        }

        int targetIndex = indexOf(list, target.id());
        if (targetIndex < 0) {
            messages.send(player, "config-error");
            return null;
        }
        int currentIndex = currentIndex(user, list);
        return new ConfirmState(list, currentIndex, targetIndex, economy.getBalance(player), symbol(),
                plugin.pending().get(player.getUniqueId()));
    }

    // ------------------------------------------------------------------
    //  Boutique : achat en attente
    // ------------------------------------------------------------------
    /** Ajoute un grade acheté sur Tebex en attente. Il sera récupéré via /rankup. */
    public void grant(CommandSender sender, String playerName, String gradeId) {
        Messages messages = plugin.messages();
        List<Step> list = steps;
        Step target = null;
        for (Step step : list) {
            if (step.id().equalsIgnoreCase(gradeId)) {
                target = step;
                break;
            }
        }
        if (target == null) {
            messages.send(sender, "grant-unknown-grade", "grade", gradeId);
            return;
        }

        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(playerName);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            messages.send(sender, "grant-unknown-player", "player", playerName);
            return;
        }

        UUID uuid = offline.getUniqueId();
        LuckPerms luckPerms = luckPerms();
        User user = luckPerms == null ? null : luckPerms.getUserManager().getUser(uuid);
        if (user != null) {
            int current = currentIndex(user, list);
            int targetIndex = indexOf(list, target.id());
            if (targetIndex >= 0 && targetIndex <= current) {
                messages.send(sender, "grant-already-owned",
                        "player", playerName, "grade", target.display());
                return;
            }
        }

        if (!plugin.pending().add(uuid, target.id())) {
            messages.send(sender, "grant-already",
                    "player", playerName, "grade", target.display());
            return;
        }

        messages.send(sender, "grant-added",
                "player", playerName, "grade", target.display());
        Player online = offline.getPlayer();
        if (online != null) {
            messages.send(online, "grant-received", "grade", target.display());
        }
    }

    /** Ids des grades, pour la tab-completion. */
    public List<String> stepIds() {
        List<String> ids = new ArrayList<>();
        for (Step step : steps) {
            ids.add(step.id());
        }
        return ids;
    }

    // ------------------------------------------------------------------
    //  /rankup confirm  : paiement puis promotion
    // ------------------------------------------------------------------
    public void confirm(Player player) {
        confirm(player, (Runnable) null);
    }

    public void confirm(Player player, Runnable onSuccess) {
        confirm(player, (String) null, false, onSuccess);
    }

    /** Confirme l'achat du grade choisi dans le menu. Le grade doit toujours être le prochain. */
    public void confirm(Player player, Step target, Runnable onSuccess) {
        confirm(player, target == null ? null : target.id(), false, onSuccess);
    }

    /** Récupère un grade acheté sur la boutique, sans repayer. */
    public void claim(Player player, Step target, Runnable onSuccess) {
        confirm(player, target == null ? null : target.id(), true, onSuccess);
    }

    /**
     * @param targetId null = acheter le prochain grade ; sinon id du grade choisi.
     * @param free true = achat boutique déjà payé, on ne retire pas d'argent.
     * @param onSuccess exécuté sur le thread principal après une promotion réussie (null autorisé).
     */
    private void confirm(Player player, String targetId, boolean free, Runnable onSuccess) {
        Messages messages = plugin.messages();
        UUID uuid = player.getUniqueId();

        if (!busy.add(uuid)) {
            messages.send(player, "busy");
            return;
        }

        boolean handedOver = false; // vrai quand la promotion asynchrone prend le relais
        try {
            List<Step> list = steps;
            if (list.size() < 2) {
                messages.send(player, "config-error");
                return;
            }

            LuckPerms luckPerms = luckPerms();
            User user = luckPerms == null ? null : luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                messages.send(player, "no-luckperms");
                return;
            }

            int currentIndex = currentIndex(user, list);
            int targetIndex;
            if (targetId == null) {
                if (currentIndex >= list.size() - 1) {
                    messages.send(player, "rankup-max", "current", list.get(currentIndex).display());
                    return;
                }
                targetIndex = currentIndex + 1;
            } else {
                targetIndex = indexOf(list, targetId);
                if (targetIndex < 0) {
                    messages.send(player, "config-error");
                    return;
                }
            }

            Step target = list.get(targetIndex);
            if (targetIndex <= currentIndex) {
                messages.send(player, "rankup-already-owned", "target", target.display());
                return;
            }
            if (targetIndex > currentIndex + 1) {
                messages.send(player, "rankup-locked",
                        "target", target.display(),
                        "previous", list.get(targetIndex - 1).display());
                return;
            }
            final boolean pendingClaim = plugin.pending().has(uuid, target.id());
            if (free && !pendingClaim) {
                messages.send(player, "rankup-no-pending", "target", target.display());
                return;
            }
            // Si le grade a été acheté sur Tebex, on le récupère sans repayer,
            // même si le joueur passe par /rankup confirm.
            final boolean usePending = free || pendingClaim;

            final Economy economy;
            if (usePending) {
                economy = null;
            } else {
                economy = economy();
                if (economy == null) {
                    messages.send(player, "no-economy");
                    return;
                }
            }

            double price = target.price();
            String symbol = symbol();

            if (!usePending) {
                if (!economy.has(player, price)) {
                    double missing = price - economy.getBalance(player);
                    messages.send(player, "rankup-not-enough", "next", target.display(),
                            "target", target.display(),
                            "missing", Money.format(missing), "symbol", symbol);
                    return;
                }

                EconomyResponse response = economy.withdrawPlayer(player, price);
                if (!response.transactionSuccess()) {
                    messages.send(player, "rankup-withdraw-failed");
                    return;
                }
            }

            // Retire les groupes de la progression, ajoute le nouveau. Les autres groupes ne bougent pas.
            Set<String> trackIds = new HashSet<>();
            for (Step step : list) {
                trackIds.add(step.id());
            }
            handedOver = true;
            luckPerms.getUserManager().modifyUser(uuid, editable -> {
                for (String id : trackIds) {
                    editable.data().remove(InheritanceNode.builder(id).build());
                }
                editable.data().add(InheritanceNode.builder(target.id()).build());
            }).whenComplete((ignored, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                busy.remove(uuid);
                if (error != null) {
                    if (economy != null) {
                        economy.depositPlayer(player, price); // remboursement
                    }
                    plugin.getLogger().severe("Rankup echoue pour " + player.getName() + " : " + error);
                    messages.send(player, usePending ? "rankup-claim-error" : "rankup-error");
                    return;
                }
                if (usePending) {
                    plugin.pending().remove(uuid, target.id());
                    messages.send(player, "rankup-claim-success",
                            "next", target.display(), "target", target.display());
                } else {
                    messages.send(player, "rankup-success", "next", target.display(),
                            "price", Money.format(price), "symbol", symbol);
                }
                if (plugin.getConfig().getBoolean("rankup.announce", true)) {
                    messages.broadcast("rankup-announce", "player", player.getName(), "next", target.display());
                }
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }));
        } finally {
            if (!handedOver) {
                busy.remove(uuid);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Utilitaires
    // ------------------------------------------------------------------

    /** Index du grade le plus haut parmi les groupes directs du joueur (0 par défaut). */
    private int currentIndex(User user, List<Step> list) {
        Set<String> groups = new HashSet<>();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            groups.add(node.getGroupName().toLowerCase(Locale.ROOT));
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if (groups.contains(list.get(i).id())) {
                return i;
            }
        }
        return 0;
    }

    private int indexOf(List<Step> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return -1;
    }

    private String symbol() {
        return plugin.getConfig().getString("currency-symbol", "$");
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    private LuckPerms luckPerms() {
        RegisteredServiceProvider<LuckPerms> provider =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        return provider == null ? null : provider.getProvider();
    }

    private User user(UUID uuid) {
        LuckPerms luckPerms = luckPerms();
        return luckPerms == null ? null : luckPerms.getUserManager().getUser(uuid);
    }
}
