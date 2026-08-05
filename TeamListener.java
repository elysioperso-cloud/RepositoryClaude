package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.models.HomeLocation;
import fr.timeo.lumidiscord.minecraft.managers.HomeManager;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeCommand implements CommandExecutor, TabCompleter, Listener {

    private final LumiDiscord plugin;
    private final HomeManager homeManager;
    private final LevelManager levelManager;
    private final Map<UUID, Integer> pendingSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTasks = new ConcurrentHashMap<>();

    public HomeCommand(LumiDiscord plugin, HomeManager homeManager, LevelManager levelManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.levelManager = levelManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        boolean isAdmin = player.hasPermission("lumidiscord.admin");

        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            player.sendMessage(ChatColor.GOLD + "=== Vos Homes ===");
            player.sendMessage(ChatColor.YELLOW + "- main (Position: " + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ")");
            return true;
        }

        if (!isAdmin) {
            long xp = plugin.getXpBufferManager().getCachedXp(player.getUniqueId());
            int level = levelManager.calculateLevel(xp);
            if (level < 20) {
                player.sendMessage(ChatColor.RED + "Vous devez être niveau 20 pour utiliser le /home. (Actuel: " + level + ")");
                return true;
            }
        }

        // Blocage pendant l'assaut (sauf admin)
        if (!isAdmin && plugin.getWarManager() != null && plugin.getWarManager().isCommandDisabled(player, "/home")) {
            player.sendMessage(ChatColor.RED + "Impossible pendant l'assaut !");
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (pendingTasks.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "Teleportation deja en cours.");
            return true;
        }

        HomeLocation homeLocation = homeManager.getHome(player.getUniqueId());
        if (homeLocation == null || homeLocation.toLocation() == null) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas de home défini. (/sethome)");
            return true;
        }
        Location targetLocation = homeLocation.toLocation();

        pendingSeconds.put(playerId, 3);
        player.sendMessage(ChatColor.YELLOW + "Téléportation à votre home dans 3 secondes. Ne bougez pas.");

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) {
                cancelPending(playerId);
                return;
            }

            int secondsLeft = pendingSeconds.getOrDefault(playerId, 0);
            if (secondsLeft <= 0) {
                cancelPending(playerId);
                online.teleport(targetLocation);
                online.sendMessage(ChatColor.GREEN + "Téléporté à votre home.");
                return;
            }

            online.sendTitle(ChatColor.GOLD + String.valueOf(secondsLeft), ChatColor.YELLOW + "Ne bouge pas", 0, 20, 0);
            pendingSeconds.put(playerId, secondsLeft - 1);
        }, 0L, 20L);

        pendingTasks.put(playerId, task);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isPending(event.getPlayer().getUniqueId()) && positionChanged(event)) {
            cancelWithMessage(event.getPlayer(), "Mouvement détecté ! Téléportation annulée.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isPending(player.getUniqueId())) {
            cancelWithMessage(player, "Dégâts reçus ! Téléportation annulée.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && isPending(attacker.getUniqueId())) {
            cancelWithMessage(attacker, "Combat détecté ! Téléportation annulée.");
        }
    }

    private boolean positionChanged(PlayerMoveEvent event) {
        Location from = event.getFrom(); Location to = event.getTo();
        return to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    private boolean isPending(UUID playerId) { return pendingTasks.containsKey(playerId); }

    private void cancelWithMessage(Player player, String message) {
        cancelPending(player.getUniqueId());
        player.sendMessage(ChatColor.RED + message);
    }

    private void cancelPending(UUID playerId) {
        pendingSeconds.remove(playerId);
        BukkitTask task = pendingTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("list");
        return new ArrayList<>();
    }
}