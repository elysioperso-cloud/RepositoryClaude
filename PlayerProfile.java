package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelManager {

    private final LumiDiscord plugin;

    public LevelManager(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    public int calculateLevel(long xp) {
        int level = 0;
        long totalNeeded = 0;
        while (true) {
            long nextLevelXp = getXpNeededForLevel(level + 1);
            if (xp >= (totalNeeded + nextLevelXp)) {
                totalNeeded += nextLevelXp;
                level++;
            } else {
                break;
            }
        }
        return level;
    }

    private long getXpNeededForLevel(int level) {
        if (level <= 1) return 200; // Niveau 1 : 200 XP
        // Formule : 200 * (1.20)^(level-1)
        return Math.round(200 * Math.pow(1.20, level - 1));
    }

    public long getXpRemainingBeforeNextLevel(long xp) {
        int currentLevel = calculateLevel(xp);
        long totalForCurrent = getTotalXpRequiredForLevel(currentLevel);
        long neededForNext = getXpNeededForLevel(currentLevel + 1);
        return Math.max(0, neededForNext - (xp - totalForCurrent));
    }

    public long getTotalXpRequiredForLevel(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++) {
            total += getXpNeededForLevel(i);
        }
        return total;
    }

    public int getPluginLevel(UUID uuid) {
        try (Connection connection = plugin.getDatabaseService().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT xp FROM players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) return calculateLevel(resultSet.getLong("xp"));
                }
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    public CompletableFuture<Integer> getPluginLevelAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getPluginLevel(uuid));
    }

    public int getRequiredLevelForHome() {
        return plugin.getPluginConfig().getInt("home.required-level");
    }
}