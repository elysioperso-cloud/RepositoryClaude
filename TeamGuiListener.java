package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import fr.timeo.lumidiscord.minecraft.managers.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GiveXpCommand implements CommandExecutor {

    private final LumiDiscord plugin;
    private final LevelManager levelManager;
    private final RewardManager rewardManager;

    public GiveXpCommand(LumiDiscord plugin, LevelManager levelManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lumidiscord.admin.givexp")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUsage : /givexp <joueur|sélecteur> <montant>");
            return true;
        }
        String playerSelector = args[0];
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cLe montant doit être un nombre entier.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§cLe montant doit être supérieur à 0.");
            return true;
        }

        List<UUID> targetUuids = resolveTargetPlayers(sender, playerSelector);
        if (targetUuids.isEmpty()) {
            sender.sendMessage("§cAucun joueur trouvé pour le ciblage : " + playerSelector);
            return true;
        }

        for (UUID uuid : targetUuids) {
            final String targetDisplayName = resolveDisplayName(uuid, playerSelector);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("§cLe joueur spécifié n'existe pas sur le serveur."));
                    return;
                }
                UUID targetUuid = offlinePlayer.getUniqueId();
                try (Connection connection = plugin.getDatabaseService().getConnection()) {
                    long oldXp;
                    try (PreparedStatement select = connection.prepareStatement("SELECT xp FROM players WHERE uuid = ? FOR UPDATE")) {
                        select.setString(1, targetUuid.toString());
                        try (ResultSet resultSet = select.executeQuery()) {
                            if (!resultSet.next()) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("§cLe joueur spécifié n'existe pas dans la base de données."));
                                return;
                            }
                            oldXp = resultSet.getLong("xp");
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement("UPDATE players SET xp = xp + ? WHERE uuid = ?")) {
                        update.setLong(1, amount);
                        update.setString(2, targetUuid.toString());
                        update.executeUpdate();
                    }
                    long newXp;
                    try (PreparedStatement select = connection.prepareStatement("SELECT xp FROM players WHERE uuid = ?")) {
                        select.setString(1, targetUuid.toString());
                        try (ResultSet resultSet = select.executeQuery()) {
                            resultSet.next();
                            newXp = resultSet.getLong("xp");
                        }
                    }
                    int oldLevel = levelManager.calculateLevel(oldXp);
                    int newLevel = levelManager.calculateLevel(newXp);
                    rewardManager.checkAndGrantForLevel(targetUuid, oldLevel, newLevel);
                    int finalNewLevel = newLevel;
                    long finalNewXp = newXp;
                    String finalTargetName = targetDisplayName;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§a" + amount + " XP ont été ajoutés à " + finalTargetName + ". Total : " + finalNewXp + " XP, niveau " + finalNewLevel + ".");
                        Player online = Bukkit.getPlayer(targetUuid);
                        if (online != null && online.isOnline()) {
                            online.sendMessage("§6[LumiDiscord] §aVous avez reçu " + amount + " XP de la part d'un administrateur.");
                        }
                    });
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Unable to give XP to " + targetDisplayName + ": " + exception.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("§cImpossible d'attribuer l'XP pour le moment."));
                }
            });
        }
        return true;
    }

    private List<UUID> resolveTargetPlayers(CommandSender sender, String selector) {
        List<UUID> targetUuids = new ArrayList<>();
        if (selector.startsWith("@")) {
            try {
                List<Entity> entities = Bukkit.selectEntities(sender, selector);
                for (Entity entity : entities) {
                    if (entity instanceof Player player) {
                        targetUuids.add(player.getUniqueId());
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid selectors and fall back to direct offline lookup.
            }
        }

        if (targetUuids.isEmpty() && !selector.startsWith("@")) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(selector);
            if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
                targetUuids.add(offlinePlayer.getUniqueId());
            }
        }

        return targetUuids;
    }

    private String resolveDisplayName(UUID uuid, String fallbackName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null && !name.isBlank() ? name : fallbackName;
    }
}
