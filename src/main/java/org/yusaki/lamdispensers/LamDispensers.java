package org.yusaki.lamdispensers;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.yusaki.lamdispensers.commands.ReloadCommand;
import org.yusaki.lib.YskLib;

import static org.bukkit.Bukkit.getPluginManager;

import java.util.ArrayList;
import java.util.List;

public final class LamDispensers extends JavaPlugin {

    private YskLib yskLib;
    private YskLibWrapper wrapper;
    private DispenserPlacementHandler placementHandler;
    private DispenserMiningHandler miningHandler;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        yskLib = (YskLib) getPluginManager().getPlugin("YskLib");
        wrapper = new YskLibWrapper(this, yskLib);

        // Register command
        ReloadCommand reloadCommand = new ReloadCommand(this);
        
        // Get the primary command name from config
        List<String> aliases = getConfig().getStringList("command.aliases");
        String primaryCommand = aliases.isEmpty() ? "lamdispensers" : aliases.get(0);
        
        // Create the command
        PluginCommand command = getCommand("lamdispensers");
        getServer().getCommandMap().register(primaryCommand, command);
        
        // Set up the command
        command.setExecutor(reloadCommand);
        command.setTabCompleter(reloadCommand);
        
        if (aliases.size() > 1) {
            command.setAliases(aliases.subList(1, aliases.size()));
        }

        // Register handlers based on config
        registerHandlers();

        wrapper.logDebug("LamDispensers enabled with command: " + primaryCommand + 
                        " and aliases: " + (aliases.size() > 1 ? aliases.subList(1, aliases.size()) : "none"));

        miningHandler = new DispenserMiningHandler(this);
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(this, miningHandler);
        getCommand("ldperf").setExecutor(performanceMonitor);
        getCommand("ldperf").setTabCompleter(performanceMonitor);
    }

    @Override
    public void onDisable() {
        unregisterHandlers();
        wrapper.logDebug("LamDispensers disabled!");
    }

    public YskLibWrapper getWrapper() {
        return wrapper;
    }

    private void registerHandlers() {
        unregisterHandlers(); // Clean up any existing handlers first

        if (getConfig().getBoolean("modules.placement", true)) {
            placementHandler = new DispenserPlacementHandler(this);
            getServer().getPluginManager().registerEvents(placementHandler, this);
            wrapper.logDebug("Placement module enabled!");
        }

        if (getConfig().getBoolean("modules.mining", true)) {
            miningHandler = new DispenserMiningHandler(this);
            getServer().getPluginManager().registerEvents(miningHandler, this);
            wrapper.logDebug("Mining module enabled!");
        }
    }

    private void unregisterHandlers() {
        if (placementHandler != null) {
            HandlerList.unregisterAll(placementHandler);
            placementHandler = null;
        }
        if (miningHandler != null) {
            HandlerList.unregisterAll(miningHandler);
            miningHandler = null;
        }
    }

    /**
     * Reloads the plugin configuration
     */
    public void reloadPlugin() {
        reloadConfig();
        
        // Update command aliases
        List<String> aliases = getConfig().getStringList("command.aliases");
        String primaryCommand = aliases.isEmpty() ? "lamdispensers" : aliases.get(0);
        
        if (aliases.size() > 1) {
            getCommand(primaryCommand).setAliases(aliases.subList(1, aliases.size()));
        } else {
            getCommand(primaryCommand).setAliases(new ArrayList<>());
        }
        
        registerHandlers();
        wrapper.logDebug("Configuration reloaded!");
    }
}
