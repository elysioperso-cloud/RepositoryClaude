package fr.timeo.lumidiscord.minecraft.listeners;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.TeamRole;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamChatListener implements Listener {

    private final LumiDiscord plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private static final Map<UUID, TeamRole> renamingPlayers = new ConcurrentHashMap<>();

    public TeamChatListener(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    public static void startRenaming(Player player, TeamRole role) {
        renamingPlayers.put(player.getUniqueId(), role);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!renamingPlayers.containsKey(player.getUniqueId())) return;

        event.setCancelled(true); // Bloquer le message public
        TeamRole role = renamingPlayers.remove(player.getUniqueId());
        String newName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (newName.length() < 2 || newName.length() > 16) {
            player.sendMessage(mm.deserialize("<red>Le nom doit faire entre 2 et 16 caractères !"));
            return;
        }

        role.setName(newName);
        plugin.getTeamManager().updateRole(role);
        
        player.sendMessage(mm.deserialize("<green>Le rôle a été renommé en : <aqua>" + newName));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }
}
