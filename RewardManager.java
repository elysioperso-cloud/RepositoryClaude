package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class XpTopCommand implements CommandExecutor {

    private final LumiDiscord plugin;

    public XpTopCommand(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sender.sendMessage("§e[LumiDiscord] §7Chargement du classement...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (sender instanceof org.bukkit.entity.Player player) {
                try (Connection connection = plugin.getDatabaseService().getConnection();
                     PreparedStatement stmt = connection.prepareStatement("SELECT discord_id FROM players WHERE uuid = ?")) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getString("discord_id") == null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("§cTu dois d'abord lier ton compte Discord pour utiliser cette commande ! Utilise /linkdiscord."));
                            return;
                        }
                    }
                } catch (SQLException ignored) {}
            }
            
            try (Connection connection = plugin.getDatabaseService().getConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT username, xp FROM players ORDER BY xp DESC LIMIT 10")) {

                List<String> topList = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        String username = rs.getString("username");
                        long xp = rs.getLong("xp");
                        int level = plugin.getLevelManager().calculateLevel(xp);
                        topList.add("§6#" + rank + " §e- §f" + username + " §7(Niv. " + level + " | " + xp + " XP)");
                        rank++;
                    }
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§6=== §eTop 10 Joueurs (XP) §6===");
                    if (topList.isEmpty()) {
                        sender.sendMessage("§cAucun joueur trouvé.");
                    } else {
                        for (String line : topList) {
                            sender.sendMessage(line);
                        }
                    }
                });

            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to fetch xptop: " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§cUne erreur est survenue lors de la récupération du classement."));
            }
        });
        return true;
    }
}
