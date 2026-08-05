package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.discord.DiscordBotService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RewardManager {

    private final LumiDiscord plugin;
    private final LevelManager levelManager;
    private final Map<UUID, Set<Integer>> claimedLevelsByPlayer = new HashMap<>();
    private final List<Integer> rewardLevels = new ArrayList<>();
    private final Map<Integer, RewardDefinition> rewardDefinitions = new HashMap<>();
    private final Map<UUID, Set<Integer>> pendingOfflineRewardsByPlayer = new HashMap<>();
    private final Set<UUID> pendingOfflineLevelUps = new HashSet<>();

    private static class RewardDefinition {
        private final String name;
        private final List<String> commands;
        private RewardDefinition(String name, List<String> commands) { this.name = name; this.commands = commands; }
    }

    public RewardManager(LumiDiscord plugin, LevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
        loadConfiguredRewards();
    }

    public void checkAndGrantForLevel(UUID uuid, int oldLevel, int newLevel) {
        if (oldLevel >= newLevel) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performLevelCheck(uuid, oldLevel, newLevel));
    }

    private void performLevelCheck(UUID uuid, int oldLevel, int newLevel) {
        Set<Integer> claimed = claimedLevelsByPlayer.computeIfAbsent(uuid, k -> new HashSet<>());
        String username = loadPlayerUsername(uuid);
        String discordId = loadPlayerDiscordId(uuid); // S'assurer que l'ID Discord est bien chargé

        for (int level = oldLevel + 1; level <= newLevel; level++) {
            final int currentLvl = level;
            String rewardName = getConfiguredRewardName(currentLvl);
            
            // ANNONCE DISCORD ET TCHAT
            announceLevelUp(username, discordId, currentLvl, rewardName, uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    if (rewardDefinitions.containsKey(currentLvl) && !claimed.contains(currentLvl)) {
                        claimed.add(currentLvl);
                        persistClaim(uuid, currentLvl);
                        giveReward(online, currentLvl);
                    }
                } else {
                    pendingOfflineRewardsByPlayer.computeIfAbsent(uuid, k -> new HashSet<>()).add(currentLvl);
                    pendingOfflineLevelUps.add(uuid);
                }
            });
        }
    }

    private String loadPlayerDiscordId(UUID uuid) {
        try (Connection conn = plugin.getDatabaseService().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT discord_id FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("discord_id");
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private void announceLevelUp(String username, String discordId, int level, String rewardName, UUID uuid) {
        String name = (username != null) ? username : "Joueur";
        
        // Message en jeu
        String msg = "§6[LumiDiscord] §e" + name + " §7est passé au niveau §6" + level;
        if (rewardName != null) msg += " §7et a gagné §a" + rewardName + " §7!";
        Bukkit.broadcastMessage(msg);

        // Envoi au bot Discord
        DiscordBotService discord = plugin.getDiscordBotService();
        if (discord != null) {
            discord.announceLevelUp(discordId, name, level, rewardName, uuid);
        }
    }

    // --- LE RESTE DES MÉTHODES ---
    public void removeClaimsForLevelsAbove(UUID uuid, int hvl) { claimedLevelsByPlayer.getOrDefault(uuid, new HashSet<>()).removeIf(l -> l > hvl); Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { try (Connection c = plugin.getDatabaseService().getConnection(); PreparedStatement s = c.prepareStatement("DELETE FROM claimed_rewards WHERE uuid = ? AND level > ?")) { s.setString(1, uuid.toString()); s.setInt(2, hvl); s.executeUpdate(); } catch (SQLException ignored) {} }); }
    public void loadClaimedRewards(UUID uuid, Runnable callback) { Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { Set<Integer> claims = loadClaimedLevelsFromDatabase(uuid); Bukkit.getScheduler().runTask(plugin, () -> { claimedLevelsByPlayer.put(uuid, claims); if (callback != null) callback.run(); }); }); }
    private Set<Integer> loadClaimedLevelsFromDatabase(UUID uuid) { Set<Integer> claims = new HashSet<>(); try (Connection c = plugin.getDatabaseService().getConnection(); PreparedStatement s = c.prepareStatement("SELECT level FROM claimed_rewards WHERE uuid = ?")) { s.setString(1, uuid.toString()); try (ResultSet r = s.executeQuery()) { while (r.next()) claims.add(r.getInt("level")); } } catch (SQLException e) { return new HashSet<>(); } return claims; }
    public void grantMissingRewardsOnLogin(Player player, int cl) { UUID uuid = player.getUniqueId(); Set<Integer> claimed = claimedLevelsByPlayer.computeIfAbsent(uuid, k -> new HashSet<>()); for (int rl : rewardLevels) { if (rl <= cl && !claimed.contains(rl)) { claimed.add(rl); persistClaim(uuid, rl); giveReward(player, rl); } } }
    public boolean consumePendingOfflineLevelUp(UUID uuid) { return pendingOfflineLevelUps.remove(uuid); }
    private void loadConfiguredRewards() { ConfigurationSection s = plugin.getConfig().getConfigurationSection("rewards"); if (s == null) return; for (String l : s.getKeys(false)) { int lvl = Integer.parseInt(l); String n = s.getString(l + ".name"); List<String> cmd = s.isList(l + ".commands") ? s.getStringList(l + ".commands") : Collections.singletonList(s.getString(l + ".command")); rewardLevels.add(lvl); rewardDefinitions.put(lvl, new RewardDefinition(n, cmd)); } Collections.sort(rewardLevels); }
    private String loadPlayerUsername(UUID uuid) { try (Connection c = plugin.getDatabaseService().getConnection(); PreparedStatement s = c.prepareStatement("SELECT username FROM players WHERE uuid = ?")) { s.setString(1, uuid.toString()); try (ResultSet r = s.executeQuery()) { if (r.next()) return r.getString("username"); } } catch (SQLException ignored) {} return Bukkit.getOfflinePlayer(uuid).getName(); }
    private void persistClaim(UUID uuid, int rl) { Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { try (Connection c = plugin.getDatabaseService().getConnection()) { try (PreparedStatement s = c.prepareStatement("INSERT IGNORE INTO claimed_rewards (uuid, level) VALUES (?, ?)")) { s.setString(1, uuid.toString()); s.setInt(2, rl); s.executeUpdate(); } } catch (SQLException ignored) {} }); }
    private void giveReward(Player p, int rl) { RewardDefinition d = rewardDefinitions.get(rl); if (d == null) return; for (String c : d.commands) { String rc = c.replace("%player%", p.getName()); if (rc.equalsIgnoreCase("HOME")) { p.addAttachment(plugin, "lumidiscord.home", true); continue; } Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rc); } }
    private String getConfiguredRewardName(int rl) { RewardDefinition d = rewardDefinitions.get(rl); return (d != null) ? d.name : null; }
}