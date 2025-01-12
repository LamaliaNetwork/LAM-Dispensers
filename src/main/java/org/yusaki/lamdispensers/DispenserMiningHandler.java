package org.yusaki.lamdispensers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.ArrayList;

public class DispenserMiningHandler implements Listener {
    private final LamDispensers plugin;
    private final Set<Location> activeMiningOperations = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> activeDispenserTools = Collections.synchronizedSet(new HashSet<>());

    public DispenserMiningHandler(LamDispensers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent event) {
        if (event.isCancelled()) return;
        
        Block dispenserBlock = event.getBlock();
        if (!(dispenserBlock.getState() instanceof Dispenser)) return;
        
        ItemStack dispensedItem = event.getItem();
        if (!isTool(dispensedItem.getType())) return;
        
        // Cancel event early to prevent item ejection
        event.setCancelled(true);
        
        BlockFace facing = ((Directional) dispenserBlock.getBlockData()).getFacing();
        Block targetBlock = dispenserBlock.getRelative(facing);
        
        // Return after cancelling if there's no block to mine
        if (targetBlock.getType().isAir()) return;
        
        // Check if chunk is loaded
        if (!targetBlock.getChunk().isLoaded()) {
            event.setCancelled(true);
            return;
        }
        
        plugin.getServer().getRegionScheduler().run(plugin, targetBlock.getLocation(), (task) -> {
            try {
                // Recheck if chunk is still loaded
                if (!targetBlock.getChunk().isLoaded()) {
                    cleanupTracking(targetBlock.getLocation(), dispenserBlock.getLocation(), event.getItem());
                    return;
                }

                Dispenser dispenser = (Dispenser) dispenserBlock.getState();
                Location targetLoc = targetBlock.getLocation();
                
                if (activeMiningOperations.contains(targetLoc)) {
                    return;
                }

                // Find best tool asynchronously
                ItemStack bestTool = findBestTool(dispenser, targetBlock);
                if (bestTool == null) return;

                String dispenserToolKey = dispenserBlock.getLocation().toString() + ":" + bestTool.getType().name();
                if (activeDispenserTools.contains(dispenserToolKey)) {
                    return;
                }

                activeMiningOperations.add(targetLoc);
                activeDispenserTools.add(dispenserToolKey);
                startMining(dispenser, bestTool, targetBlock);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error in dispenser mining: " + e.getMessage());
                e.printStackTrace();
                // Cleanup on error
                cleanupTracking(targetBlock.getLocation(), dispenserBlock.getLocation(), event.getItem());
            }
        });
    }

    private ItemStack findBestTool(Dispenser dispenser, Block targetBlock) {
        ItemStack[] contents = dispenser.getInventory().getContents();
        ItemStack bestTool = null;
        float bestSpeed = -1;
        
        for (ItemStack item : contents) {
            if (item == null || !isTool(item.getType()) || !canMineBlock(item, targetBlock)) {
                continue;
            }
            
            String dispenserToolKey = dispenser.getLocation().toString() + ":" + item.getType().name();
            if (activeDispenserTools.contains(dispenserToolKey)) {
                continue;
            }
            
            float miningSpeed = calculateToolEfficiency(item, targetBlock);
            if (miningSpeed > bestSpeed) {
                bestSpeed = miningSpeed;
                bestTool = item;
            }
        }
        
        return bestTool;
    }

    private void startMining(Dispenser dispenser, ItemStack tool, Block targetBlock) {
        Location loc = targetBlock.getLocation();
        Material originalType = targetBlock.getType();
        ItemStack originalTool = tool.clone();
        
        // Check if chunk is loaded before starting mining
        if (!targetBlock.getChunk().isLoaded()) {
            cleanupTracking(loc, dispenser.getLocation(), tool);
            return;
        }
        
        float miningTicks = calculateMiningTicks(tool, targetBlock);
        
        if (miningTicks <= 0.05f) {
            plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
                if (!isValidMiningOperation(targetBlock, originalType, dispenser, originalTool)) {
                    cleanupTracking(loc, dispenser.getLocation(), tool);
                    return;
                }
                performInstantMining(dispenser, tool, targetBlock);
                cleanupTracking(loc, dispenser.getLocation(), tool);
            });
            return;
        }

        int animationTicks = Math.max((int) (miningTicks * 20), 2);
        
        plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
            if (!isValidMiningOperation(targetBlock, originalType, dispenser, originalTool)) {
                cleanupTracking(loc, dispenser.getLocation(), tool);
                return;
            }
            
            // Calculate number of animation steps (9 steps total, from 0.1 to 0.9)
            int steps = 9;
            int stepInterval = Math.max(1, animationTicks / steps);
            
            // Start with initial damage
            showMiningAnimation(targetBlock, 0.1f);
            
            // Schedule animation updates
            for (int i = 2; i <= steps; i++) {
                final float progress = i / (float) steps;
                plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (animTask) -> {
                    if (isValidMiningOperation(targetBlock, originalType, dispenser, originalTool)) {
                        showMiningAnimation(targetBlock, progress);
                    }
                }, stepInterval * (i - 1));
            }
            
            // Schedule the block break
            scheduleBlockBreak(loc, dispenser, tool, targetBlock, originalType, animationTicks);
            
            // Play digging sound periodically
            int soundInterval = Math.max(1, animationTicks / 4);
            for (int i = 1; i <= 4; i++) {
                final int stage = i;
                plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (soundTask) -> {
                    if (isValidMiningOperation(targetBlock, originalType, dispenser, originalTool)) {
                        targetBlock.getWorld().playSound(
                            loc,
                            targetBlock.getBlockData().getSoundGroup().getHitSound(),
                            1.0f,
                            0.8f
                        );
                    }
                }, soundInterval * i);
            }
        });
    }

    private void cleanupTracking(Location blockLoc, Location dispenserLoc, ItemStack tool) {
        // Clear any existing animation
        Block block = blockLoc.getBlock();
        showMiningAnimation(block, -1);
        
        // Remove from tracking sets
        activeMiningOperations.remove(blockLoc);
        activeDispenserTools.remove(dispenserLoc.toString() + ":" + tool.getType().name());
    }

    private void scheduleMiningAnimation(Location loc, Block block, Material originalType, 
                                       Dispenser dispenser, ItemStack originalTool, int stage, long delay) {
        if (delay <= 0) delay = 1;
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (task) -> {
            if (!isValidMiningOperation(block, originalType, dispenser, originalTool)) {
                cleanupTracking(loc, dispenser.getLocation(), originalTool);
                showMiningAnimation(block, -1); // Clear animation
                return;
            }
            showMiningAnimation(block, stage);
        }, delay);
    }

    private void scheduleBlockBreak(Location loc, Dispenser dispenser, ItemStack tool, 
                                  Block block, Material originalType, long delay) {
        Location originalLocation = block.getLocation().clone();
        String dispenserToolKey = dispenser.getLocation().toString() + ":" + tool.getType().name();
        ItemStack originalTool = tool.clone();
        
        if (delay <= 0) delay = 1;
        
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (task) -> {
            try {
                if (!isValidMiningOperation(block, originalType, dispenser, originalTool)) {
                    showMiningAnimation(block, -1); // Clear animation
                    return;
                }
                
                performInstantMining(dispenser, tool, block);
            } finally {
                activeMiningOperations.remove(originalLocation);
                activeDispenserTools.remove(dispenserToolKey);
            }
        }, delay);
    }

    private void performInstantMining(Dispenser dispenser, ItemStack pickaxe, Block block) {
        // Clear any existing animation first
        showMiningAnimation(block, -1);
        
        // Play break sound
        block.getWorld().playSound(
            block.getLocation(),
            block.getBlockData().getSoundGroup().getBreakSound(),
            1.0f,
            1.0f
        );
        
        // Drop block items naturally
        block.breakNaturally(pickaxe);
        
        // Handle pickaxe durability
        if (pickaxe.getType().getMaxDurability() > 0) {
            ItemStack[] contents = dispenser.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.equals(pickaxe)) {
                    int unbreakingLevel = item.getEnchantmentLevel(Enchantment.DURABILITY);
                    
                    boolean shouldTakeDamage = true;
                    if (unbreakingLevel > 0) {
                        double chance = 1.0 / (unbreakingLevel + 1);
                        shouldTakeDamage = Math.random() < chance;
                    }
                    
                    if (shouldTakeDamage) {
                        // Only apply 1 durability damage, removed the 10x multiplier
                        short newDurability = (short) (item.getDurability() + 1);
                        item.setDurability(newDurability);
                        
                        dispenser.getInventory().setItem(i, item);
                        
                        if (newDurability >= item.getType().getMaxDurability()) {
                            block.getWorld().playSound(
                                dispenser.getLocation(),
                                org.bukkit.Sound.ENTITY_ITEM_BREAK,
                                1.0f,
                                1.0f
                            );
                            dispenser.getInventory().setItem(i, null);
                        }
                    }
                    break;
                }
            }
        }
    }

    private void showMiningAnimation(Block block, float progress) {
        Location blockLoc = block.getLocation();
        World world = block.getWorld();
        
        // For negative values or completion, clear the animation
        float damage = progress < 0 ? 0.0f : Math.min(progress, 1.0f);
        
        // Send the animation packet to nearby players
        world.getPlayers().stream()
            .filter(player -> player.getLocation().distanceSquared(blockLoc) <= 1024)
            .forEach(player -> player.sendBlockDamage(blockLoc, damage));
    }

    private float calculateMiningTicks(ItemStack tool, Block block) {
        float hardness = block.getType().getHardness();
        if (hardness == 0) return 0.05f; // Instant break for zero hardness blocks
        
        boolean isCorrectTool = isCorrectToolForBlock(tool.getType(), block.getType());
        float speedMultiplier = getBaseBreakingSpeed(tool.getType(), block.getType());
        
        // If it's the correct tool and we can harvest it
        if (isCorrectTool) {
            // Apply efficiency enchantment
            int efficiencyLevel = tool.getEnchantmentLevel(Enchantment.DIG_SPEED);
            if (efficiencyLevel > 0) {
                speedMultiplier += (efficiencyLevel * efficiencyLevel) + 1;
            }
        } else {
            // If wrong tool, reset multiplier to 1
            speedMultiplier = 1.0f;
        }

        // Calculate damage per tick
        float damage = speedMultiplier / hardness;
        
        // Apply harvest modifier
        if (isCorrectTool) {
            damage /= 30;
        } else {
            damage /= 100;
        }

        // Check for instant breaking
        if (damage > 1) {
            return 0.05f; // One tick
        }

        // Convert to ticks and then to seconds
        float ticks = (float) Math.ceil(1.0f / damage);
        float seconds = ticks / 20.0f;
        
        // Cap maximum mining time at 20 seconds
        return Math.min(seconds, 20.0f);
    }

    private float getBaseBreakingSpeed(Material tool, Material block) {
        String toolName = tool.name();
        if (toolName.contains("GOLD")) return 12.0f;
        if (toolName.contains("NETHERITE")) return 9.0f;
        if (toolName.contains("DIAMOND")) return 8.0f;
        if (toolName.contains("IRON")) return 6.0f;
        if (toolName.contains("STONE")) return 4.0f;
        if (toolName.contains("WOODEN")) return 2.0f;
        return 1.0f;
    }

    private boolean canMineBlock(ItemStack tool, Block block) {
        return block.getType().isSolid() && 
               !block.getType().isAir() && 
               block.getType().getHardness() >= 0; // Check for unbreakable blocks (-1 hardness)
    }

    private boolean isCorrectToolForBlock(Material tool, Material block) {
        String toolName = tool.name();
        String blockName = block.name();
        
        // Pickaxe materials
        if (toolName.endsWith("PICKAXE")) {
            if (block == Material.OBSIDIAN || block == Material.CRYING_OBSIDIAN || block == Material.ANCIENT_DEBRIS) {
                return tool == Material.DIAMOND_PICKAXE || tool == Material.NETHERITE_PICKAXE;
            }
            
            if (blockName.contains("DIAMOND") || blockName.contains("EMERALD") || blockName.contains("GOLD")) {
                return tool == Material.IRON_PICKAXE || tool == Material.DIAMOND_PICKAXE || tool == Material.NETHERITE_PICKAXE;
            }
            
            return blockName.contains("STONE") || blockName.contains("ORE") || 
                   blockName.contains("BRICK") || blockName.contains("CONCRETE") ||
                   blockName.contains("TERRACOTTA") || blockName.contains("DEEPSLATE") ||
                   blockName.contains("GRANITE") || blockName.contains("ANDESITE") ||
                   blockName.contains("DIORITE");
        }
        
        // Axe materials
        if (toolName.endsWith("_AXE")) {
            return blockName.contains("LOG") || blockName.contains("WOOD") || 
                   blockName.contains("PLANK") || blockName.contains("FENCE") ||
                   blockName.contains("DOOR") || blockName.contains("TRAPDOOR") ||
                   blockName.contains("STAIRS") && block.name().contains("WOOD");
        }
        
        // Shovel materials
        if (toolName.endsWith("_SHOVEL")) {
            return block == Material.DIRT || block == Material.GRASS_BLOCK ||
                   block == Material.SAND || block == Material.GRAVEL ||
                   block == Material.CLAY || block == Material.SOUL_SAND ||
                   block == Material.SOUL_SOIL || block == Material.MYCELIUM ||
                   block == Material.SNOW || block == Material.SNOW_BLOCK;
        }
        
        // Hoe materials
        if (toolName.endsWith("_HOE")) {
            return // Add crop support
                   block == Material.WHEAT || block == Material.CARROTS ||
                   block == Material.POTATOES || block == Material.BEETROOTS ||
                   block == Material.NETHER_WART || block == Material.COCOA ||
                   blockName.contains("CROP") || blockName.contains("SEEDS") ||
                   // Add farmland/dirt support
                   block == Material.FARMLAND || block == Material.DIRT ||
                   block == Material.GRASS_BLOCK || block == Material.DIRT_PATH;
        }
        
        return false;
    }

    private void removePickaxe(Dispenser dispenser, ItemStack pickaxe) {
        ItemStack[] contents = dispenser.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].equals(pickaxe)) {
                contents[i] = null;
                break;
            }
        }
        dispenser.getInventory().setContents(contents);
    }

    private boolean isTool(Material material) {
        return material.name().endsWith("_PICKAXE") ||
               material.name().endsWith("_AXE") ||
               material.name().endsWith("_SHOVEL") ||
               material.name().endsWith("_HOE");
    }

    public int getActiveMiningCount() {
        return activeMiningOperations.size();
    }

    public int getActiveToolCount() {
        return activeDispenserTools.size();
    }

    public Set<Location> getActiveMiningLocations() {
        return new HashSet<>(activeMiningOperations);
    }

    public Set<String> getActiveToolOperations() {
        return new HashSet<>(activeDispenserTools);
    }

    private float calculateToolEfficiency(ItemStack tool, Block block) {
        if (!isCorrectToolForBlock(tool.getType(), block.getType())) {
            return 0.1f; // Very low priority for wrong tools
        }
        
        float baseSpeed = getBaseBreakingSpeed(tool.getType(), block.getType());
        int efficiencyLevel = tool.getEnchantmentLevel(Enchantment.DIG_SPEED);
        
        // Add efficiency bonus
        if (efficiencyLevel > 0) {
            baseSpeed += (efficiencyLevel * efficiencyLevel) + 1;
        }
        
        // Prioritize better tool materials
        if (tool.getType().name().contains("NETHERITE")) baseSpeed *= 1.2f;
        else if (tool.getType().name().contains("DIAMOND")) baseSpeed *= 1.1f;
        
        // Consider durability - slightly prefer tools with more durability left
        float durabilityFactor = 1.0f - (float)tool.getDurability() / tool.getType().getMaxDurability();
        baseSpeed *= (0.9f + (0.1f * durabilityFactor));
        
        return baseSpeed;
    }

    // Add chunk unload event handler
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        // Clean up any mining operations in the unloading chunk
        activeMiningOperations.removeIf(loc -> {
            if (loc.getChunk().equals(event.getChunk())) {
                String toolKey = null;
                for (String key : activeDispenserTools) {
                    if (key.contains(loc.toString())) {
                        toolKey = key;
                        break;
                    }
                }
                if (toolKey != null) {
                    activeDispenserTools.remove(toolKey);
                }
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (activeMiningOperations.contains(loc)) {
            // Clear animation before removing tracking
            showMiningAnimation(event.getBlock(), -1);
            activeMiningOperations.remove(loc);
            activeDispenserTools.removeIf(key -> key.contains(loc.toString()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryChange(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Dispenser) {
            Dispenser dispenser = (Dispenser) event.getSource().getHolder();
            String dispenserLoc = dispenser.getLocation().toString();
            // Check if this dispenser has any active operations
            activeDispenserTools.removeIf(key -> {
                if (key.startsWith(dispenserLoc)) {
                    // Find and clear any associated mining operations
                    activeMiningOperations.forEach(loc -> showMiningAnimation(loc.getBlock(), -1));
                    return true;
                }
                return false;
            });
        }
    }

    private boolean isValidMiningOperation(Block block, Material originalType, 
                                         Dispenser dispenser, ItemStack originalTool) {
        // Check if chunk is loaded
        if (!block.getChunk().isLoaded()) return false;
        
        // Check if block has moved or changed
        if (block.getType() != originalType || !block.getLocation().equals(block.getLocation())) return false;
        
        // Check if dispenser still exists and has the tool
        if (!(dispenser.getBlock().getState() instanceof Dispenser)) return false;
        
        // Check if tool still exists in dispenser with same properties
        boolean toolExists = false;
        for (ItemStack item : dispenser.getInventory().getContents()) {
            if (item != null && item.isSimilar(originalTool)) {
                toolExists = true;
                break;
            }
        }
        
        return toolExists;
    }
} 