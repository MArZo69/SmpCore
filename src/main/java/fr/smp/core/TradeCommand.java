package fr.smp.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /trade : demande, acceptation, refus, annulation, argent et XP. */
public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final SmpCore plugin;

    public TradeCommand(SmpCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("smpcore.trade")) {
            messages.send(player, "no-permission");
            return true;
        }

        TradeManager trade = plugin.trade();
        if (args.length == 0) {
            messages.send(player, "trade.usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accepter", "accept" -> {
                trade.accept(player, args.length >= 2 ? args[1] : null);
                return true;
            }
            case "refuser", "refuse", "decline" -> {
                trade.decline(player, args.length >= 2 ? args[1] : null);
                return true;
            }
            case "annuler", "cancel" -> {
                trade.cancelCommand(player);
                return true;
            }
            case "argent", "money" -> {
                if (args.length < 2) {
                    messages.send(player, "trade.usage");
                    return true;
                }
                try {
                    trade.setMoney(player, TradeManager.parseAmount(args[1]));
                } catch (NumberFormatException exception) {
                    messages.send(player, "trade.money-invalid");
                }
                return true;
            }
            case "xp", "experience" -> {
                if (args.length < 2) {
                    messages.send(player, "trade.usage");
                    return true;
                }
                try {
                    trade.setXp(player, TradeManager.parseLevels(args[1]));
                } catch (NumberFormatException exception) {
                    messages.send(player, "trade.xp-invalid");
                }
                return true;
            }
            case "aide", "help" -> {
                messages.send(player, "trade.usage");
                return true;
            }
            default -> {
                if (args.length == 1) {
                    trade.request(player, args[0]);
                } else {
                    messages.send(player, "trade.usage");
                }
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("accepter", "refuser", "annuler", "argent", "xp", "aide")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player player && online.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(online.getName());
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("accepter") || sub.equals("accept")
                    || sub.equals("refuser") || sub.equals("refuse") || sub.equals("decline")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(online.getName());
                    }
                }
            }
        }
        return result;
    }
}
