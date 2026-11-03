package fr.smp.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Récupère la saisie du montant / des niveaux après un clic sur Argent ou XP dans /trade. */
public final class TradeInputListener implements Listener {

    private final SmpCore plugin;

    public TradeInputListener(SmpCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.trade().isAwaitingInput(player.getUniqueId())) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.trade().handleInput(player, text));
    }
}
