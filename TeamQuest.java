package fr.timeo.lumidiscord.minecraft.listeners;

import fr.timeo.lumidiscord.LumiDiscord;
import fr.timeo.lumidiscord.minecraft.team.*;
import fr.timeo.lumidiscord.minecraft.team.gui.TeamPanelGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class TeamGuiListener implements Listener {

    private final LumiDiscord plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TeamGuiListener(LumiDiscord plugin) {
        this.plugin = plugin;
    }

    private void delayedDrawPage(TeamPanelGui gui, int page, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() { gui.drawPage(page, player); }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamPanelGui gui)) return;
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        TeamData team = gui.getTeam();

        // GESTION DU COFFRE (Page 1)
        if (gui.getCurrentPage() == 1 && slot >= 18 && slot < 54) {
            int chestSlot = slot - 18;
            if (chestSlot < team.getChestSlots()) {
                event.setCancelled(false); // Autoriser déplacement d'item
                return;
            } else {
                event.setCancelled(true);
                // DÉBLOCAGE AVEC NETHERITE
                if (player.getInventory().contains(Material.NETHERITE_BLOCK, 1)) {
                    player.getInventory().removeItem(new ItemStack(Material.NETHERITE_BLOCK, 1));
                    team.setChestSlots(team.getChestSlots() + 1);
                    plugin.getTeamManager().persistTeam(team);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    player.sendMessage("§a[Team] §eUn nouveau slot de coffre a été débloqué !");
                    delayedDrawPage(gui, 1, player);
                } else {
                    player.sendMessage("§cIl te faut 1 Bloc de Netherite pour débloquer ce slot !");
                }
                return;
            }
        }
        
        // Autoriser les clics dans son inventaire uniquement en mode coffre
        if (event.getClickedInventory() == player.getInventory()) {
            if (gui.getCurrentPage() == 1) event.setCancelled(false);
            else event.setCancelled(true);
            return;
        }

        event.setCancelled(true); // Bloquer tout le reste par défaut

        // NAVBAR
        if (slot >= 1 && slot <= 7) {
            if (slot == 1) delayedDrawPage(gui, 0, player);
            else if (slot == 2) delayedDrawPage(gui, 1, player);
            else if (slot == 3) delayedDrawPage(gui, 2, player);
            else if (slot == 5) delayedDrawPage(gui, 3, player);
            else if (slot == 6) delayedDrawPage(gui, 4, player);
            else if (slot == 7) delayedDrawPage(gui, 9, player);
            return;
        }

        if (gui.getCurrentPage() == 4 && slot == 53) {
            player.sendMessage("§8§iSoon...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }

        if (gui.getCurrentPage() == 0 && slot == 40) {
            player.sendMessage("§8§iSoon...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamPanelGui gui)) return;
        if (gui.getCurrentPage() == 1) { // Page du coffre
            TeamData team = gui.getTeam();
            for (int i = 0; i < 36; i++) {
                if (i < team.getChestSlots()) {
                    ItemStack item = event.getInventory().getItem(i + 18);
                    if (item == null || item.getType() == Material.AIR) team.getChestItems().remove(i);
                    else team.getChestItems().put(i, item.clone());
                }
            }
            plugin.getTeamManager().persistTeam(team); // SAUVEGARDE RÉELLE
        }
    }
}