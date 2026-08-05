package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreakManager {

    private final LumiDiscord plugin;
    // Cache du streak en mémoire pour éviter les requêtes SQL répétées
    private final Map<UUID, Integer> streakCache = new ConcurrentHashMap<>();

    public static final int MAX_STREAK = 10;

    public StreakManager(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    /**
     * Appelé à la connexion du joueur.
     * Met à jour le streak en BDD et notifie le joueur.
     */
    public void handleLogin(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseService().getConnection()) {
                LocalDate today = LocalDate.now();

                int currentStreak;
                LocalDate lastLoginDate;
                boolean isLinked = false;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT login_streak, last_login_date, discord_id FROM players WHERE uuid = ?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) return;
                        currentStreak = rs.getInt("login_streak");
                        Date sqlDate = rs.getDate("last_login_date");
                        lastLoginDate = sqlDate != null ? sqlDate.toLocalDate() : null;
                        isLinked = (rs.getString("discord_id") != null);
                    }
                }

                // Calculer le nouveau streak
                int newStreak;
                if (lastLoginDate == null) {
                    // Première connexion
                    newStreak = 1;
                } else if (lastLoginDate.equals(today)) {
                    // Déjà connecté aujourd'hui, pas de changement
                    newStreak = currentStreak;
                } else if (lastLoginDate.equals(today.minusDays(1))) {
                    // Connexion hier → streak +1, capé à MAX_STREAK
                    newStreak = Math.min(currentStreak + 1, MAX_STREAK);
                } else {
                    // Gap de 2+ jours → reset
                    newStreak = 1;
                }

                // Mettre à jour en BDD
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE players SET login_streak = ?, last_login_date = ? WHERE uuid = ?")) {
                    update.setInt(1, newStreak);
                    update.setDate(2, Date.valueOf(today));
                    update.setString(3, uuid.toString());
                    update.executeUpdate();
                }

                // Mettre à jour le cache uniquement si lié
                if (isLinked) {
                    streakCache.put(uuid, newStreak);
                }

                // Notifier le joueur sur le thread principal
                final int finalStreak = newStreak;
                final boolean streakIncreased = lastLoginDate != null
                        && lastLoginDate.equals(today.minusDays(1))
                        && newStreak > currentStreak;
                final boolean streakReset = lastLoginDate != null
                        && !lastLoginDate.equals(today)
                        && !lastLoginDate.equals(today.minusDays(1));
                final boolean finalLinked = isLinked;

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (!finalLinked) return; // Seulement si lié

                    double bonus = (finalStreak * 2);
                    String fire = getFireDisplay(finalStreak);

                    if (finalStreak == 1 && streakReset && currentStreak > 1) {
                        player.sendMessage("§6[LumiDiscord] §cTon streak de connexion est retombé à 0. Reviens demain pour le relancer !");
                    } else if (finalStreak >= 2 && streakIncreased) {
                        player.sendMessage("§6[LumiDiscord] " + fire + " §eStreak §6x" + finalStreak
                                + " §e! Bonus XP actif : §a+" + (int) bonus + "%"
                                + (finalStreak >= MAX_STREAK ? " §7(max atteint)" : ""));
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Unable to update login streak for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /**
     * Appelé à la déconnexion pour nettoyer le cache.
     */
    public void handleLogout(UUID uuid) {
        streakCache.remove(uuid);
    }

    /**
     * Retourne le multiplicateur XP du joueur (ex: 1.06 pour streak x3).
     * Si le streak n'est pas en cache, retourne 1.0 (pas de bonus).
     */
    public double getXpMultiplier(UUID uuid) {
        int streak = streakCache.getOrDefault(uuid, 0);
        return 1.0 + (streak * 0.02);
    }

    /**
     * Charge le streak en cache depuis la BDD (appelé à la connexion).
     */
    public void loadStreak(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseService().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT login_streak, discord_id FROM players WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        boolean isLinked = rs.getString("discord_id") != null;
                        if (isLinked) {
                            streakCache.put(uuid, rs.getInt("login_streak"));
                        } else {
                            streakCache.remove(uuid);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Unable to load streak for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private String getFireDisplay(int streak) {
        if (streak >= MAX_STREAK) return "§c🔥🔥🔥";
        if (streak >= 7) return "§6🔥🔥";
        if (streak >= 3) return "§e🔥";
        return "§7🔥";
    }
}
