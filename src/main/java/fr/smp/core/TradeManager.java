package fr.smp.core;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /trade : échange d'objets, d'argent (Vault) et de niveaux d'XP.
 * Une demande expire automatiquement au bout de request-timeout-seconds (180 s par défaut).
 * Les objets proposés sont physiquement dans le GUI ; ils sont rendus si l'échange est annulé.
 */
public final class TradeManager {

    /** Slots d'offre du joueur A (colonnes de gauche). */
    public static final int[] SLOTS_A = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    /** Slots d'offre du joueur B (colonnes de droite). */
    public static final int[] SLOTS_B = {6, 7, 8, 15, 16, 17, 24, 25, 26};

    public static final int SLOT_HEAD_A = 4;
    public static final int SLOT_STATUS = 13;
    public static final int SLOT_HEAD_B = 22;
    public static final int SLOT_MONEY_A = 27;
    public static final int SLOT_XP_A = 28;
    public static final int SLOT_XP_B = 34;
    public static final int SLOT_MONEY_B = 35;
    public static final int SLOT_CONFIRM_A = 45;
    public static final int SLOT_CANCEL = 49;
    public static final int SLOT_CONFIRM_STATUS_A = 51;
    public static final int SLOT_CONFIRM_STATUS_B = 52;
    public static final int SLOT_CONFIRM_B = 53;
    public static final int SIZE = 54;

    private final SmpCore plugin;
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Input> inputs = new ConcurrentHashMap<>();
    private final BukkitTask ticker;

    public TradeManager(SmpCore plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---------------------------------------------------------------
    // Demandes
    // ---------------------------------------------------------------

    /** /trade <joueur> : envoie une demande. */
    public void request(Player from, String targetName) {
        Messages m = plugin.messages();
        Player to = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(targetName)) {
                to = online;
                break;
            }
        }
        if (to == null) {
            m.send(from, "trade.player-not-found", "player", targetName);
            return;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            m.send(from, "trade.self");
            return;
        }
        if (sessions.containsKey(from.getUniqueId())) {
            m.send(from, "trade.already-in-trade");
            return;
        }
        if (sessions.containsKey(to.getUniqueId())) {
            m.send(from, "trade.target-busy", "player", to.getName());
            return;
        }

        long now = System.currentTimeMillis();
        Long until = cooldowns.get(from.getUniqueId());
        if (until != null && until > now) {
            long seconds = Math.max(1L, (until - now + 999L) / 1000L);
            m.send(from, "trade.cooldown", "seconds", Long.toString(seconds));
            return;
        }

        int timeout = Math.max(10, plugin.getConfig().getInt("trade.request-timeout-seconds", 180));
        // Une seule demande sortante à la fois : l'ancienne est annulée.
        Iterator<Map.Entry<UUID, Request>> outgoing = requests.entrySet().iterator();
        while (outgoing.hasNext()) {
            Map.Entry<UUID, Request> entry = outgoing.next();
            if (entry.getValue().from.equals(from.getUniqueId())) {
                outgoing.remove();
                Player old = Bukkit.getPlayer(entry.getKey());
                if (old != null) {
                    m.send(old, "trade.cancelled", "player", from.getName());
                }
            }
        }
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), from.getName(), to.getName(),
                now + timeout * 1000L));

        int spam = Math.max(1, plugin.getConfig().getInt("trade.request-cooldown-seconds", 5));
        cooldowns.put(from.getUniqueId(), now + spam * 1000L);

        m.send(from, "trade.request-sent", "player", to.getName(), "seconds", Integer.toString(timeout));
        m.send(to, "trade.request-received", "player", from.getName());
    }

    /** /trade accepter [joueur] : ouvre l'échange. */
    public void accept(Player target, String expectedName) {
        Messages m = plugin.messages();
        Request request = requests.get(target.getUniqueId());
        if (request == null) {
            m.send(target, "trade.no-request");
            return;
        }
        if (expectedName != null && !request.fromName.equalsIgnoreCase(expectedName)) {
            m.send(target, "trade.no-request-from", "player", expectedName);
            return;
        }

        Player from = Bukkit.getPlayer(request.from);
        if (from == null) {
            requests.remove(target.getUniqueId());
            m.send(target, "trade.target-offline", "player", request.fromName);
            return;
        }
        if (sessions.containsKey(target.getUniqueId()) || sessions.containsKey(from.getUniqueId())) {
            requests.remove(target.getUniqueId());
            m.send(target, "trade.already-in-trade");
            m.send(from, "trade.target-busy", "player", target.getName());
            return;
        }

        requests.remove(target.getUniqueId());

        Session session = new Session(from.getUniqueId(), from.getName(),
                target.getUniqueId(), target.getName());
        session.gui = new TradeGui(plugin, session);
        session.inventory = session.gui.create();
        sessions.put(from.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);

        from.openInventory(session.inventory);
        target.openInventory(session.inventory);

        m.send(from, "trade.accepted", "player", target.getName());
        m.send(target, "trade.opened", "player", from.getName());
    }

    /** /trade refuser [joueur]. */
    public void decline(Player target, String expectedName) {
        Messages m = plugin.messages();
        Request request = requests.get(target.getUniqueId());
        if (request == null) {
            m.send(target, "trade.no-request");
            return;
        }
        if (expectedName != null && !request.fromName.equalsIgnoreCase(expectedName)) {
            m.send(target, "trade.no-request-from", "player", expectedName);
            return;
        }

        requests.remove(target.getUniqueId());
        m.send(target, "trade.declined-target", "player", request.fromName);

        Player from = Bukkit.getPlayer(request.from);
        if (from != null) {
            m.send(from, "trade.declined-sender", "player", target.getName());
        }
    }

    /** /trade annuler : annule l'échange actif, sinon la demande envoyée, sinon la demande reçue. */
    public void cancelCommand(Player player) {
        Messages m = plugin.messages();

        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancel(session, player);
            return;
        }

        for (Map.Entry<UUID, Request> entry : requests.entrySet()) {
            if (entry.getValue().from.equals(player.getUniqueId())) {
                requests.remove(entry.getKey());
                m.send(player, "trade.cancelled-request", "player", entry.getValue().toName);
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    m.send(target, "trade.cancelled", "player", player.getName());
                }
                return;
            }
        }

        Request incoming = requests.get(player.getUniqueId());
        if (incoming != null) {
            decline(player, null);
            return;
        }

        m.send(player, "trade.no-request");
    }

    // ---------------------------------------------------------------
    // Offres
    // ---------------------------------------------------------------

    public void setMoney(Player player, double amount) {
        Messages m = plugin.messages();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            m.send(player, "trade.not-in-trade");
            return;
        }
        inputs.remove(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("trade.money-enabled", true)) {
            m.send(player, "trade.money-disabled");
            return;
        }
        Economy economy = economy();
        if (economy == null) {
            m.send(player, "trade.no-money");
            return;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            m.send(player, "trade.money-invalid");
            return;
        }
        if (amount > economy.getBalance(player)) {
            m.send(player, "trade.money-too-much",
                    "balance", Money.format(economy.getBalance(player)), "symbol", symbol());
            return;
        }

        boolean isA = player.getUniqueId().equals(session.a);
        if (isA) {
            session.moneyA = amount;
        } else {
            session.moneyB = amount;
        }
        resetConfirmations(session);
        session.gui.render();

        m.send(player, "trade.money-set", "amount", Money.format(amount), "symbol", symbol());
        Player other = otherPlayer(session, player);
        if (other != null) {
            m.send(other, "trade.money-updated", "player", player.getName(),
                    "amount", Money.format(amount), "symbol", symbol());
        }
        reopen(player, session);
    }

    public void setXp(Player player, int levels) {
        Messages m = plugin.messages();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            m.send(player, "trade.not-in-trade");
            return;
        }
        inputs.remove(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("trade.xp-enabled", true)) {
            m.send(player, "trade.xp-disabled");
            return;
        }
        if (levels < 0) {
            m.send(player, "trade.xp-invalid");
            return;
        }
        if (levels > player.getLevel()) {
            m.send(player, "trade.xp-too-much", "levels", Integer.toString(player.getLevel()));
            return;
        }

        boolean isA = player.getUniqueId().equals(session.a);
        if (isA) {
            session.xpA = levels;
        } else {
            session.xpB = levels;
        }
        resetConfirmations(session);
        session.gui.render();

        m.send(player, "trade.xp-set", "levels", Integer.toString(levels));
        Player other = otherPlayer(session, player);
        if (other != null) {
            m.send(other, "trade.xp-updated", "player", player.getName(),
                    "levels", Integer.toString(levels));
        }
        reopen(player, session);
    }

    // ---------------------------------------------------------------
    // GUI
    // ---------------------------------------------------------------

    public boolean isActive(Session session) {
        return session != null && !session.closing && sessions.get(session.a) == session;
    }

    public boolean isOwnOfferSlot(Session session, UUID viewer, int slot) {
        int[] slots = viewer.equals(session.a) ? SLOTS_A : SLOTS_B;
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    /** Appelé dès qu'un objet de l'offre change : les deux confirmations sont retirées. */
    public void offerChanged(Session session) {
        if (!isActive(session)) {
            return;
        }
        resetConfirmations(session);
    }

    public void render(Session session) {
        if (isActive(session) && session.gui != null) {
            session.gui.render();
        }
    }

    /** Clic sur un bouton du GUI. */
    public void click(Player viewer, Session session, int slot) {
        if (!isActive(session)) {
            return;
        }
        if (slot == SLOT_CANCEL) {
            cancel(session, viewer);
            return;
        }

        boolean isA = viewer.getUniqueId().equals(session.a);
        int confirmSlot = isA ? SLOT_CONFIRM_A : SLOT_CONFIRM_B;
        if (slot == confirmSlot) {
            if (isA) {
                session.confirmA = !session.confirmA;
            } else {
                session.confirmB = !session.confirmB;
            }
            session.gui.render();
            if (session.confirmA && session.confirmB) {
                Bukkit.getScheduler().runTask(plugin, () -> tryExecute(session));
            }
            return;
        }

        if (slot == SLOT_MONEY_A || slot == SLOT_MONEY_B
                || slot == SLOT_XP_A || slot == SLOT_XP_B) {
            boolean ownMoney = isA ? slot == SLOT_MONEY_A : slot == SLOT_MONEY_B;
            boolean ownXp = isA ? slot == SLOT_XP_A : slot == SLOT_XP_B;
            if (ownMoney || ownXp) {
                boolean money = ownMoney;
                Bukkit.getScheduler().runTask(plugin, () -> startInput(viewer, session, money));
            }
        }
    }

    // ---------------------------------------------------------------
    // Saisie au clic (chat)
    // ---------------------------------------------------------------

    public void startInput(Player player, Session session, boolean money) {
        if (!isActive(session)) {
            return;
        }
        Messages m = plugin.messages();
        if (money) {
            if (!plugin.getConfig().getBoolean("trade.money-enabled", true)) {
                m.send(player, "trade.money-disabled");
                return;
            }
            if (economy() == null) {
                m.send(player, "trade.no-money");
                return;
            }
        } else if (!plugin.getConfig().getBoolean("trade.xp-enabled", true)) {
            m.send(player, "trade.xp-disabled");
            return;
        }

        inputs.put(player.getUniqueId(), new Input(session, money,
                System.currentTimeMillis()
                        + Math.max(10, plugin.getConfig().getInt("trade.input-timeout-seconds", 60)) * 1000L));
        m.send(player, money ? "trade.input-money" : "trade.input-xp");
        player.closeInventory();
    }

    public boolean isAwaitingInput(UUID uuid) {
        return inputs.containsKey(uuid);
    }

    /** Traite le message envoyé dans le chat après un clic sur Argent/XP. */
    public boolean handleInput(Player player, String raw) {
        Input input = inputs.get(player.getUniqueId());
        if (input == null) {
            return false;
        }
        String text = raw == null ? "" : raw.trim();
        inputs.remove(player.getUniqueId());

        if (text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
            plugin.messages().send(player, "trade.input-cancelled");
            reopen(player, input.session);
            return true;
        }

        if (!isActive(input.session)) {
            String other = player.getUniqueId().equals(input.session.a)
                    ? input.session.nameB
                    : input.session.nameA;
            plugin.messages().send(player, "trade.cancelled", "player", other);
            return true;
        }

        try {
            if (input.money) {
                setMoney(player, parseAmount(text));
            } else {
                setXp(player, parseLevels(text));
            }
        } catch (NumberFormatException exception) {
            Messages m = plugin.messages();
            m.send(player, input.money ? "trade.money-invalid" : "trade.xp-invalid");
        }
        reopen(player, input.session);
        return true;
    }

    /**
     * Lit "1000", "1 000", "1,5K", "10K", "1M", "1B", "1T".
     * K = milliers, M = millions, B = milliards, T = billions.
     */
    public static double parseAmount(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("\u00a0", "")
                .replace("$", "")
                .replace("€", "");
        if (text.isEmpty()) {
            throw new NumberFormatException("empty");
        }

        double multiplier = 1;
        char last = text.charAt(text.length() - 1);
        switch (last) {
            case 'k' -> {
                multiplier = 1_000d;
                text = text.substring(0, text.length() - 1);
            }
            case 'm' -> {
                multiplier = 1_000_000d;
                text = text.substring(0, text.length() - 1);
            }
            case 'b' -> {
                multiplier = 1_000_000_000d;
                text = text.substring(0, text.length() - 1);
            }
            case 't' -> {
                multiplier = 1_000_000_000_000d;
                text = text.substring(0, text.length() - 1);
            }
            default -> {
            }
        }
        if (text.isEmpty()) {
            throw new NumberFormatException("empty");
        }

        if (text.contains(",") && text.contains(".")) {
            if (text.matches("\\d{1,3}(,\\d{3})+\\.\\d+")) {
                text = text.replace(",", ""); // format anglais : 1,234.56
            } else {
                text = text.replace(".", "").replace(",", "."); // format français : 1.234,56
            }
        } else if (text.contains(",")) {
            if (text.matches("\\d{1,3}(,\\d{3})+")) {
                text = text.replace(",", ""); // 1,000 = 1000
            } else {
                text = text.replace(",", "."); // 1,5 = 1.5
            }
        } else if (text.matches("\\d{1,3}(\\.\\d{3})+")) {
            text = text.replace(".", ""); // 1.000 = 1000
        }

        double value = Double.parseDouble(text) * multiplier;
        if (!Double.isFinite(value) || value < 0) {
            throw new NumberFormatException("invalid");
        }
        return value;
    }

    public static int parseLevels(String raw) {
        double value = parseAmount(raw);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
            throw new NumberFormatException("not an integer");
        }
        return (int) value;
    }

    // ---------------------------------------------------------------
    // Exécution
    // ---------------------------------------------------------------

    private void tryExecute(Session session) {
        if (!isActive(session) || !session.confirmA || !session.confirmB) {
            return;
        }
        Messages m = plugin.messages();
        Player a = Bukkit.getPlayer(session.a);
        Player b = Bukkit.getPlayer(session.b);
        if (a == null || b == null) {
            cancel(session, null);
            return;
        }

        Economy economy = economy();
        if (session.moneyA > 0 || session.moneyB > 0) {
            if (economy == null) {
                fail(session, "trade.no-money", null);
                return;
            }
            if (economy.getBalance(a) < session.moneyA) {
                fail(session, "trade.failed-money", session.nameA);
                return;
            }
            if (economy.getBalance(b) < session.moneyB) {
                fail(session, "trade.failed-money", session.nameB);
                return;
            }
        }
        if (a.getLevel() < session.xpA) {
            fail(session, "trade.failed-xp", session.nameA);
            return;
        }
        if (b.getLevel() < session.xpB) {
            fail(session, "trade.failed-xp", session.nameB);
            return;
        }

        // Retire l'argent avant tout transfert, avec remboursement si le second échoue.
        boolean paidA = false;
        if (session.moneyA > 0) {
            EconomyResponse response = economy.withdrawPlayer(a, session.moneyA);
            if (!response.transactionSuccess()) {
                fail(session, "trade.failed", null);
                return;
            }
            paidA = true;
        }
        if (session.moneyB > 0) {
            EconomyResponse response = economy.withdrawPlayer(b, session.moneyB);
            if (!response.transactionSuccess()) {
                if (paidA) {
                    economy.depositPlayer(a, session.moneyA);
                }
                fail(session, "trade.failed", null);
                return;
            }
        }

        // Retire l'XP (en niveaux).
        if (session.xpA > 0) {
            a.setLevel(a.getLevel() - session.xpA);
        }
        if (session.xpB > 0) {
            b.setLevel(b.getLevel() - session.xpB);
        }

        // Récupère les objets puis ferme les inventaires.
        List<ItemStack> itemsA = take(session.inventory, SLOTS_A);
        List<ItemStack> itemsB = take(session.inventory, SLOTS_B);
        close(session, a, b);

        // Verse l'argent et l'XP au partenaire.
        if (session.moneyB > 0) {
            economy.depositPlayer(a, session.moneyB);
        }
        if (session.moneyA > 0) {
            economy.depositPlayer(b, session.moneyA);
        }
        if (session.xpB > 0) {
            a.setLevel(a.getLevel() + session.xpB);
        }
        if (session.xpA > 0) {
            b.setLevel(b.getLevel() + session.xpA);
        }

        give(a, itemsB);
        give(b, itemsA);

        m.send(a, "trade.success", "player", b.getName());
        m.send(b, "trade.success", "player", a.getName());
    }

    private void fail(Session session, String key, String playerName) {
        if (!isActive(session)) {
            return;
        }
        resetConfirmations(session);
        session.gui.render();
        Messages m = plugin.messages();
        Player a = Bukkit.getPlayer(session.a);
        Player b = Bukkit.getPlayer(session.b);
        if (playerName != null) {
            if (a != null) {
                m.send(a, key, "player", playerName);
            }
            if (b != null) {
                m.send(b, key, "player", playerName);
            }
        } else {
            if (a != null) {
                m.send(a, key);
            }
            if (b != null) {
                m.send(b, key);
            }
        }
    }

    /** Annule un échange et rend les objets proposés à leur propriétaire. */
    public void cancel(Session session, Player closer) {
        if (session == null || session.closing || !isActive(session)) {
            return;
        }
        session.closing = true;
        sessions.remove(session.a);
        sessions.remove(session.b);
        inputs.remove(session.a);
        inputs.remove(session.b);

        List<ItemStack> itemsA = take(session.inventory, SLOTS_A);
        List<ItemStack> itemsB = take(session.inventory, SLOTS_B);

        Player a = Bukkit.getPlayer(session.a);
        Player b = Bukkit.getPlayer(session.b);
        String nameA = a != null ? a.getName() : session.nameA;
        String nameB = b != null ? b.getName() : session.nameB;

        if (a != null) {
            if (closer == null || !closer.getUniqueId().equals(a.getUniqueId())) {
                a.closeInventory();
            }
            give(a, itemsA);
            plugin.messages().send(a, "trade.cancelled", "player", nameB);
        }
        if (b != null) {
            if (closer == null || !closer.getUniqueId().equals(b.getUniqueId())) {
                b.closeInventory();
            }
            give(b, itemsB);
            plugin.messages().send(b, "trade.cancelled", "player", nameA);
        }
    }

    /** Déconnexion : annule l'échange et nettoie les demandes. */
    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cooldowns.remove(uuid);

        Session session = sessions.get(uuid);
        if (session != null) {
            cancel(session, player);
        }
        inputs.remove(uuid);

        Request incoming = requests.remove(uuid);
        if (incoming != null) {
            Player from = Bukkit.getPlayer(incoming.from);
            if (from != null) {
                plugin.messages().send(from, "trade.target-offline", "player", player.getName());
            }
        }

        Iterator<Map.Entry<UUID, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Request> entry = iterator.next();
            if (entry.getValue().from.equals(uuid)) {
                iterator.remove();
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    plugin.messages().send(target, "trade.target-offline", "player", player.getName());
                }
            }
        }
    }

    public void shutdown() {
        ticker.cancel();
        for (Session session : new ArrayList<>(sessions.values())) {
            cancel(session, null);
        }
        requests.clear();
        cooldowns.clear();
        inputs.clear();
    }

    // ---------------------------------------------------------------
    // Ticker : expiration automatique des demandes
    // ---------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Request> entry = iterator.next();
            Request request = entry.getValue();
            if (now < request.expiresAt) {
                continue;
            }
            iterator.remove();
            Player target = Bukkit.getPlayer(entry.getKey());
            Player from = Bukkit.getPlayer(request.from);
            if (target != null) {
                plugin.messages().send(target, "trade.request-expired-target", "player", request.fromName);
            }
            if (from != null) {
                plugin.messages().send(from, "trade.request-expired-sender", "player", request.toName);
            }
        }

        Iterator<Map.Entry<UUID, Input>> inputIterator = inputs.entrySet().iterator();
        while (inputIterator.hasNext()) {
            Map.Entry<UUID, Input> entry = inputIterator.next();
            Input input = entry.getValue();
            if (now < input.expiresAt) {
                continue;
            }
            inputIterator.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                plugin.messages().send(player, "trade.input-expired");
                reopen(player, input.session);
            }
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private void close(Session session, Player a, Player b) {
        session.closing = true;
        sessions.remove(session.a);
        sessions.remove(session.b);
        inputs.remove(session.a);
        inputs.remove(session.b);
        a.closeInventory();
        b.closeInventory();
    }

    /** Rouvre le GUI si le joueur l'avait fermé pour saisir une valeur dans le chat. */
    private void reopen(Player player, Session session) {
        if (isActive(session) && player.getOpenInventory().getTopInventory() != session.inventory) {
            player.openInventory(session.inventory);
        }
    }

    private void resetConfirmations(Session session) {
        session.confirmA = false;
        session.confirmB = false;
    }

    private Player otherPlayer(Session session, Player player) {
        UUID other = player.getUniqueId().equals(session.a) ? session.b : session.a;
        return Bukkit.getPlayer(other);
    }

    private List<ItemStack> take(Inventory inventory, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
            inventory.setItem(slot, null);
        }
        return items;
    }

    private void give(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            for (ItemStack item : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            plugin.messages().send(player, "trade.inventory-full");
        }
    }

    public Session sessionOf(Player player) {
        return sessions.get(player.getUniqueId());
    }

    private String symbol() {
        return plugin.getConfig().getString("currency-symbol", "$");
    }

    /** Solde Vault du joueur (0 si l'économie n'est pas disponible). */
    public double balance(Player player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    public boolean economyAvailable() {
        return economy() != null;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    // ---------------------------------------------------------------
    // Structures
    // ---------------------------------------------------------------

    private static final class Input {
        final Session session;
        final boolean money;
        final long expiresAt;

        Input(Session session, boolean money, long expiresAt) {
            this.session = session;
            this.money = money;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Request {
        final UUID from;
        final String fromName;
        final String toName;
        final long expiresAt;

        Request(UUID from, String fromName, String toName, long expiresAt) {
            this.from = from;
            this.fromName = fromName;
            this.toName = toName;
            this.expiresAt = expiresAt;
        }
    }

    public static final class Session {
        public final UUID a;
        public final String nameA;
        public final UUID b;
        public final String nameB;
        public double moneyA;
        public double moneyB;
        public int xpA;
        public int xpB;
        public boolean confirmA;
        public boolean confirmB;
        public boolean closing;
        public Inventory inventory;
        public TradeGui gui;

        Session(UUID a, String nameA, UUID b, String nameB) {
            this.a = a;
            this.nameA = nameA;
            this.b = b;
            this.nameB = nameB;
        }
    }
}
