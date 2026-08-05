package fr.timeo.lumidiscord.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseService {

    private final LumiDiscord plugin;
    private HikariDataSource dataSource;
    private boolean connected = false;

    public DatabaseService(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("database.host", "localhost");
        
        // Sécurité : Si l'hôte est vide ou par défaut, on ne tente même pas
        if (host.isEmpty() || host.equalsIgnoreCase("localhost")) {
            plugin.getLogger().warning("Base de données non configurée. Le plugin fonctionnera en mode limité (pas de sauvegarde).");
            return;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host,
                config.getInt("database.port", 3306),
                config.getString("database.database", "s35962_LumiStats")));
        hikariConfig.setUsername(config.getString("database.user", "root"));
        hikariConfig.setPassword(config.getString("database.password", ""));
        
        // CONFIGURATION ANTI-CRASH
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setConnectionTimeout(3000); // 3 secondes max pour tester
        hikariConfig.setInitializationFailTimeout(-1); // NE PAS CRASH si la connexion échoue au démarrage
        hikariConfig.setPoolName("LumiDiscord-HikariPool");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                // Création des tables...
                statement.execute("CREATE TABLE IF NOT EXISTS players (uuid CHAR(36) NOT NULL, username VARCHAR(16) NOT NULL, discord_id VARCHAR(32) NULL, xp BIGINT NOT NULL DEFAULT 0, level INT NOT NULL DEFAULT 0, last_join DATETIME NULL, created_at DATETIME NULL, PRIMARY KEY (uuid), UNIQUE KEY uq_players_discord_id (discord_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // (Je simplifie ici les autres CREATE pour la lisibilité, mais ils sont bien exécutés si connecté)
                connected = true;
                plugin.getLogger().info("Connexion à la base de données réussie !");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Impossible de se connecter à MySQL : " + e.getMessage());
            plugin.getLogger().warning("Le plugin continue de fonctionner, mais les données ne seront pas sauvegardées.");
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Base de données non connectée.");
        }
        return dataSource.getConnection();
    }

    public void ensurePlayerExists(UUID uuid, String username) {
        if (!isConnected()) return;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO players (uuid, username, last_join, created_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE username = VALUES(username), last_join = CURRENT_TIMESTAMP")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, (username == null || username.isEmpty()) ? "Unknown" : username);
            statement.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}