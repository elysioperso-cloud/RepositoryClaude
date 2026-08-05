package fr.timeo.lumidiscord.api;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.config.PluginConfig;
import fr.timeo.lumidiscord.database.DatabaseService;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import net.dv8tion.jda.api.entities.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpApiService {

    private final LumiDiscord plugin;
    private final PluginConfig pluginConfig;
    private final DatabaseService databaseService;
    private final LevelManager levelManager;
    private Javalin app;

    public HttpApiService(LumiDiscord plugin, PluginConfig pluginConfig, DatabaseService databaseService) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.databaseService = databaseService;
        this.levelManager = plugin.getLevelManager();
    }

    public void start() {
        if (!pluginConfig.getBoolean("http.enabled")) return;

        try {
            app = Javalin.create(config -> {
                config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.allowHost(pluginConfig.getString("http.cors-origin"))));
                config.http.maxRequestSize = 1024L * 1024L;
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = plugin.getDataFolder().getAbsolutePath() + "/web";
                    staticFiles.location = io.javalin.http.staticfiles.Location.EXTERNAL;
                });
            });

            app.get("/api/players", this::handlePlayers);
            app.get("/api/players/{uuid}", this::handlePlayer);
            app.get("/api/players/{uuid}/xp-sources", this::handleXpSources);
            app.get("/api/stats/top", this::handleTopStats);
            app.get("/api/teams", this::handleTeams);
            app.get("/api/teams/{id}/members", this::handleTeamMembers);

            app.start(pluginConfig.getInt("http.port"));
            plugin.getLogger().info("HTTP API started on port " + pluginConfig.getInt("http.port"));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start HTTP API (Port already in use?): " + e.getMessage());
            app = null;
        }
    }

    public void stop() {
        if (app != null) {
            try { app.stop(); } catch (Exception ignored) {}
        }
    }

    private void handlePlayers(Context ctx) {
        ctx.json(queryPlayers());
    }

    private void handlePlayer(Context ctx) {
        Map<String, Object> result = queryPlayer(ctx.pathParam("uuid"));
        if (result == null) {
            ctx.status(404).json(Map.of("error", "Player not found"));
            return;
        }
        ctx.json(result);
    }

    private void handleXpSources(Context ctx) {
        Map<String, Object> result = queryXpSources(ctx.pathParam("uuid"));
        if (result == null) {
            ctx.status(404).json(Map.of("error", "Sources not found"));
            return;
        }
        ctx.json(result);
    }

    private void handleTeams(Context ctx) {
        ctx.json(queryTeams());
    }

    private void handleTeamMembers(Context ctx) {
        try {
            int teamId = Integer.parseInt(ctx.pathParam("id"));
            List<Map<String, Object>> members = queryTeamMembers(teamId);
            if (members == null) {
                ctx.status(404).json(Map.of("error", "Team not found"));
                return;
            }
            ctx.json(members);
        } catch (NumberFormatException exception) {
            ctx.status(400).json(Map.of("error", "Invalid team id"));
        }
    }

    private void handleTopStats(Context ctx) {
        String stat = ctx.queryParam("stat");
        Integer limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
        if (stat == null || limit == null || limit < 1 || limit > 50) {
            ctx.status(400).json(Map.of("error", "Invalid parameters"));
            return;
        }
        List<String> allowedStats = List.of("level", "playtime_seconds", "blocks_broken", "blocks_placed", "distance_traveled", "deaths", "items_crafted", "mobs_killed", "jumps");
        if (!allowedStats.contains(stat)) {
            ctx.status(400).json(Map.of("error", "Invalid stat"));
            return;
        }
        ctx.json(queryTopStats(stat, limit));
    }

    private List<Map<String, Object>> queryPlayers() {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT p.uuid, p.username, p.discord_id, p.discord_username, p.xp, p.last_join, ps.playtime_seconds, ps.blocks_broken, ps.blocks_placed, ps.distance_traveled, ps.deaths, ps.items_crafted, ps.mobs_killed, ps.jumps, tm.team_id, t.name AS team_name, t.color AS team_color, t.prefix AS team_prefix FROM players p LEFT JOIN player_stats ps ON ps.uuid = p.uuid LEFT JOIN team_members tm ON tm.player_uuid = p.uuid LEFT JOIN teams t ON t.id = tm.team_id")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("uuid", resultSet.getString("uuid"));
                    row.put("username", resultSet.getString("username"));
                    row.put("discord_id", resultSet.getString("discord_id"));
                    
                    String discordUsername = null;
                    try { discordUsername = resultSet.getString("discord_username"); } catch(SQLException ignored) {}
                    
                    if (discordUsername == null && resultSet.getString("discord_id") != null && plugin.getDiscordBotService() != null && plugin.getDiscordBotService().getJda() != null) {
                        User user = plugin.getDiscordBotService().getJda().getUserById(resultSet.getString("discord_id"));
                        if (user != null) discordUsername = user.getEffectiveName();
                    }
                    if (discordUsername != null) row.put("discord_username", discordUsername);

                    long xp = resultSet.getLong("xp");
                    row.put("xp", xp);
                    row.put("level", levelManager.calculateLevel(xp));
                    row.put("last_join", resultSet.getTimestamp("last_join"));
                    row.put("playtime_seconds", resultSet.getLong("playtime_seconds"));
                    row.put("blocks_broken", resultSet.getLong("blocks_broken"));
                    row.put("blocks_placed", resultSet.getLong("blocks_placed"));
                    row.put("distance_traveled", resultSet.getDouble("distance_traveled"));
                    row.put("deaths", resultSet.getInt("deaths"));
                    row.put("items_crafted", resultSet.getLong("items_crafted"));
                    row.put("mobs_killed", resultSet.getInt("mobs_killed"));
                    row.put("jumps", resultSet.getLong("jumps"));
                    row.put("team_id", resultSet.getObject("team_id"));
                    row.put("team_name", resultSet.getString("team_name"));
                    row.put("team_color", resultSet.getString("team_color"));
                    row.put("team_prefix", resultSet.getString("team_prefix"));
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query player list", exception);
        }
    }

    private Map<String, Object> queryPlayer(String uuid) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT p.uuid, p.username, p.discord_id, p.discord_username, p.xp, p.last_join, ps.playtime_seconds, ps.blocks_broken, ps.blocks_placed, ps.distance_traveled, ps.deaths, ps.items_crafted, ps.mobs_killed, ps.jumps, tm.team_id, t.name AS team_name, t.color AS team_color, t.prefix AS team_prefix FROM players p LEFT JOIN player_stats ps ON ps.uuid = p.uuid LEFT JOIN team_members tm ON tm.player_uuid = p.uuid LEFT JOIN teams t ON t.id = tm.team_id WHERE p.uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                Map<String, Object> row = new HashMap<>();
                row.put("uuid", resultSet.getString("uuid"));
                row.put("username", resultSet.getString("username"));
                row.put("discord_id", resultSet.getString("discord_id"));
                
                String discordUsername = null;
                try { discordUsername = resultSet.getString("discord_username"); } catch(SQLException ignored) {}
                
                if (discordUsername == null && resultSet.getString("discord_id") != null && plugin.getDiscordBotService() != null && plugin.getDiscordBotService().getJda() != null) {
                    User user = plugin.getDiscordBotService().getJda().getUserById(resultSet.getString("discord_id"));
                    if (user != null) discordUsername = user.getEffectiveName();
                }
                if (discordUsername != null) row.put("discord_username", discordUsername);

                long xp = resultSet.getLong("xp");
                row.put("xp", xp);
                row.put("level", levelManager.calculateLevel(xp));
                row.put("last_join", resultSet.getTimestamp("last_join"));
                row.put("playtime_seconds", resultSet.getLong("playtime_seconds"));
                row.put("blocks_broken", resultSet.getLong("blocks_broken"));
                row.put("blocks_placed", resultSet.getLong("blocks_placed"));
                row.put("distance_traveled", resultSet.getDouble("distance_traveled"));
                row.put("deaths", resultSet.getInt("deaths"));
                row.put("items_crafted", resultSet.getLong("items_crafted"));
                row.put("mobs_killed", resultSet.getInt("mobs_killed"));
                row.put("jumps", resultSet.getLong("jumps"));
                row.put("team_id", resultSet.getObject("team_id"));
                row.put("team_name", resultSet.getString("team_name"));
                row.put("team_color", resultSet.getString("team_color"));
                row.put("team_prefix", resultSet.getString("team_prefix"));
                return row;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query player detail", exception);
        }
    }

    private Map<String, Object> queryXpSources(String uuid) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM xp_sources WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                Map<String, Object> row = new HashMap<>();
                row.put("uuid", resultSet.getString("uuid"));
                row.put("xp_from_playtime", resultSet.getLong("xp_from_playtime"));
                row.put("xp_from_voice", resultSet.getLong("xp_from_voice"));
                row.put("xp_from_messages", resultSet.getLong("xp_from_messages"));
                row.put("xp_from_daily", resultSet.getLong("xp_from_daily"));
                row.put("xp_from_admin", resultSet.getLong("xp_from_admin"));
                return row;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query XP sources", exception);
        }
    }

    private List<Map<String, Object>> queryTeams() {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT t.id, t.name, t.leader_uuid, p.username AS leader_username, t.color, t.prefix, t.home_world, (SELECT COUNT(*) FROM team_members tm WHERE tm.team_id = t.id) AS member_count FROM teams t LEFT JOIN players p ON p.uuid = t.leader_uuid ORDER BY t.name ASC")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", resultSet.getInt("id"));
                    row.put("name", resultSet.getString("name"));
                    row.put("leader_uuid", resultSet.getString("leader_uuid"));
                    row.put("leader_username", resultSet.getString("leader_username"));
                    row.put("color", resultSet.getString("color"));
                    row.put("prefix", resultSet.getString("prefix"));
                    row.put("home_world", resultSet.getString("home_world"));
                    row.put("member_count", resultSet.getInt("member_count"));
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query teams", exception);
        }
    }

    private List<Map<String, Object>> queryTeamMembers(int teamId) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement checkStatement = connection.prepareStatement("SELECT id FROM teams WHERE id = ?")) {
            checkStatement.setInt(1, teamId);
            try (ResultSet checkResult = checkStatement.executeQuery()) {
                if (!checkResult.next()) return null;
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT tm.player_uuid, COALESCE(p.username, tm.player_uuid) AS username, t.leader_uuid FROM team_members tm JOIN teams t ON t.id = tm.team_id LEFT JOIN players p ON p.uuid = tm.player_uuid WHERE tm.team_id = ? ORDER BY CASE WHEN tm.player_uuid = t.leader_uuid THEN 0 ELSE 1 END, COALESCE(p.username, tm.player_uuid) ASC")) {
                statement.setInt(1, teamId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("player_uuid", resultSet.getString("player_uuid"));
                        row.put("username", resultSet.getString("username"));
                        row.put("is_leader", resultSet.getString("player_uuid").equals(resultSet.getString("leader_uuid")));
                        rows.add(row);
                    }
                    return rows;
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query team members", exception);
        }
    }

    private List<Map<String, Object>> queryTopStats(String stat, int limit) {
        String query = "level".equals(stat) ? "SELECT uuid, username, xp FROM players ORDER BY xp DESC LIMIT ?" : "SELECT p.uuid, p.username, ps." + stat + " FROM players p LEFT JOIN player_stats ps ON ps.uuid = p.uuid ORDER BY ps." + stat + " DESC LIMIT ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("uuid", resultSet.getString("uuid"));
                    row.put("username", resultSet.getString("username"));
                    if ("level".equals(stat)) {
                        long xp = resultSet.getLong("xp");
                        row.put("level", levelManager.calculateLevel(xp));
                    } else {
                        row.put(stat, resultSet.getObject(stat));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to query leaderboards", exception);
        }
    }
}