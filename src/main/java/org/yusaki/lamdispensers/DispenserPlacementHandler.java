package org.yusaki.lamdispensers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

public class DispenserPlacementHandler implements Listener {

    private final LamDispensers plugin;
    private final Random random = new Random();
    private final Set<Material> replaceable = new HashSet<>();
    private final Set<Material> placeableBlocks = new HashSet<>();

    public DispenserPlacementHandler(LamDispensers plugin) {
        this.plugin = plugin;
        initializeReplaceableBlocks();
        initializePlaceableBlocks();
    }

    private void initializeReplaceableBlocks() {
        replaceable.add(Material.AIR);
        replaceable.add(Material.WATER);
        replaceable.add(Material.LAVA);
    }

    private void initializePlaceableBlocks() {
        // Add all solid blocks
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isSolid()) {
                placeableBlocks.add(material);
            }
        }

        // Remove TNT and Shulker Boxes
        placeableBlocks.remove(Material.TNT);
        for (Material material : Material.values()) {
            if (material.name().endsWith("SHULKER_BOX")) {
                placeableBlocks.remove(material);
            }
        }

        // Add specific transparent blocks
        Material[] transparentBlocks = {
                // Carpets
                Material.WHITE_CARPET, Material.ORANGE_CARPET, Material.MAGENTA_CARPET,
                Material.LIGHT_BLUE_CARPET, Material.YELLOW_CARPET, Material.LIME_CARPET,
                Material.PINK_CARPET, Material.GRAY_CARPET, Material.LIGHT_GRAY_CARPET,
                Material.CYAN_CARPET, Material.PURPLE_CARPET, Material.BLUE_CARPET,
                Material.BROWN_CARPET, Material.GREEN_CARPET, Material.RED_CARPET,
                Material.BLACK_CARPET,

                // Rails
                Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL,
                Material.ACTIVATOR_RAIL,

                // Saplings
                Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
                Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
                Material.MANGROVE_PROPAGULE,Material.CHERRY_SAPLING,

                // Other common transparent blocks
                Material.TORCH, Material.REDSTONE_TORCH, Material.LEVER, Material.STONE_BUTTON,
                Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
                Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
                Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.REPEATER,
                Material.COMPARATOR, Material.REDSTONE_WIRE
        };

        placeableBlocks.addAll(Set.of(transparentBlocks));
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        Block dispenserBlock = event.getBlock();
        if (!(dispenserBlock.getState() instanceof Dispenser)) return;

        Dispenser dispenser = (Dispenser) dispenserBlock.getState();
        if (!(dispenserBlock.getBlockData() instanceof Directional)) return;

        Directional directional = (Directional) dispenserBlock.getBlockData();
        BlockFace facing = directional.getFacing();
        Block frontBlock = dispenserBlock.getRelative(facing);

        if (!replaceable.contains(frontBlock.getType())) return;

        ItemStack dispensedItem = event.getItem();
        if (!placeableBlocks.contains(dispensedItem.getType())) return;

        event.setCancelled(true);

        Location loc = dispenserBlock.getLocation();
        scheduleFoliaCompatibleTask(loc, () -> {
            // Re-check conditions
            if (!replaceable.contains(frontBlock.getType())) return;

            ItemStack selectedItem = getRandomItemFromDispenser(dispenser);
            if (selectedItem != null && placeableBlocks.contains(selectedItem.getType())) {
                if (removeItem(dispenser, selectedItem)) {
                    Material originalType = frontBlock.getType();
                    frontBlock.setType(selectedItem.getType());
                    //plugin.getLogger().info("Dispenser placed a " + selectedItem.getType() + " block, replacing " + originalType);
                }
            }
        });
    }

    private ItemStack getRandomItemFromDispenser(Dispenser dispenser) {
        ItemStack[] contents = dispenser.getInventory().getContents();
        int[] filledSlots = new int[9];
        int filledCount = 0;

        for (int i = 0; i < 9; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                filledSlots[filledCount++] = i;
            }
        }

        if (filledCount == 0) return null;

        int selectedSlot = filledSlots[random.nextInt(filledCount)];
        return contents[selectedSlot];
    }

    private boolean removeItem(Dispenser dispenser, ItemStack item) {
        ItemStack[] contents = dispenser.getInventory().getContents();
        int slot = dispenser.getInventory().first(item);
        if (slot >= 0) {
            if (contents[slot].getAmount() > 1) {
                contents[slot].setAmount(contents[slot].getAmount() - 1);
            } else {
                contents[slot] = null;
            }
            dispenser.getInventory().setContents(contents);
            return true;
        }
        return false;
    }

    private void scheduleFoliaCompatibleTask(Location location, Runnable task) {
        try {
            plugin.getServer().getRegionScheduler().execute(plugin, location, task);
        } catch (NoSuchMethodError e) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
