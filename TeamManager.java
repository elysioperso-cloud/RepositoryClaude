package fr.timeo.lumidiscord.minecraft.listeners;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.models.PlayerStats;
import fr.timeo.lumidiscord.minecraft.managers.AfkManager;
import fr.timeo.lumidiscord.minecraft.managers.LevelManager;
import fr.timeo.lumidiscord.minecraft.managers.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Statistic;
import org.bukkit.entity.Monster;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerStatsListener implements Listener {

    private final LumiDiscord plugin;
    private final AfkManager afkManager;
    private final LevelManager levelManager;
    private final RewardManager rewardManager;
    private final Map<UUID, PlayerStats> statsByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastFlush = new HashMap<>();

    public PlayerStatsListener(LumiDiscord plugin, AfkManager afkManager, LevelManager levelManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        this.levelManager = levelManager;
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ensureRegistered(player);
        plugin.getStreakManager().handleLogin(player);
        plugin.getXpBufferManager().loadLinkedStatus(player.getUniqueId());
        rewardManager.loadClaimedRewards(player.getUniqueId(), () -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int level = levelManager.getPluginLevel(player.getUniqueId());
             Bukkit.getScheduler().runTask(plugin, () -> {
                rewardManager.grantMissingRewardsOnLogin(player, level);
                if (rewardManager.consumePendingOfflineLevelUp(player.getUniqueId())) {
                    player.sendMessage("§6[LumiDiscord] §aVotre progression de niveau a été synchronisée avec Discord.");
                }
            });
        }));
    }

    public void ensureRegistered(Player player) {
        UUID uuid = player.getUniqueId();
        // ensurePlayerExists est maintenant safe et ne throw plus d'exception
        plugin.getDatabaseService().ensurePlayerExists(uuid, player.getName());
        statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flushOne(uuid);
        statsByPlayer.remove(uuid);
        plugin.getStreakManager().handleLogout(uuid);
        plugin.getXpBufferManager().markAsUnlinked(uuid);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) { addBlocksBroken(event.getPlayer().getUniqueId()); afkManager.markActive(event.getPlayer()); }
    @EventHandler
    public void onPlace(BlockPlaceEvent event) { addBlocksPlaced(event.getPlayer().getUniqueId()); afkManager.markActive(event.getPlayer()); }
    @EventHandler
    public void onDeath(PlayerDeathEvent event) { addDeath(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY() && event.getFrom().getZ() == event.getTo().getZ()) return;
        if (afkManager.isAfkManual(player)) afkManager.forceDeactivateAfk(player);
        afkManager.markActive(player);
        PlayerStats stats = statsByPlayer.computeIfAbsent(player.getUniqueId(), id -> new PlayerStats(id.toString()));
        stats.setDistanceTraveled(stats.getDistanceTraveled() + event.getFrom().distance(event.getTo()));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (afkManager.isAfkManual(player)) afkManager.forceDeactivateAfk(player);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        afkManager.markActive(event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Monster && event.getEntity().getKiller() != null) {
            addMobKill(event.getEntity().getKiller().getUniqueId());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        afkManager.markActive(player);
        addItemsCrafted(player.getUniqueId(), event.getRecipe().getResult().getAmount());
    }

    @EventHandler
    public void onStatIncrement(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() == Statistic.JUMP) {
            addJump(event.getPlayer().getUniqueId());
        }
    }

    public void tickPlaytime() {
        for (UUID uuid : new HashMap<>(statsByPlayer).keySet()) {
            PlayerStats stats = statsByPlayer.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || afkManager.isAfk(player)) continue;
            stats.setPlaytimeSeconds(stats.getPlaytimeSeconds() + 1);
        }
    }

    public void flushIfNeeded() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new HashMap<>(statsByPlayer).keySet()) {
            Long last = lastFlush.get(uuid);
            if (last == null || now - last >= plugin.getPluginConfig().getInt("xp.flush-interval-seconds") * 1000L) {
                flushOne(uuid);
                lastFlush.put(uuid, now);
            }
        }
    }

    public void flushNow() { for (UUID uuid : new HashMap<>(statsByPlayer).keySet()) flushOne(uuid); }
    private void addBlocksBroken(UUID uuid) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setBlocksBroken(stats.getBlocksBroken() + 1); }
    private void addBlocksPlaced(UUID uuid) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setBlocksPlaced(stats.getBlocksPlaced() + 1); }
    private void addDeath(UUID uuid) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setDeaths(stats.getDeaths() + 1); }
    private void addMobKill(UUID uuid) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setMobsKilled(stats.getMobsKilled() + 1); }
    private void addItemsCrafted(UUID uuid, int amount) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setItemsCrafted(stats.getItemsCrafted() + amount); }
    private void addJump(UUID uuid) { PlayerStats stats = statsByPlayer.computeIfAbsent(uuid, id -> new PlayerStats(id.toString())); stats.setJumps(stats.getJumps() + 1); }

    private void flushOne(UUID uuid) {
        PlayerStats stats = statsByPlayer.get(uuid);
        if (stats == null) return;
        long pt = stats.getPlaytimeSeconds(), bb = stats.getBlocksBroken(), bp = stats.getBlocksPlaced();
        double dt = stats.getDistanceTraveled(); int de = stats.getDeaths(), mk = stats.getMobsKilled();
        long ic = stats.getItemsCrafted(), ju = stats.getJumps();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = plugin.getDatabaseService().getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player_stats (uuid, playtime_seconds, blocks_broken, blocks_placed, distance_traveled, deaths, items_crafted, mobs_killed, jumps) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE playtime_seconds = playtime_seconds + VALUES(playtime_seconds), blocks_broken = blocks_broken + VALUES(blocks_broken), blocks_placed = blocks_placed + VALUES(blocks_placed), distance_traveled = distance_traveled + VALUES(distance_traveled), deaths = deaths + VALUES(deaths), items_crafted = items_crafted + VALUES(items_crafted), mobs_killed = mobs_killed + VALUES(mobs_killed), jumps = jumps + VALUES(jumps)")) {
                    statement.setString(1, uuid.toString()); statement.setLong(2, pt); statement.setLong(3, bb); statement.setLong(4, bp);
                    statement.setDouble(5, dt); statement.setInt(6, de); statement.setLong(7, ic); statement.setInt(8, mk); statement.setLong(9, ju);
                    statement.executeUpdate();
                }
            } catch (SQLException ignored) {}
        });
        if (Bukkit.getPlayer(uuid) == null) statsByPlayer.remove(uuid);
        else { stats.setPlaytimeSeconds(0); stats.setBlocksBroken(0); stats.setBlocksPlaced(0); stats.setDistanceTraveled(0.0); stats.setDeaths(0); stats.setItemsCrafted(0); stats.setMobsKilled(0); stats.setJumps(0); }
    }
}