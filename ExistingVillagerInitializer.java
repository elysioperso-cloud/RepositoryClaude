package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TeamAdminCommand implements CommandExecutor, TabCompleter {

    private final LumiDiscord plugin;
    private final TeamManager teamManager;

    public TeamAdminCommand(LumiDiscord plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lumidiscord.admin")) {
            sender.sendMessage(ChatColor.RED + "Pas de permission.");
            return true;
        }

        if (args.length < 1) { return false; }

        String sub = args[0].toLowerCase();
        if (sub.equals("addxp")) {
            if (args.length < 3) return false;
            TeamData team = teamManager.getTeamByName(args[1]);
            if (team != null) {
                team.setXp(team.getXp() + Long.parseLong(args[2]));
                teamManager.persistTeam(team);
                sender.sendMessage(ChatColor.GREEN + "XP ajouté.");
            }
        } else if (sub.equals("chest_cost")) {
            if (args.length < 2) return false;
            Material mat = Material.matchMaterial(args[1].toUpperCase());
            if (mat != null) {
                teamManager.setChestUpgradeItem(mat);
                sender.sendMessage(ChatColor.GREEN + "Nouvel item de déblocage : " + mat.name());
            }
        } else if (sub.equals("quest") && args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player player)) return true;
            Material item = player.getInventory().getItemInMainHand().getType();
            if (item == Material.AIR) { player.sendMessage(ChatColor.RED + "Tenez l'item de la quête en main !"); return true; }
            int amount = Integer.parseInt(args[2]);
            String reward = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            
            TeamQuest quest = new TeamQuest(item, amount, reward);
            teamManager.getAllTeams().forEach(t -> t.setActiveQuest(quest));
            Bukkit.broadcastMessage("§6[Quête] §eNouvelle quête : Récoltez §6" + amount + " " + item.name() + " §e!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("addxp", "chest_cost", "quest");
        if (args.length == 2 && args[0].equalsIgnoreCase("addxp")) return teamManager.getAllTeams().stream().map(TeamData::getName).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("quest")) return List.of("set");
        return new ArrayList<>();
    }
}