package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.database.DatabaseService;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class XpDiscordCommand implements CommandExecutor {

    private final LumiDiscord plugin;
    private final DatabaseService databaseService;
    private final LevelManager levelManager;

    public XpDiscordCommand(LumiDiscord plugin, DatabaseService databaseService, LevelManager levelManager) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.levelManager = levelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lumidiscord.admin")) {
                sender.sendMessage("§cVous n'avez pas la permission d'exécuter cette commande.");
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage("§aLe plugin LumiDiscord (config, bot Discord, site Web) a été rechargé !");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final long[] xpHolder = {0L};
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement xpStatement = connection.prepareStatement("SELECT xp, discord_id FROM players WHERE uuid = ?")) {
                xpStatement.setString(1, uuid.toString());
                try (ResultSet xpResult = xpStatement.executeQuery()) {
                    if (xpResult.next()) {
                        if (xpResult.getString("discord_id") == null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§cTu dois d'abord lier ton compte Discord pour utiliser cette commande ! Utilise /linkdiscord."));
                            return;
                        }
                        xpHolder[0] = xpResult.getLong("xp");
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to load XP profile for " + uuid + ": " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§cImpossible de charger votre profil XP pour le moment."));
                return;
            }

            final int level = levelManager.calculateLevel(xpHolder[0]);
            final long totalXpRequiredForNextLevel = levelManager.getTotalXpRequiredForLevel(level + 1);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6=== Ton profil LumiDiscord ===");
                player.sendMessage("§eNiveau : §f" + level);
                player.sendMessage("§eXP : §b" + xpHolder[0] + " / " + totalXpRequiredForNextLevel);
                player.sendMessage("§7(Il te manque " + (totalXpRequiredForNextLevel - xpHolder[0]) + " XP pour le niveau " + (level + 1) + ")");
            });
        });

        return true;
    }
}
