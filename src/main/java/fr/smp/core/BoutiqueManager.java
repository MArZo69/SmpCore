package fr.smp.core;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boutique en LureCoins.
 * Les grades restent dans l'ordre de /rankup : seul le prochain grade est achetable.
 * Les clés sont données via la commande du plugin Crates.
 * Les LureCoins ne s'obtiennent pas en jeu : ils sont ajoutés par Tebex via la console.
 */
public final class BoutiqueManager {

    private static final Map<String, Long> DEFAULT_COIN_PRICES = Map.of(
            "heros", 500L,
            "legende", 1500L,
            "supreme", 2250L,
            "divin", 2700L
    );

    private final SmpCore plugin;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile List<KeyOffer> keyOffers = List.of();

    public BoutiqueManager(SmpCore plugin) {
        this.plugin = plugin;
    }

    /** Recharge les offres de clés depuis config.yml. */
    public void load() {
        List<KeyOffer> result = new ArrayList<>();
        for (Map<?, ?> raw : keyDefinitions()) {
            Object id = raw.get("id");
            if (id == null) {
                continue;
            }

            String keyId = id.toString().toLowerCase(Locale.ROOT);
            String display = stringValue(raw.get("display"), id.toString());
            String crate = resolveCrateName(stringValue(raw.get("crate"), id.toString()));
            Material material = Material.matchMaterial(stringValue(raw.get("material"), "CHEST"));
            if (material == null) {
                material = Material.CHEST;
            }
            boolean glow = booleanValue(raw.get("glow"));

            List<KeyPackage> packages = new ArrayList<>();
            Object rawPackages = raw.get("packages");
            if (rawPackages instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> map)) {
                        continue;
                    }
                    int quantity = intValue(map.get("quantity"));
                    long price = longValue(map.get("price"));
                    if (quantity > 0 && price > 0) {
                        packages.add(new KeyPackage(quantity, price, 0));
                    }
                }
            }

            if (packages.isEmpty()) {
                plugin.getLogger().warning("boutique.keys." + keyId + " n'a aucune offre valide.");
                continue;
            }

            long unitPrice = 0L;
            for (KeyPackage offer : packages) {
                if (offer.quantity() == 1) {
                    unitPrice = offer.price();
                    break;
                }
            }

            List<KeyPackage> withDiscount = new ArrayList<>();
            for (KeyPackage offer : packages) {
                int discount = 0;
                if (offer.quantity() > 1 && unitPrice > 0) {
                    long fullPrice = unitPrice * offer.quantity();
                    if (offer.price() < fullPrice) {
                        discount = (int) Math.round(
                                (1.0D - (double) offer.price() / (double) fullPrice) * 100.0D);
                    }
                }
                withDiscount.add(new KeyPackage(offer.quantity(), offer.price(), Math.max(0, discount)));
            }

            result.add(new KeyOffer(keyId, display, crate, material, glow, List.copyOf(withDiscount)));
        }
        keyOffers = List.copyOf(result);
        logCrateSetup();
    }

    public List<KeyOffer> keyOffers() {
        return keyOffers;
    }

    public List<RankupManager.Step> steps() {
        return plugin.rankup().steps();
    }

    public int currentIndex(Player player) {
        return plugin.rankup().currentIndex(player);
    }

    /** Prix en LureCoins du grade, ou -1 s'il n'est pas configuré. */
    public long coinPrice(RankupManager.Step step) {
        return plugin.getConfig().getLong("boutique.grades." + step.id(),
                DEFAULT_COIN_PRICES.getOrDefault(step.id(), -1L));
    }

    /**
     * Relit l'état du joueur au moment de l'affichage/clic.
     * Retourne null si LuckPerms est indisponible ou si le grade n'existe plus.
     */
    public GradeState gradeState(Player player, RankupManager.Step target) {
        if (target == null) {
            return null;
        }
        List<RankupManager.Step> list = steps();
        if (list.size() < 2) {
            return null;
        }
        int current = currentIndex(player);
        if (current < 0) {
            return null;
        }
        int targetIndex = indexOf(list, target.id());
        if (targetIndex < 0) {
            return null;
        }
        return new GradeState(
                list,
                current,
                targetIndex,
                plugin.coins().get(player.getUniqueId()),
                coinPrice(target)
        );
    }

    /** Achète le grade avec des LureCoins, puis applique le groupe LuckPerms. */
    public void buyGrade(Player player, RankupManager.Step target, Runnable onSuccess) {
        Messages messages = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (!busy.add(uuid)) {
            messages.send(player, "busy");
            return;
        }

        boolean handedOver = false;
        try {
            List<RankupManager.Step> list = steps();
            if (list.size() < 2) {
                messages.send(player, "config-error");
                return;
            }

            int current = currentIndex(player);
            if (current < 0) {
                messages.send(player, "no-luckperms");
                return;
            }

            int targetIndex = indexOf(list, target.id());
            if (targetIndex < 0) {
                messages.send(player, "config-error");
                return;
            }
            if (targetIndex <= current) {
                messages.send(player, "rankup-already-owned", "target", target.display());
                return;
            }
            if (targetIndex > current + 1) {
                messages.send(player, "rankup-locked",
                        "target", target.display(),
                        "previous", list.get(targetIndex - 1).display());
                return;
            }

            long price = coinPrice(target);
            if (price <= 0) {
                messages.send(player, "boutique.grade-unavailable");
                return;
            }
            if (!plugin.coins().has(uuid, price)) {
                long missing = price - plugin.coins().get(uuid);
                messages.send(player, "coins.not-enough",
                        "target", target.display(),
                        "missing", plugin.coins().format(missing),
                        "currency", plugin.coins().currencyName());
                return;
            }
            if (!plugin.coins().remove(uuid, price)) {
                messages.send(player, "boutique.grade-unavailable");
                return;
            }

            handedOver = true;
            plugin.rankup().applyGrade(player, target, () -> {
                busy.remove(uuid);
                if (player.isOnline()) {
                    messages.send(player, "boutique.success",
                            "target", target.display(),
                            "price", plugin.coins().format(price),
                            "currency", plugin.coins().currencyName());
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                }
            }, () -> {
                busy.remove(uuid);
                plugin.coins().add(uuid, price);
                if (player.isOnline()) {
                    messages.send(player, "boutique.error");
                    messages.send(player, "boutique.refund");
                }
            });
        } finally {
            if (!handedOver) {
                busy.remove(uuid);
            }
        }
    }

    /** Achète une clé et la donne avec /crates give physical ... */
    public void buyKey(Player player, KeyOffer offer, KeyPackage pack, Runnable onSuccess) {
        Messages messages = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (!busy.add(uuid)) {
            messages.send(player, "busy");
            return;
        }

        try {
            if (offer == null || pack == null || pack.quantity() <= 0 || pack.price() <= 0) {
                messages.send(player, "boutique.key-unavailable");
                return;
            }

            String currency = plugin.coins().currencyName();
            if (!plugin.coins().has(uuid, pack.price())) {
                long missing = pack.price() - plugin.coins().get(uuid);
                messages.send(player, "coins.not-enough",
                        "target", pack.quantity() + "x " + offer.display(),
                        "missing", plugin.coins().format(missing),
                        "currency", currency);
                return;
            }
            if (!plugin.coins().remove(uuid, pack.price())) {
                messages.send(player, "boutique.key-unavailable");
                return;
            }

            String command = "crates give physical " + offer.crate() + " "
                    + pack.quantity() + " " + player.getName();
            boolean success;
            try {
                success = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Commande Crates échouée (/" + command + ") : " + e.getMessage());
                success = false;
            }
            plugin.getLogger().info("Achat de clé : " + player.getName() + " -> /" + command
                    + " (résultat=" + success + ")");

            if (!success) {
                plugin.getLogger().warning("Commande Crates refusée (/" + command
                        + ") : vérifie le nom du crate dans config.yml > boutique.keys > crate.");
                plugin.coins().add(uuid, pack.price());
                messages.send(player, "boutique.key-error");
                return;
            }

            String qty = Integer.toString(pack.quantity());
            String price = plugin.coins().format(pack.price());
            messages.send(player, "boutique.key-success",
                    "key", offer.display(), "qty", qty,
                    "price", price, "currency", currency);
            messages.broadcast("boutique.key-announce",
                    "player", player.getName(), "key", offer.display(), "qty", qty,
                    "price", price, "currency", currency);

            if (onSuccess != null) {
                onSuccess.run();
            }
        } finally {
            busy.remove(uuid);
        }
    }

    /** Dossier des crates du plugin (LureCrates, ou CrazyCrates en secours). */
    private File findCratesDir() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        for (String folder : List.of("LureCrates", "CrazyCrates")) {
            File cratesDir = new File(pluginsDir, folder + File.separator + "crates");
            if (cratesDir.isDirectory()) {
                return cratesDir;
            }
        }
        return null;
    }

    /** Utilise le nom exact du fichier de crate (la casse peut compter). */
    private String resolveCrateName(String configured) {
        File cratesDir = findCratesDir();
        if (cratesDir == null) {
            return configured;
        }
        String[] files = cratesDir.list((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return configured;
        }
        for (String file : files) {
            String name = file.substring(0, file.length() - 4);
            if (name.equalsIgnoreCase(configured)) {
                return name;
            }
        }
        return configured;
    }

    /** Offres du config.yml du serveur, sinon celles du config.yml fourni dans le jar. */
    private List<Map<?, ?>> keyDefinitions() {
        List<Map<?, ?>> fromServer = plugin.getConfig().getMapList("boutique.keys");
        if (!fromServer.isEmpty()) {
            return fromServer;
        }

        InputStream stream = plugin.getResource("config.yml");
        if (stream == null) {
            return List.of();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            List<Map<?, ?>> fallback = defaults.getMapList("boutique.keys");
            if (!fallback.isEmpty()) {
                plugin.getLogger().warning("'boutique.keys' est absent de ton config.yml : "
                        + "utilisation des offres par defaut du jar. Remplace config.yml pour les modifier.");
            }
            return fallback;
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de lire le config.yml par defaut : " + e.getMessage());
            return List.of();
        }
    }

    /** Affiche dans la console les crates configures et ceux reellement presents dans LureCrates. */
    private void logCrateSetup() {
        if (keyOffers.isEmpty()) {
            plugin.getLogger().warning("Boutique cles : aucune offre. Verifie 'boutique.keys' dans config.yml "
                    + "(ou remplace le fichier par celui fourni).");
            return;
        }

        List<String> configured = new ArrayList<>();
        for (KeyOffer offer : keyOffers) {
            configured.add(offer.crate());
        }
        plugin.getLogger().info("Boutique cles : " + keyOffers.size() + " offre(s) -> "
                + String.join(", ", configured));

        File cratesDir = findCratesDir();
        if (cratesDir == null) {
            plugin.getLogger().info("Dossier des crates introuvable (LureCrates/crates ou CrazyCrates/crates) : "
                    + "verification des noms de crates ignoree.");
            return;
        }

        String[] files = cratesDir.list((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Aucun fichier de crate dans " + cratesDir.getPath() + ".");
            return;
        }

        List<String> available = new ArrayList<>();
        for (String file : files) {
            available.add(file.substring(0, file.length() - 4));
        }
        plugin.getLogger().info("Crates detectes -> " + String.join(", ", available));

        for (KeyOffer offer : keyOffers) {
            boolean found = available.stream().anyMatch(name -> name.equalsIgnoreCase(offer.crate()));
            if (!found) {
                plugin.getLogger().warning("Crate '" + offer.crate() + "' introuvable. "
                        + "Crates disponibles : " + String.join(", ", available)
                        + ". Corrige boutique.keys." + offer.id() + ".crate dans config.yml.");
            }
        }
    }

    private int indexOf(List<RankupManager.Step> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public record KeyOffer(String id, String display, String crate, Material material, boolean glow,
                           List<KeyPackage> packages) {
    }

    public record KeyPackage(int quantity, long price, int discount) {
    }

    public record GradeState(List<RankupManager.Step> steps, int currentIndex, int targetIndex,
                             long coins, long price) {

        public RankupManager.Step target() {
            return steps.get(targetIndex);
        }

        public RankupManager.Step previous() {
            return targetIndex > 0 ? steps.get(targetIndex - 1) : steps.get(currentIndex);
        }

        public boolean owned() {
            return targetIndex <= currentIndex;
        }

        public boolean next() {
            return targetIndex == currentIndex + 1;
        }

        public boolean locked() {
            return targetIndex > currentIndex + 1;
        }

        public boolean available() {
            return price > 0;
        }

        public boolean canAfford() {
            return available() && coins >= price;
        }

        public long missing() {
            return Math.max(0L, price - coins);
        }
    }
}
