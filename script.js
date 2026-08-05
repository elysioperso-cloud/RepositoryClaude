package fr.timeo.lumidiscord.minecraft.team;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.gui.TeamPanelGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TeamCommand implements CommandExecutor, TabCompleter, Listener {
    private final LumiDiscord plugin;
    private final TeamManager teamManager;

    public TeamCommand(LumiDiscord plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
            if (team == null) { sendHelp(player); return true; }
            new TeamPanelGui(plugin, team).open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "sethome" -> handleSetHome(player);
            case "accept" -> handleAccept(player);
            case "war" -> handleWar(player, args);
            case "flagset" -> handleFlagSet(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleSetHome(Player player) {
        TeamData team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { player.sendMessage("§cVous n'avez pas d'équipe."); return; }
        if (!team.getLeader().equals(player.getUniqueId())) { player.sendMessage("§cSeul le chef peut faire ça."); return; }
        
        // APPEL DE LA MÉTHODE ROBUSTE
        teamManager.setHomeAndPersist(team, player.getLocation(), player);
    }

    private void handleCreate(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage("§cUsage: /team create <nom> <chef>"); return; }
        Player leader = Bukkit.getPlayer(args[2]);
        if (leader == null) { p.sendMessage("§cLe chef doit être connecté."); return; }
        if (teamManager.createTeam(args[1], leader.getUniqueId(), ChatColor.WHITE, "") != null) {
            p.sendMessage("§aÉquipe créée !");
        }
    }

    private void handleWar(Player p, String[] args) {
        if (args.length < 3) return;
        TeamData t = teamManager.getTeamByPlayer(p.getUniqueId());
        if (t != null && plugin.getWarManager().declareWar(t.getName(), args[1], args[2], p)) p.sendMessage("§aDemande envoyée !");
    }

    private void handleFlagSet(Player p) {
        TeamData t = teamManager.getTeamByPlayer(p.getUniqueId());
        if (t != null) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType().name().endsWith("_BANNER")) {
                teamManager.updateBanner(t, item);
                p.sendMessage("§aBannière définie !");
            }
        }
    }

    private void handleAccept(Player p) { p.sendMessage("§cPanel /team pour accepter."); }
    private void sendHelp(Player p) { p.sendMessage("§6/team create <nom> <chef>\n/team sethome"); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a, @NotNull String[] args) {
        return (args.length == 1) ? Arrays.asList("create", "sethome", "war", "flagset") : new ArrayList<>();
    }
}