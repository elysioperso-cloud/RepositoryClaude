package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnCommand implements CommandExecutor, Listener {
    private final LumiDiscord plugin;
    private final Map<UUID, Integer> pendingSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTasks = new ConcurrentHashMap<>();

    public SpawnCommand(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        // VÉRIFICATION NIVEAU 1
        long xp = plugin.getXpBufferManager().getCachedXp(player.getUniqueId());
        int level = plugin.getLevelManager().calculateLevel(xp);
        if (level < 1) {
            player.sendMessage(ChatColor.RED + "Vous devez être niveau 1 pour utiliser le /spawn. (Actuel: " + level + ")");
            return true;
        }

        // BLOCAGE ASSAUT
        if (plugin.getWarManager() != null && plugin.getWarManager().isCommandDisabled(player, "/spawn")) {
            player.sendMessage(ChatColor.RED + "Impossible pendant l'assaut !");
            return true;
        }

        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            player.sendMessage(ChatColor.RED + "La commande /spawn fonctionne uniquement dans l'Overworld.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (pendingTasks.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "Teleportation deja en cours. Ne bouge pas et ne combat pas.");
            return true;
        }

        Location spawnLocation = player.getWorld().getSpawnLocation();
        pendingSeconds.put(playerId, 3);
        player.sendMessage(ChatColor.YELLOW + "Teleportation au spawn dans 3 secondes. Ne bouge pas et ne combat pas.");

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) {
                cancelPending(playerId);
                return;
            }

            int secondsLeft = pendingSeconds.getOrDefault(playerId, 0);
            if (secondsLeft <= 0) {
                cancelPending(playerId);
                online.teleport(spawnLocation);
                online.sendTitle(ChatColor.GREEN + "Teleportation", ChatColor.WHITE + "Spawn atteint", 0, 30, 10);
                online.sendMessage(ChatColor.GREEN + "Teleporte au spawn.");
                return;
            }

            online.sendTitle(ChatColor.GOLD + String.valueOf(secondsLeft), ChatColor.YELLOW + "Ne bouge pas et ne combat pas", 0, 20, 0);
            pendingSeconds.put(playerId, secondsLeft - 1);
        }, 0L, 20L);

        pendingTasks.put(playerId, task);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isPending(event.getPlayer().getUniqueId())) return;
        if (!positionChanged(event)) return;
        cancelWithMessage(event.getPlayer(), "Teleportation annulee: vous avez bouge.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isPending(player.getUniqueId())) return;
        cancelWithMessage(player, "Teleportation annulee: vous avez ete touche.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !isPending(attacker.getUniqueId())) return;
        cancelWithMessage(attacker, "Teleportation annulee: vous avez attaque.");
    }

    private boolean positionChanged(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ());
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    private boolean isPending(UUID playerId) { return pendingTasks.containsKey(playerId); }

    private void cancelWithMessage(Player player, String message) {
        cancelPending(player.getUniqueId());
        player.sendTitle(ChatColor.RED + "Annule", "", 0, 20, 10);
        player.sendMessage(ChatColor.RED + message);
    }

    private void cancelPending(UUID playerId) {
        pendingSeconds.remove(playerId);
        BukkitTask task = pendingTasks.remove(playerId);
        if (task != null) task.cancel();
    }
}