package fr.timeo.lumidiscord.minecraft.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExistingVillagerInitializer {

    private final JavaPlugin plugin;
    private final VillagerTradeManager villagerTradeManager;

    public ExistingVillagerInitializer(JavaPlugin plugin, VillagerTradeManager villagerTradeManager) {
        this.plugin = plugin;
        this.villagerTradeManager = villagerTradeManager;
    }

    /**
     * Initialise tous les villageois libraires dans les chunks chargés
     */
    public void initializeExistingVillagers() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getWorlds().forEach(world -> {
                world.getEntitiesByClass(Villager.class).forEach(villager -> {
                    if (villager.getProfession() == Villager.Profession.LIBRARIAN) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            villagerTradeManager.addEnchantmentTradesToVillager(villager);
                        });
                    }
                });
            });
        });
    }
}


