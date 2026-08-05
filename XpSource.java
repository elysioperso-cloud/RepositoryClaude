package fr.timeo.lumidiscord.minecraft.managers;

import fr.timeo.lumidiscord.minecraft.enchants.EnchantmentManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VillagerTradeManager {

    private final EnchantmentManager enchantmentManager;
    private final Random random = new Random();

    public VillagerTradeManager(EnchantmentManager enchantmentManager) {
        this.enchantmentManager = enchantmentManager;
    }

    public void addEnchantmentTradesToVillager(Villager villager) {
        // S'assurer que le villageois est un libraire
        if (villager.getProfession() != Villager.Profession.LIBRARIAN) return;

        // 10% de chance d'ajouter un trade custom
        if (random.nextDouble() > 0.10) return;

        // Choisir aléatoirement Smelt ou Vein Miner
        boolean isSmelt = random.nextBoolean();
        String enchantName = isSmelt ? "Smelt" : "Vein Miner";
        String enchantDesc = isSmelt ? "Cuit automatiquement les minerais minés." : "Mine tout le filon de minerais.";

        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = enchantedBook.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + enchantName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + enchantDesc);
        meta.setLore(lore);
        enchantedBook.setItemMeta(meta);

        // Prix 2 fois supérieur à la normale (entre 30 et 64 émeraudes)
        int price = (30 + random.nextInt(35)) * 2; // Prix doublé
        MerchantRecipe recipe = new MerchantRecipe(enchantedBook, 1); // 1 utilisation
        recipe.addIngredient(new ItemStack(Material.EMERALD, price));
        recipe.addIngredient(new ItemStack(Material.BOOK)); // Nécessite un livre normal

        // Ajouter la recette au villageois
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        recipes.add(recipe);
        villager.setRecipes(recipes);
    }
}