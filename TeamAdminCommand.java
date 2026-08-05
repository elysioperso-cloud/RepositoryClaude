package fr.timeo.lumidiscord.discord.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.database.DatabaseService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class XpTopDiscordCommand extends ListenerAdapter {

    private final LumiDiscord plugin;
    private final DatabaseService databaseService;

    public XpTopDiscordCommand(LumiDiscord plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("xptop")) {
            return;
        }

        event.deferReply(false).queue(); // Public reply

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT username, xp FROM players ORDER BY xp DESC LIMIT 10")) {
                
                List<String> topList = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        String username = rs.getString("username");
                        long xp = rs.getLong("xp");
                        int level = plugin.getLevelManager().calculateLevel(xp);
                        topList.add("**#" + rank + "** - " + username + " (Niveau " + level + " | " + xp + " XP)");
                        rank++;
                    }
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("🏆 Top 10 Joueurs (XP)")
                            .setColor(Color.ORANGE)
                            .setTimestamp(Instant.now());
                    
                    if (topList.isEmpty()) {
                        embed.setDescription("Aucun joueur n'a été trouvé.");
                    } else {
                        embed.setDescription(String.join("\n", topList));
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                });

            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to fetch xptop: " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("❌ Erreur")
                            .setDescription("Impossible de récupérer le classement.")
                            .setColor(Color.RED);
                    event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
                });
            }
        });
    }
}
