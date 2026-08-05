package fr.timeo.lumidiscord.minecraft.listeners;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.TeamManager;
import fr.timeo.lumidiscord.minecraft.team.TeamData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeamListener implements Listener {
    private final LumiDiscord plugin;
    private final TeamManager teamManager;

    public TeamListener(LumiDiscord plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster && event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            // CORRECTION : Passer le joueur ET le type de l'entité
            teamManager.addMobKillXp(killer, event.getEntityType());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team != null) teamManager.applyVisualsForMember(team, player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team != null) teamManager.clearVisualForPlayer(team, player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getWarManager() != null) plugin.getWarManager().handlePlayerDeath(event.getEntity());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(sender.getUniqueId());
        if (team == null) return;
        String tag = teamManager.chatTag(team);
        String nameColor = team.getColor() == null ? ChatColor.WHITE.toString() : team.getColor().toString();
        event.setFormat(tag + ChatColor.RESET + " " + nameColor + "%1$s" + ChatColor.GRAY + " » " + ChatColor.WHITE + "%2$s");
    }
}