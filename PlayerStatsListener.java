package fr.timeo.lumidiscord.minecraft.commands;

import fr.timeo.lumidiscord.minecraft.managers.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelHomeCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public DelHomeCommand(fr.timeo.lumidiscord.LumiDiscord plugin, HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }
        homeManager.deleteHome(player.getUniqueId());
        player.sendMessage("§aHome supprimé.");
        return true;
    }
}
