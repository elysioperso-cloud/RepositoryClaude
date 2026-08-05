package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.minecraft.enchants.EnchantmentManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GetEnchantCommand implements CommandExecutor, TabCompleter {

    private final EnchantmentManager enchantmentManager;

    public GetEnchantCommand(EnchantmentManager enchantmentManager) {
        this.enchantmentManager = enchantmentManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent utiliser cette commande!");
            return true;
        }

        if (!player.hasPermission("lumidiscord.enchant.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "Usage: /enchantadmin <smelt|veinminer>");
            return true;
        }

        String enchantType = args[0].toLowerCase();
        ItemStack pickaxe;

        if ("smelt".equals(enchantType)) {
            pickaxe = createCustomPickaxe("Smelt", "Cuit automatiquement les minerais minés.");
            player.sendMessage(ChatColor.GREEN + "Vous avez reçu une Pioche Smelt !");
        } else if ("veinminer".equals(enchantType) || "rootminer".equals(enchantType)) {
            pickaxe = createCustomPickaxe("Vein Miner", "Mine tout le filon de minerais.");
            player.sendMessage(ChatColor.GREEN + "Vous avez reçu une Pioche Vein Miner !");
        } else {
            player.sendMessage(ChatColor.RED + "Enchantement inconnu.");
            return true;
        }

        player.getInventory().addItem(pickaxe);
        return true;
    }

    private ItemStack createCustomPickaxe(String name, String desc) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Pioche de " + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + desc);
            lore.add("");
            lore.add(ChatColor.YELLOW + name); // Le tag que le manager détecte
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("smelt", "veinminer");
        }
        return new ArrayList<>();
    }
}