package fr.timeo.lumidiscord.minecraft.listeners;

import fr.timeo.lumidiscord.minecraft.managers.VillagerTradeManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class VillagerListener implements Listener {

    private final VillagerTradeManager villagerTradeManager;

    public VillagerListener(VillagerTradeManager villagerTradeManager) {
        this.villagerTradeManager = villagerTradeManager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();

            // Vérifier si c'est un libraire
            if (villager.getProfession() == org.bukkit.entity.Villager.Profession.LIBRARIAN) {
                // Ajouter les trades personnalisés
                villagerTradeManager.addEnchantmentTradesToVillager(villager);
            }
        }
    }
}

