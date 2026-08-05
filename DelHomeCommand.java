package fr.timeo.lumidiscord.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PluginConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isDiscordEnabled() {
        return !getString("discord.token").isBlank();
    }

    public boolean isHttpEnabled() {
        return config.getBoolean("http.enabled", true);
    }

    public String getString(String path) {
        return config.getString(path, "");
    }

    public int getInt(String path) {
        return config.getInt(path, 0);
    }

    public long getLong(String path) {
        return config.getLong(path, 0L);
    }

    public double getDouble(String path) {
        return config.getDouble(path, 0.0);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path, false);
    }

    public FileConfiguration getRaw() {
        return config;
    }
}
