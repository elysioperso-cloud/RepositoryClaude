package fr.timeo.lumidiscord.minecraft.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;

public class AnvilListener implements Listener {

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        
        // Supprime la limite de 40 niveaux (Trop coûteux !)
        // En mettant la limite au maximum possible de Java.
        inventory.setMaximumRepairCost(Integer.MAX_VALUE);
    }
}