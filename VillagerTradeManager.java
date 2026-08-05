package fr.timeo.lumidiscord.minecraft.enchants;

import fr.timeo.lumidiscord.LumiDiscord;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnchantmentManager implements Listener {

    private final LumiDiscord plugin;

    public EnchantmentManager(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        boolean hasSmelt = lore.stream().anyMatch(line -> line.contains("Smelt"));
        boolean hasVeinMiner = lore.stream().anyMatch(line -> line.contains("Vein Miner"));

        if (hasSmelt) handleSmelt(event);
        if (hasVeinMiner) handleVeinMiner(event, player, item);
    }

    private void handleSmelt(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material drop = null;

        switch (block.getType()) {
            case RAW_IRON_BLOCK -> drop = Material.IRON_BLOCK;
            case RAW_GOLD_BLOCK -> drop = Material.GOLD_BLOCK;
            case RAW_COPPER_BLOCK -> drop = Material.COPPER_BLOCK;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> drop = Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> drop = Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> drop = Material.COPPER_INGOT;
        }

        if (drop != null) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(drop));
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.5f);
        }
    }

    private void handleVeinMiner(BlockBreakEvent event, Player player, ItemStack tool) {
        Block startBlock = event.getBlock();
        if (!isOre(startBlock.getType())) return;

        Set<Block> vein = new HashSet<>();
        findVein(startBlock, startBlock.getType(), vein, 64);

        if (vein.size() <= 1) return;

        for (Block b : vein) {
            if (b.equals(startBlock)) continue;
            b.breakNaturally(tool);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
    }

    private void findVein(Block block, Material target, Set<Block> vein, int max) {
        if (vein.size() >= max || block.getType() != target || vein.contains(block)) return;
        vein.add(block);
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            findVein(block.getRelative(face), target, vein, max);
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.contains("_ORE") || name.contains("RAW_");
    }
}