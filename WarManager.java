package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DayCountManager {

    private final LumiDiscord plugin;
    private final Map<UUID, BukkitTask> activeDisplays = new ConcurrentHashMap<>();

    public DayCountManager(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    public void startDisplay(Player player) {
        if (activeDisplays.containsKey(player.getUniqueId())) {
            stopDisplay(player); // Arrête l'ancienne tâche si elle existe
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopDisplay(player);
                return;
            }
            long day = getDayCount();
            player.sendActionBar(ChatColor.WHITE + "Jour : " + day);
        }, 0L, 20L); // Met à jour toutes les secondes (20 ticks)

        activeDisplays.put(player.getUniqueId(), task);
    }

    public void stopDisplay(Player player) {
        BukkitTask task = activeDisplays.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendActionBar(""); // Efface l'action bar
        }
    }

    public long getDayCount() {
        // Prend le premier monde chargé (généralement l'overworld)
        // fullTime est en ticks, 24000 ticks = 1 jour
        if (Bukkit.getWorlds().isEmpty()) {
            return 0;
        }
        return Bukkit.getWorlds().get(0).getFullTime() / 24000L;
    }

    public void onPlayerQuit(Player player) {
        stopDisplay(player);
    }
}