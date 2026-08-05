package fr.timeo.lumidiscord.minecraft.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VillagerListener implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onVillagerTradeAcquire(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getProfession() != Villager.Profession.LIBRARIAN) return;

        // 10% de chance d'avoir un enchantement custom
        if (random.nextDouble() > 0.10) return;

        boolean isSmelt = random.nextBoolean();
        String name = isSmelt ? "Smelt" : "Vein Miner";
        String desc = isSmelt ? "Cuit automatiquement les minerais minés." : "Mine tout le filon de minerais.";

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + desc);
        meta.setLore(lore);
        book.setItemMeta(meta);

        // Prix élevé (entre 30 et 64 émeraudes)
        int price = 30 + random.nextInt(35);
        MerchantRecipe recipe = new MerchantRecipe(book, 12);
        recipe.addIngredient(new ItemStack(Material.EMERALD, price));
        recipe.addIngredient(new ItemStack(Material.BOOK));

        event.setRecipe(recipe);
    }
}