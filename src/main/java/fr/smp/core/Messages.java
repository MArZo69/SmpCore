package fr.smp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Messages MiniMessage lus depuis messages.yml. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SmpCore plugin;
    private YamlConfiguration config = new YamlConfiguration();
    /** Valeurs du messages.yml fourni dans le jar : utilisées pour les clés absentes du fichier du serveur. */
    private YamlConfiguration defaults = new YamlConfiguration();

    public Messages(SmpCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        loadDefaults();

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void loadDefaults() {
        InputStream stream = plugin.getResource("messages.yml");
        if (stream == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de lire le messages.yml par défaut : " + e.getMessage());
        }
    }

    /**
     * Message complet avec le préfixe du serveur.
     *
     * @param pairs suite clé, valeur, clé, valeur... : {clé} est remplacé par valeur
     */
    public Component get(String key, String... pairs) {
        String prefix = config.getString("prefix");
        if (prefix == null) {
            prefix = defaults.getString("prefix", "");
        }
        return parse(prefix + rawString(key, pairs));
    }

    /**
     * Message sans le préfixe du serveur (pour les noms/lore d'items de menu).
     *
     * @param pairs suite clé, valeur, clé, valeur... : {clé} est remplacé par valeur
     */
    public Component raw(String key, String... pairs) {
        return parse(rawString(key, pairs));
    }

    /** Plusieurs lignes de lore sans préfixe (liste YAML). */
    public List<Component> rawList(String key) {
        List<String> lines = config.getStringList(key);
        if (lines.isEmpty()) {
            lines = defaults.getStringList(key);
        }
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                result.add(parse(line));
            }
        }
        return result;
    }

    /** Parse directement un texte MiniMessage (utilisé pour les avantages écrits dans config.yml). */
    public Component component(String text) {
        return parse(text);
    }

    private String rawString(String key, String... pairs) {
        String raw = config.getString(key);
        if (raw == null) {
            raw = defaults.getString(key, "<red>Message manquant : " + key);
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        // Compatibilité avec les anciens fichiers qui utilisaient des pseudo-placeholders.
        String next = pair(pairs, "next");
        if (next != null) {
            raw = raw.replace("<Grade.rankup>", next)
                    .replace("<grade.rankup>", next)
                    .replace("<grade>", next)
                    .replace("<next>", next);
        }
        String player = pair(pairs, "player");
        if (player != null) {
            raw = raw.replace("<player>", player);
        }
        return raw;
    }

    private String pair(String[] pairs, String key) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i].equals(key)) {
                return pairs[i + 1];
            }
        }
        return null;
    }

    private Component parse(String text) {
        try {
            return MM.deserialize(text);
        } catch (RuntimeException e) {
            return Component.text(text);
        }
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(get(key, pairs));
    }

    public void broadcast(String key, String... pairs) {
        plugin.getServer().broadcast(get(key, pairs));
    }
}
