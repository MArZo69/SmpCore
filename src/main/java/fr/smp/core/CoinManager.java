package fr.smp.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solde de LureCoins par joueur.
 * Stockage local dans plugins/SmpCore/coins.yml : pas besoin de SQL.
 * Les LureCoins sont ajoutés uniquement par Tebex (commande console).
 */
public final class CoinManager {

    private final SmpCore plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    public CoinManager(SmpCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "coins.yml");
    }

    public void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("coins");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long amount = section.getLong(key, 0L);
                if (amount > 0) {
                    balances.put(uuid, amount);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID invalide dans coins.yml : " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            if (entry.getValue() > 0) {
                config.set("coins." + entry.getKey(), entry.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder coins.yml : " + e.getMessage());
        }
    }

    public long get(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public boolean has(UUID uuid, long amount) {
        return amount <= 0 || get(uuid) >= amount;
    }

    public void add(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        long current = get(uuid);
        long next = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        balances.put(uuid, next);
        save();
    }

    public boolean remove(UUID uuid, long amount) {
        if (amount <= 0) {
            return false;
        }
        long current = get(uuid);
        if (current < amount) {
            return false;
        }
        long next = current - amount;
        if (next == 0) {
            balances.remove(uuid);
        } else {
            balances.put(uuid, next);
        }
        save();
        return true;
    }

    public void set(UUID uuid, long amount) {
        if (amount <= 0) {
            balances.remove(uuid);
        } else {
            balances.put(uuid, amount);
        }
        save();
    }

    public String format(long amount) {
        return Long.toString(amount);
    }

    public String currencyName() {
        return plugin.getConfig().getString("lurecoin.name", "LureCoins");
    }
}
