package fr.timeo.lumidiscord.discord.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.config.PluginConfig;
import fr.timeo.lumidiscord.database.DatabaseService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public class DailyXpCommand extends ListenerAdapter {

    private final LumiDiscord plugin;
    private final DatabaseService databaseService;
    private final PluginConfig pluginConfig;

    public DailyXpCommand(LumiDiscord plugin, DatabaseService databaseService, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("dailyxp")) return;
        Member member = event.getMember();
        if (member == null) { replyPrivateEmbed(event, "❌ Erreur", "Impossible de déterminer votre membre Discord.", Color.RED); return; }

        int dailyAmount = Math.max(25, pluginConfig.getInt("discord.daily-xp-amount"));
        String discordId = member.getId();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseService.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement selectStmt = connection.prepareStatement("SELECT uuid, username, last_daily_claim, xp FROM players WHERE discord_id = ? FOR UPDATE")) {
                    selectStmt.setString(1, discordId);
                    try (ResultSet resultSet = selectStmt.executeQuery()) {
                        if (!resultSet.next()) {
                            connection.rollback();
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    replyPrivateEmbed(event, "❌ Profil introuvable", "Votre compte Discord n'est pas lié. Utilisez /linkdiscord en jeu.", Color.RED));
                            return;
                        }
                        
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        String username = resultSet.getString("username");
                        Timestamp lastClaim = resultSet.getTimestamp("last_daily_claim");
                        long currentXp = resultSet.getLong("xp");

                        // LOGIQUE MINUIT: Vérifier si la date du dernier claim est la même que AUJOURD'HUI
                        ZoneId zone = ZoneId.of("Europe/Paris"); // Fuseau horaire ajustable
                        LocalDate today = LocalDate.now(zone);
                        
                        if (lastClaim != null) {
                            LocalDate lastClaimDate = lastClaim.toInstant().atZone(zone).toLocalDate();
                            if (today.equals(lastClaimDate)) {
                                connection.rollback();
                                plugin.getServer().getScheduler().runTask(plugin, () ->
                                        replyPrivateEmbed(event, "⏰ Déjà récupéré !", "Tu as déjà pris ton bonus aujourd'hui. Reviens demain dès **minuit** !", Color.ORANGE));
                                return;
                            }
                        }

                        long newXp = currentXp + dailyAmount;
                        try (PreparedStatement updateStmt = connection.prepareStatement("UPDATE players SET xp = ?, last_daily_claim = ? WHERE uuid = ?")) {
                            updateStmt.setLong(1, newXp);
                            updateStmt.setTimestamp(2, Timestamp.from(Instant.now()));
                            updateStmt.setString(3, uuid.toString());
                            updateStmt.executeUpdate();
                        }
                        
                        try (PreparedStatement srcStmt = connection.prepareStatement("INSERT INTO xp_sources (uuid, xp_from_daily) VALUES (?, ?) ON DUPLICATE KEY UPDATE xp_from_daily = xp_from_daily + VALUES(xp_from_daily)")) {
                            srcStmt.setString(1, uuid.toString());
                            srcStmt.setInt(2, dailyAmount);
                            srcStmt.executeUpdate();
                        }

                        connection.commit();
                        plugin.getRewardManager().checkAndGrantForLevel(uuid, plugin.getLevelManager().calculateLevel(currentXp), plugin.getLevelManager().calculateLevel(newXp));

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle("🎁 Bonus Journalier")
                                    .setDescription("**" + (username != null ? username : member.getEffectiveName()) + "** vient de récupérer son bonus de **" + dailyAmount + " XP** !")
                                    .setColor(Color.GREEN)
                                    .setTimestamp(Instant.now());
                            event.replyEmbeds(embed.build()).queue();
                        });
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Daily XP Error: " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> replyPrivateEmbed(event, "❌ Erreur", "Erreur SQL lors du bonus.", Color.RED));
            }
        });
    }

    private void replyPrivateEmbed(SlashCommandInteractionEvent event, String title, String description, Color color) {
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(color).setTimestamp(Instant.now());
        if (event.isAcknowledged()) event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
        else event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}