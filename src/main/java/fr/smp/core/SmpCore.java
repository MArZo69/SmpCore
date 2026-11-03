package fr.smp.core;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Point d'entrée. Chaque module (rankup, plus tard sell/worth...) a sa propre classe. */
public final class SmpCore extends JavaPlugin {

    private Messages messages;
    private PendingStore pending;
    private RankupManager rankup;
    private CoinManager coins;
    private BoutiqueManager boutique;
    private TradeManager trade;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.load();

        pending = new PendingStore(this);
        pending.load();

        rankup = new RankupManager(this);
        rankup.load();

        coins = new CoinManager(this);
        coins.load();

        boutique = new BoutiqueManager(this);
        boutique.load();

        trade = new TradeManager(this);

        CoreCommand command = new CoreCommand(this);
        register("rankup", command, command);
        register("smpcore", command, command);
        register("coin", command, command);
        register("boutique", command, command);

        TradeCommand tradeCommand = new TradeCommand(this);
        register("trade", tradeCommand, tradeCommand);

        getServer().getPluginManager().registerEvents(new RankupGuiListener(), this);
        getServer().getPluginManager().registerEvents(new BoutiqueGuiListener(), this);
        getServer().getPluginManager().registerEvents(new TradeGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeInputListener(this), this);

        // Placeholders PlaceholderAPI (%smpcore_lurecoins%, etc.)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmpCoreExpansion(this).register();
            getLogger().info("Expansion PlaceholderAPI enregistrée (smpcore).");
        }

        getLogger().info("SmpCore active - module rankup : " + rankup.stepCount()
                + " grades, boutique LureCoin et /trade actifs.");
    }

    @Override
    public void onDisable() {
        if (trade != null) {
            trade.shutdown();
        }
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Commande absente de plugin.yml : " + name);
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(completer);
    }

    /** Recharge config.yml, messages.yml et les prix. */
    public void reloadAll() {
        reloadConfig();
        messages.load();
        pending.load();
        rankup.load();
        coins.load();
        boutique.load();
    }

    public Messages messages() {
        return messages;
    }

    public PendingStore pending() {
        return pending;
    }

    public RankupManager rankup() {
        return rankup;
    }

    public CoinManager coins() {
        return coins;
    }

    public BoutiqueManager boutique() {
        return boutique;
    }

    public TradeManager trade() {
        return trade;
    }
}