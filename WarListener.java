package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfkManager {

    private final LumiDiscord plugin;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    
    // États : 0 = Actif, 1 = AFK Auto, 2 = AFK Manuel
    private final Map<UUID, Integer> afkState = new HashMap<>(); 

    public AfkManager(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    public void markActive(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        
        // Si le joueur était en AFK AUTO, on le repasse en ACTIF
        if (afkState.getOrDefault(uuid, 0) == 1) {
            afkState.put(uuid, 0);
            player.sendMessage(ChatColor.GREEN + "Vous n'êtes plus en mode AFK (détection automatique).");
        }
    }

    public boolean isAfk(Player player) {
        int state = afkState.getOrDefault(player.getUniqueId(), 0);
        return state == 1 || state == 2;
    }

    public boolean isAfkManual(Player player) {
        return afkState.getOrDefault(player.getUniqueId(), 0) == 2;
    }

    public void toggleAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (isAfkManual(player)) {
            afkState.put(uuid, 0);
            player.sendMessage(ChatColor.GREEN + "Vous n'êtes plus en mode AFK.");
            player.removePotionEffect(PotionEffectType.SATURATION);
        } else {
            afkState.put(uuid, 2);
            player.sendMessage(ChatColor.YELLOW + "Vous êtes maintenant en mode AFK. Ne bougez pas et ne prenez pas de dégâts.");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        }
    }

    public void forceDeactivateAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (isAfkManual(player)) {
            afkState.put(uuid, 0);
            player.sendMessage(ChatColor.RED + "Votre mode AFK a été désactivé.");
            player.removePotionEffect(PotionEffectType.SATURATION);
        }
    }

    public void tick() {
        long threshold = plugin.getPluginConfig().getLong("afk.detection-threshold-seconds") * 1000L;
        if (threshold <= 0) threshold = 300000L;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            int currentState = afkState.getOrDefault(uuid, 0);
            
            if (currentState == 2) continue; // On ne touche pas à l'AFK manuel

            Long seen = lastActivity.get(uuid);
            if (seen == null) {
                lastActivity.put(uuid, System.currentTimeMillis());
                continue;
            }

            if (System.currentTimeMillis() - seen > threshold) {
                if (currentState == 0) {
                    afkState.put(uuid, 1);
                    player.sendMessage(ChatColor.YELLOW + "Vous êtes maintenant en mode AFK (détection automatique).");
                }
            }
        }
    }
}