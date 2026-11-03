package fr.smp.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Expose les LureCoins de SmpCore à PlaceholderAPI (donc à TAB, scoreboards, etc.).
 *
 * Placeholders :
 *   %smpcore_lurecoins%            -> nombre brut          (ex : 15000)
 *   %smpcore_lurecoins_formatted%  -> nombre formaté       (via CoinManager#format)
 *   %smpcore_currency%             -> nom de la monnaie    (via CoinManager#currencyName)
 */
public class SmpCoreExpansion extends PlaceholderExpansion {

    private final SmpCore plugin;

    public SmpCoreExpansion(SmpCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "smpcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LureSMP";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    // Reste enregistrée après /papi reload
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        CoinManager coins = plugin.coins();

        if (params.equalsIgnoreCase("currency")) {
            return coins.currencyName();
        }
        if (player == null) {
            return "";
        }

        long balance = coins.get(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "lurecoins" -> String.valueOf(balance);
            case "lurecoins_formatted" -> coins.format(balance);
            default -> null; // placeholder inconnu
        };
    }
}

/* ------------------------------------------------------------------
   À FAIRE AUSSI dans le projet :

   1) SmpCore#onEnable, à la fin :
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmpCoreExpansion(this).register();
        }

   2) plugin.yml : ajouter PlaceholderAPI aux softdepend
        softdepend: [Essentials, PlaceholderAPI]

   3) Dépendance de build (scope provided) :
        me.clip:placeholderapi:2.11.6  (repo : https://repo.helpch.at/releases/)

   4) Recompiler le jar de SmpCore, le remplacer sur le serveur, redémarrer.

   Test : /tab parse <pseudo> %smpcore_lurecoins%
------------------------------------------------------------------- */