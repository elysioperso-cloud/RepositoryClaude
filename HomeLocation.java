package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.models.HomeLocation;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final LumiDiscord plugin;
    private final Map<UUID, HomeLocation> homes = new HashMap<>();
    private final File homesFile;

    public HomeManager(LumiDiscord plugin) {
        this.plugin = plugin;
        this.homesFile = new File(plugin.getDataFolder(), "homes.yml");
    }

    public void loadHomes() {
        if (!homesFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(homesFile);
        var section = config.getConfigurationSection("homes");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            String world = config.getString("homes." + key + ".world", "world");
            double x = config.getDouble("homes." + key + ".x");
            double y = config.getDouble("homes." + key + ".y");
            double z = config.getDouble("homes." + key + ".z");
            float yaw = (float) config.getDouble("homes." + key + ".yaw", 0.0);
            float pitch = (float) config.getDouble("homes." + key + ".pitch", 0.0);
            homes.put(uuid, new HomeLocation(world, x, y, z, yaw, pitch));
        }
    }

    public void saveHomes() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, HomeLocation> entry : homes.entrySet()) {
            HomeLocation location = entry.getValue();
            config.set("homes." + entry.getKey() + ".world", location.toLocation() != null ? location.toLocation().getWorld().getName() : "world");
            config.set("homes." + entry.getKey() + ".x", location.toLocation() != null ? location.toLocation().getX() : 0.0);
            config.set("homes." + entry.getKey() + ".y", location.toLocation() != null ? location.toLocation().getY() : 0.0);
            config.set("homes." + entry.getKey() + ".z", location.toLocation() != null ? location.toLocation().getZ() : 0.0);
            config.set("homes." + entry.getKey() + ".yaw", location.toLocation() != null ? location.toLocation().getYaw() : 0.0);
            config.set("homes." + entry.getKey() + ".pitch", location.toLocation() != null ? location.toLocation().getPitch() : 0.0);
        }
        try {
            config.save(homesFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save homes file: " + exception.getMessage());
        }
    }

    public void setHome(UUID uuid, HomeLocation location) {
        homes.put(uuid, location);
        saveHomes();
    }

    public void deleteHome(UUID uuid) {
        homes.remove(uuid);
        saveHomes();
    }

    public HomeLocation getHome(UUID uuid) {
        return homes.get(uuid);
    }
}
