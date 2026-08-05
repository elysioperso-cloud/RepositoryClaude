package fr.timeo.lumidiscord.discord.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.database.DatabaseService;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class ProfileCommand extends ListenerAdapter {

    private final LumiDiscord plugin;
    private final DatabaseService databaseService;
    private final LevelManager levelManager;

    public ProfileCommand(LumiDiscord plugin, DatabaseService databaseService, LevelManager levelManager) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.levelManager = levelManager;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("profil")) {
            return;
        }
        event.deferReply(true).queue();
        Member member = event.getMember();
        if (member == null) {
            replyEmbed(event, "❌ Profil indisponible", "Impossible de déterminer votre membre Discord.", Color.RED);
            return;
        }

        String discordId = member.getId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID[] uuidHolder = new UUID[1];
            String[] usernameHolder = new String[1];
            long[] xpHolder = new long[1];

            try (Connection connection = databaseService.getConnection();
                 PreparedStatement profileStmt = connection.prepareStatement("SELECT uuid, username, xp FROM players WHERE discord_id = ?")) {
                profileStmt.setString(1, discordId);
                try (ResultSet resultSet = profileStmt.executeQuery()) {
                    if (!resultSet.next()) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                replyEmbed(event, "❌ Profil introuvable", "Votre compte Discord n'est pas lié à un profil Minecraft. Utilisez /linkdiscord en jeu, puis /link ici.", Color.RED));
                        return;
                    }
                    uuidHolder[0] = UUID.fromString(resultSet.getString("uuid"));
                    usernameHolder[0] = resultSet.getString("username");
                    xpHolder[0] = resultSet.getLong("xp");
                }

            } catch (SQLException exception) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        replyEmbed(event, "❌ Profil indisponible", "Impossible de charger votre profil pour le moment.", Color.RED));
                return;
            }

            int level = levelManager.calculateLevel(xpHolder[0]);
            long totalXpRequiredForNextLevel = levelManager.getTotalXpRequiredForLevel(level + 1);
            String usernameDisplay = usernameHolder[0] != null ? usernameHolder[0] : "inconnu";
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📊 Profil LumiDiscord")
                        .setColor(Color.CYAN)
                        .setTimestamp(Instant.now())
                        .addField("Pseudo Minecraft", usernameDisplay, true)
                        .addField("Niveau", String.valueOf(level), true)
                        .addField("XP", xpHolder[0] + " / " + totalXpRequiredForNextLevel, true)
                        .addField("Progression", "Il manque " + (totalXpRequiredForNextLevel - xpHolder[0]) + " XP pour le niveau " + (level + 1), false);
                event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
            });
        });
    }

    private void replyEmbed(SlashCommandInteractionEvent event, String title, String description, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now());
        event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
