package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.database.DatabaseService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class LinkDiscordCommand implements CommandExecutor {

    private final LumiDiscord plugin;
    private final DatabaseService databaseService;

    public LinkDiscordCommand(LumiDiscord plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO players (uuid, username, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE username = VALUES(username)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, player.getName());
                insert.executeUpdate();

                try (PreparedStatement linkCode = connection.prepareStatement("INSERT INTO link_codes (code, uuid, expires_at, used) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), FALSE) ON DUPLICATE KEY UPDATE code = VALUES(code), uuid = VALUES(uuid), expires_at = VALUES(expires_at), used = FALSE")) {
                    linkCode.setString(1, code);
                    linkCode.setString(2, uuid.toString());
                    linkCode.executeUpdate();
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§aVotre code de liaison Discord est : §6" + code + "§a. Il expire dans 10 minutes."));
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to create link code: " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§cUne erreur est survenue lors de la génération du code de liaison. Réessayez plus tard."));
            }
        });
        return true;
    }
}
