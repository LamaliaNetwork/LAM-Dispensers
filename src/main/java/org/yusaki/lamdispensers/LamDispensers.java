package org.yusaki.lamdispensers;

import org.bukkit.Bukkit;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.yusaki.lib.YskLib;

import static org.bukkit.Bukkit.getPluginManager;

public final class LamDispensers extends SimplePlugin {

    private YskLib yskLib;
    private YskLibWrapper wrapper;

    @Override
    public void onPluginStart() {
        yskLib = (YskLib) getPluginManager().getPlugin("YskLib");
        wrapper = new YskLibWrapper(this, yskLib);

        // Register the new DispenserPlacementHandler
        DispenserPlacementHandler handler = new DispenserPlacementHandler(this);
        registerEvents(handler);

        wrapper.logDebug("LamDispensers enabled!");
    }

    @Override
    public void onPluginStop() {
        wrapper.logDebug("LamDispensers disabled!");
    }
}
