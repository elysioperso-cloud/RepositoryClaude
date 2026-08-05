package fr.timeo.lumidiscord;

import fr.timeo.lumidiscord.api.HttpApiService;
import fr.timeo.lumidiscord.config.PluginConfig;
import fr.timeo.lumidiscord.database.DatabaseService;
import fr.timeo.lumidiscord.discord.DiscordBotService;
import fr.timeo.lumidiscord.minecraft.commands.*;
import fr.timeo.lumidiscord.minecraft.enchants.EnchantmentManager;
import fr.timeo.lumidiscord.minecraft.enchants.VillagerListener;
import fr.timeo.lumidiscord.minecraft.listeners.AnvilListener;
import fr.timeo.lumidiscord.minecraft.listeners.PlayerStatsListener;
import fr.timeo.lumidiscord.minecraft.listeners.TeamListener;
import fr.timeo.lumidiscord.minecraft.listeners.TeamGuiListener;
import fr.timeo.lumidiscord.minecraft.managers.*;
import fr.timeo.lumidiscord.minecraft.team.TeamCommand;
import fr.timeo.lumidiscord.minecraft.team.TeamManager;
import fr.timeo.lumidiscord.minecraft.war.WarCommand;
import fr.timeo.lumidiscord.minecraft.war.WarListener;
import fr.timeo.lumidiscord.minecraft.war.WarManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LumiDiscord extends JavaPlugin {

    private PluginConfig pluginConfig;
    private DatabaseService databaseService;
    private DiscordBotService discordBotService;
    private HttpApiService httpApiService;
    private HomeManager homeManager;
    private AfkManager afkManager;
    private XpBufferManager xpBufferManager;
    private LevelManager levelManager;
    private RewardManager rewardManager;
    private StreakManager streakManager;
    private PlayerStatsListener playerStatsListener;
    private TeamManager teamManager;
    private TeamListener teamListener;
    private TeamCommand teamCommand;
    private WarManager warManager;
    private DayCountManager dayCountManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        databaseService = new DatabaseService(this);
        databaseService.initialize();

        teamManager = new TeamManager(this);
        teamManager.initialize();

        warManager = new WarManager(this, teamManager);
        dayCountManager = new DayCountManager(this);
        afkManager = new AfkManager(this);

        homeManager = new HomeManager(this);
        homeManager.loadHomes();

        levelManager = new LevelManager(this);
        rewardManager = new RewardManager(this, levelManager);
        streakManager = new StreakManager(this);
        xpBufferManager = new XpBufferManager(this, levelManager, rewardManager);
        playerStatsListener = new PlayerStatsListener(this, afkManager, levelManager, rewardManager);

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerStatsListener.ensureRegistered(player);
            rewardManager.loadClaimedRewards(player.getUniqueId(), null);
            xpBufferManager.loadState(player.getUniqueId());
            xpBufferManager.loadLinkedStatus(player.getUniqueId());
            streakManager.loadStreak(player.getUniqueId());
        }

        registerCommands();
        registerListeners();
        startSchedulers();

        if (pluginConfig.isDiscordEnabled()) {
            discordBotService = new DiscordBotService(this, pluginConfig, databaseService, xpBufferManager, levelManager);
            discordBotService.start();
        }

        if (pluginConfig.isHttpEnabled()) {
            httpApiService = new HttpApiService(this, pluginConfig, databaseService);
            httpApiService.start();
        }

        getLogger().info("LumiDiscord enabled successfully.");
    }

    @Override
    public void onDisable() {
        String message = ChatColor.RED + "" + ChatColor.BOLD + "Serveur fermé par un administrateur";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer(message);
        }

        if (xpBufferManager != null) xpBufferManager.flushNow();
        if (playerStatsListener != null) playerStatsListener.flushNow();
        if (httpApiService != null) httpApiService.stop();
        if (discordBotService != null) discordBotService.stop();
        if (databaseService != null) databaseService.shutdown();
        if (homeManager != null) homeManager.saveHomes();
    }

    public void reloadPlugin() {
        getLogger().info("Reloading LumiDiscord...");
        if (xpBufferManager != null) xpBufferManager.flushNow();
        if (playerStatsListener != null) playerStatsListener.flushNow();
        if (httpApiService != null) httpApiService.stop();
        if (discordBotService != null) discordBotService.stop();

        reloadConfig();
        pluginConfig.load();
        if (warManager != null) warManager.loadConfig();

        if (pluginConfig.isDiscordEnabled()) {
            discordBotService = new DiscordBotService(this, pluginConfig, databaseService, xpBufferManager, levelManager);
            discordBotService.start();
        } else {
            discordBotService = null;
        }

        if (pluginConfig.isHttpEnabled()) {
            httpApiService = new HttpApiService(this, pluginConfig, databaseService);
            httpApiService.start();
        } else {
            httpApiService = null;
        }
        getLogger().info("LumiDiscord reloaded successfully.");
    }

    private void registerCommands() {
        safeRegister("linkdiscord", new LinkDiscordCommand(this, databaseService));
        safeRegister("xpdiscord", new XpDiscordCommand(this, databaseService, levelManager));
        safeRegister("givexp", new GiveXpCommand(this, levelManager, rewardManager));
        safeRegister("ungivexp", new UnGiveXpCommand(this, levelManager, rewardManager));
        safeRegister("xptop", new XpTopCommand(this));
        safeRegister("sethome", new SetHomeCommand(this, homeManager, levelManager));
        
        HomeCommand homeCmd = new HomeCommand(this, homeManager, levelManager);
        safeRegister("home", homeCmd);
        Bukkit.getPluginManager().registerEvents(homeCmd, this);
        
        safeRegister("spawn", new SpawnCommand(this));
        safeRegister("delhome", new DelHomeCommand(this, homeManager));
        
        teamCommand = new TeamCommand(this, teamManager);
        safeRegister("team", teamCommand);

        WarCommand warCommand = new WarCommand(this, warManager, teamManager);
        safeRegister("war", warCommand);
        safeRegister("assault", warCommand);

        safeRegister("daycount", new DayCountCommand(dayCountManager));
        safeRegister("afk", new AfkCommand(afkManager));
        
        EnchantmentManager em = new EnchantmentManager(this);
        safeRegister("enchantadmin", new GetEnchantCommand(em));
        safeRegister("adminxp", new AdminXpCommand(this));
        safeRegister("teamadmin", new TeamAdminCommand(this));
    }

    private void safeRegister(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            if (executor instanceof org.bukkit.command.CommandExecutor) {
                cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
            }
            if (executor instanceof org.bukkit.command.TabCompleter) {
                cmd.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        } else {
            getLogger().warning("Command /" + name + " not found in plugin.yml!");
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(playerStatsListener, this);
        teamListener = new TeamListener(this, teamManager);
        Bukkit.getPluginManager().registerEvents(teamListener, this);
        Bukkit.getPluginManager().registerEvents(teamCommand, this);
        Bukkit.getPluginManager().registerEvents(new WarListener(warManager, teamManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamGuiListener(this), this);
        
        EnchantmentManager em = new EnchantmentManager(this);
        Bukkit.getPluginManager().registerEvents(em, this);
        Bukkit.getPluginManager().registerEvents(new VillagerListener(), this);
        Bukkit.getPluginManager().registerEvents(new AnvilListener(), this);
    }

    private void startSchedulers() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (afkManager != null) afkManager.tick();
            if (xpBufferManager != null) xpBufferManager.flushIfNeeded();
            if (playerStatsListener != null) playerStatsListener.flushIfNeeded();
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (xpBufferManager != null) xpBufferManager.tickPlaytime();
            if (playerStatsListener != null) playerStatsListener.tickPlaytime();
        }, 20L, 20L);
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public DatabaseService getDatabaseService() { return databaseService; }
    public DiscordBotService getDiscordBotService() { return discordBotService; }
    public HomeManager getHomeManager() { return homeManager; }
    public AfkManager getAfkManager() { return afkManager; }
    public XpBufferManager getXpBufferManager() { return xpBufferManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public StreakManager getStreakManager() { return streakManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public WarManager getWarManager() { return warManager; }
    public DayCountManager getDayCountManager() { return dayCountManager; }
}