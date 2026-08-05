package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.models.HomeLocation;
import fr.timeo.lumidiscord.minecraft.managers.HomeManager;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHomeCommand implements CommandExecutor {

    private final LumiDiscord plugin;
    private final HomeManager homeManager;
    private final LevelManager levelManager;

    public SetHomeCommand(LumiDiscord plugin, HomeManager homeManager, LevelManager levelManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.levelManager = levelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        // DÉROGATION ADMIN : Pas de limite de niveau
        boolean isAdmin = player.hasPermission("lumidiscord.admin");
        
        if (!isAdmin) {
            long xp = plugin.getXpBufferManager().getCachedXp(player.getUniqueId());
            int level = levelManager.calculateLevel(xp);
            if (level < 20) {
                player.sendMessage(ChatColor.RED + "Vous devez atteindre le niveau 20 pour utiliser /sethome. (Actuel: " + level + ")");
                return true;
            }
        }

        String homeName = "main";
        if (isAdmin && args.length >= 1) {
            homeName = args[0].toLowerCase();
        }

        homeManager.setHome(player.getUniqueId(), new HomeLocation(player.getLocation())); // Note: HomeManager actuel ne gère qu'un home par défaut. On garde la structure pour la compatibilité.
        player.sendMessage(ChatColor.GREEN + "Home personnel '" + homeName + "' défini !");
        return true;
    }
}