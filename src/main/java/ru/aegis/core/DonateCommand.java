package ru.aegis.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /donate - opens the donate menu. */
public final class DonateCommand implements CommandExecutor {

    private final AegisCore plugin;

    public DonateCommand(AegisCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        plugin.donate().open(p);
        return true;
    }
}
