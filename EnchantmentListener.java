package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.minecraft.managers.DayCountManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class DayCountCommand implements CommandExecutor, TabCompleter {

    private final DayCountManager dayCountManager;

    public DayCountCommand(DayCountManager dayCountManager) {
        this.dayCountManager = dayCountManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /daycount show | hide");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("show")) {
            dayCountManager.startDisplay(player);
            player.sendMessage(ChatColor.GREEN + "Affichage du nombre de jours activé !");
        } else if (subCommand.equals("hide")) {
            dayCountManager.stopDisplay(player);
            player.sendMessage(ChatColor.YELLOW + "Affichage du nombre de jours désactivé.");
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /daycount show | hide");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("show", "hide");
        }
        return List.of();
    }
}