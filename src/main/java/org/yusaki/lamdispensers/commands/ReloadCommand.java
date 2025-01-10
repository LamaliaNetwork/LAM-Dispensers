package org.yusaki.lamdispensers.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.yusaki.lamdispensers.LamDispensers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReloadCommand implements CommandExecutor, TabCompleter {
    private final LamDispensers plugin;

    public ReloadCommand(LamDispensers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lamdispensers.reload")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§cUsage: /" + label + " reload");
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage("§aLamDispensers configuration reloaded!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lamdispensers.reload")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("reload");
            }
        }

        return new ArrayList<>();
    }
} 