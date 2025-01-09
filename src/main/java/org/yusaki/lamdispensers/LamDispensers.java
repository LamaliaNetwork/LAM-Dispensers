package org.yusaki.lamdispensers;

import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;

import static org.bukkit.Bukkit.getPluginManager;

public final class LamDispensers extends JavaPlugin {

    private YskLib yskLib;
    private YskLibWrapper wrapper;

    @Override
    public void onEnable() {
        yskLib = (YskLib) getPluginManager().getPlugin("YskLib");
        wrapper = new YskLibWrapper(this, yskLib);

        // Register the new DispenserPlacementHandler
        DispenserPlacementHandler handler = new DispenserPlacementHandler(this);
        getServer().getPluginManager().registerEvents(handler, this);

        DispenserMiningHandler miningHandler = new DispenserMiningHandler(this);
        getServer().getPluginManager().registerEvents(miningHandler, this);

        wrapper.logDebug("LamDispensers enabled!");
    }

    @Override
    public void onDisable() {
        wrapper.logDebug("LamDispensers disabled!");
    }
}
