package fr.smp.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Achats boutique en attente : le grade n'est appliqué qu'au prochain /rankup. */
public final class PendingStore {

    private final SmpCore plugin;
    private final File file;
    private final Map<UUID, Set<String>> pending = new ConcurrentHashMap<>();

    public PendingStore(SmpCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
    }

    public void load() {
        pending.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("pending");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Set<String> grades = ConcurrentHashMap.newKeySet();
                for (String id : root.getStringList(key)) {
                    if (id != null && !id.isBlank()) {
                        grades.add(id.toLowerCase(Locale.ROOT));
                    }
                }
                if (!grades.isEmpty()) {
                    pending.put(uuid, grades);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID invalide dans pending.yml : " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : pending.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                config.set("pending." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder pending.yml : " + e.getMessage());
        }
    }

    /** Copie des grades achetés en attente pour ce joueur. */
    public Set<String> get(UUID uuid) {
        Set<String> grades = pending.get(uuid);
        return grades == null ? Set.of() : Set.copyOf(grades);
    }

    public boolean has(UUID uuid, String gradeId) {
        Set<String> grades = pending.get(uuid);
        return grades != null && grades.contains(gradeId.toLowerCase(Locale.ROOT));
    }

    /** @return false si ce grade était déjà en attente. */
    public boolean add(UUID uuid, String gradeId) {
        Set<String> grades = pending.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        boolean added = grades.add(gradeId.toLowerCase(Locale.ROOT));
        if (added) {
            save();
        }
        return added;
    }

    public boolean remove(UUID uuid, String gradeId) {
        Set<String> grades = pending.get(uuid);
        if (grades == null) {
            return false;
        }
        boolean removed = grades.remove(gradeId.toLowerCase(Locale.ROOT));
        if (grades.isEmpty()) {
            pending.remove(uuid);
        }
        if (removed) {
            save();
        }
        return removed;
    }
}
