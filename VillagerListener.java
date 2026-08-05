package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.TeamData;
import fr.timeo.lumidiscord.minecraft.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminXpCommand implements CommandExecutor, TabCompleter {

    private final LumiDiscord plugin;
    private final TeamManager teamManager;

    public AdminXpCommand(LumiDiscord plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lumidiscord.admin")) {
            sender.sendMessage(ChatColor.RED + "Pas de permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /adminxp <give|remove> <team> <nombre>");
            return true;
        }

        String action = args[0].toLowerCase();
        TeamData team = teamManager.getTeamByName(args[1]);
        if (team == null) {
            sender.sendMessage(ChatColor.RED + "Team introuvable.");
            return true;
        }

        try {
            long amount = Long.parseLong(args[2]);
            if (action.equals("give")) {
                team.setXp(team.getXp() + amount);
                sender.sendMessage(ChatColor.GREEN + "Ajout de " + amount + " XP à l'équipe " + team.getName());
            } else if (action.equals("remove")) {
                team.setXp(Math.max(0, team.getXp() - amount));
                sender.sendMessage(ChatColor.YELLOW + "Retrait de " + amount + " XP à l'équipe " + team.getName());
            }
            teamManager.persistTeam(team);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nombre invalide.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("give", "remove");
        if (args.length == 2) return teamManager.getAllTeams().stream().map(TeamData::getName).collect(Collectors.toList());
        return new ArrayList<>();
    }
}