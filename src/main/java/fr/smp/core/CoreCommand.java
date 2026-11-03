package fr.smp.core;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rankup, /boutique, /coin et /smpcore. */
public final class CoreCommand implements CommandExecutor, TabCompleter {

    private final SmpCore plugin;

    public CoreCommand(SmpCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();

        if (command.getName().equalsIgnoreCase("smpcore")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAll();
                messages.send(sender, "reloaded");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
                plugin.rankup().grant(sender, args[1], args[2]);
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("coins")) {
                handleAdminCoins(sender, args, messages);
            } else {
                sender.sendMessage("/smpcore reload");
                sender.sendMessage("/smpcore grant <joueur> <grade>");
                sender.sendMessage("/smpcore coins <add|remove|set> <joueur> <montant>");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("coin")) {
            return handleCoinCommand(sender, args, messages);
        }

        if (command.getName().equalsIgnoreCase("boutique")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
            } else if (args.length == 0) {
                new BoutiqueGui(plugin, player).open();
            } else {
                messages.send(sender, "boutique.usage");
            }
            return true;
        }

        // /rankup
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            new RankupGui(plugin, player).open();
        } else if (args[0].equalsIgnoreCase("confirm")) {
            plugin.rankup().confirm(player);
        } else {
            messages.send(player, "usage");
        }
        return true;
    }

    /** /coin et /coins : solde, ou /coin give <joueur> <montant> pour Tebex. */
    private boolean handleCoinCommand(CommandSender sender, String[] args, Messages messages) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("balance"))) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return true;
            }
            messages.send(player, "coins.balance",
                    "amount", plugin.coins().format(plugin.coins().get(player.getUniqueId())),
                    "currency", plugin.coins().currencyName());
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length != 3) {
                messages.send(sender, "coins.usage");
                return true;
            }
            if (sender instanceof Player && !sender.hasPermission("smpcore.admin")) {
                messages.send(sender, "coins.no-permission");
                return true;
            }

            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                messages.send(sender, "coins.invalid-amount");
                return true;
            }
            if (amount <= 0) {
                messages.send(sender, "coins.invalid-amount");
                return true;
            }

            OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                messages.send(sender, "coins.unknown-player", "player", args[1]);
                return true;
            }

            plugin.coins().add(target.getUniqueId(), amount);
            String targetName = target.getName() == null ? args[1] : target.getName();
            String currency = plugin.coins().currencyName();
            String formatted = plugin.coins().format(amount);

            messages.send(sender, "coins.given",
                    "amount", formatted, "currency", currency, "player", targetName);
            messages.broadcast("coins.thanks",
                    "amount", formatted, "currency", currency, "player", targetName);
            return true;
        }

        messages.send(sender, "coins.usage");
        return true;
    }

    /** /smpcore coins add|remove|set <joueur> <montant> (console / admin). */
    private void handleAdminCoins(CommandSender sender, String[] args, Messages messages) {
        if (args.length != 4) {
            messages.send(sender, "coins.usage-admin");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("add") && !action.equals("remove") && !action.equals("set")) {
            messages.send(sender, "coins.usage-admin");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            messages.send(sender, "coins.invalid-amount");
            return;
        }
        if (amount < 0 || (!action.equals("set") && amount == 0)) {
            messages.send(sender, "coins.invalid-amount");
            return;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            messages.send(sender, "coins.unknown-player", "player", args[2]);
            return;
        }

        String targetName = target.getName() == null ? args[2] : target.getName();
        String currency = plugin.coins().currencyName();
        String formatted = plugin.coins().format(amount);

        switch (action) {
            case "add" -> {
                plugin.coins().add(target.getUniqueId(), amount);
                messages.send(sender, "coins.added",
                        "amount", formatted, "currency", currency, "player", targetName);
                Player online = target.getPlayer();
                if (online != null) {
                    messages.send(online, "coins.received",
                            "amount", formatted, "currency", currency);
                }
            }
            case "remove" -> {
                if (!plugin.coins().remove(target.getUniqueId(), amount)) {
                    messages.send(sender, "coins.admin-not-enough",
                            "player", targetName, "currency", currency);
                    return;
                }
                messages.send(sender, "coins.removed",
                        "amount", formatted, "currency", currency, "player", targetName);
            }
            case "set" -> {
                plugin.coins().set(target.getUniqueId(), amount);
                messages.send(sender, "coins.set",
                        "amount", formatted, "currency", currency, "player", targetName);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("coin")) {
            if (args.length == 1) {
                return filter(List.of("balance", "give"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return filter(onlineNames(), args[1]);
            }
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("rankup") && args.length == 1) {
            return filter(List.of("confirm"), args[0]);
        }
        if (command.getName().equalsIgnoreCase("smpcore")) {
            if (args.length == 1) {
                return filter(List.of("reload", "grant", "coins"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("grant")) {
                return filter(onlineNames(), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
                return filter(plugin.rankup().stepIds(), args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("coins")) {
                return filter(List.of("add", "remove", "set"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("coins")) {
                return filter(onlineNames(), args[2]);
            }
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
