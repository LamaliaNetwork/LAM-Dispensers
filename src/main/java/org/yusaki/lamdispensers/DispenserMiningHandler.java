package org.yusaki.lamdispensers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;

public class DispenserMiningHandler implements Listener {
    private final LamDispensers plugin;

    public DispenserMiningHandler(LamDispensers plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        Block dispenserBlock = event.getBlock();
        if (!(dispenserBlock.getState() instanceof Dispenser)) return;

        Dispenser dispenser = (Dispenser) dispenserBlock.getState();
        if (!(dispenserBlock.getBlockData() instanceof Directional)) return;

        ItemStack dispensedItem = event.getItem();
        if (!isPickaxe(dispensedItem.getType())) return;

        // Cancel the event early for pickaxes
        event.setCancelled(true);

        Directional directional = (Directional) dispenserBlock.getBlockData();
        BlockFace facing = directional.getFacing();
        Block targetBlock = dispenserBlock.getRelative(facing);

        if (canMineBlock(dispensedItem, targetBlock)) {
            startMining(dispenser, dispensedItem, targetBlock);
        }
        // If we can't mine the block, do nothing (tool stays in dispenser)
    }

    private void startMining(Dispenser dispenser, ItemStack pickaxe, Block targetBlock) {
        Location loc = targetBlock.getLocation();
        float miningTicks = calculateMiningTicks(pickaxe, targetBlock);
        int animationTicks = Math.max((int) (miningTicks * 20), 2);

        try {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                // Start block break animation
                showMiningAnimation(targetBlock, 0);

                // Schedule progressive animations
                for (int i = 1; i < 10; i++) {
                    final int stage = i;
                    long stageDelay = Math.max(1, (long) (animationTicks * (i / 9.0)));
                    scheduleMiningAnimation(loc, targetBlock, stage, stageDelay);
                }

                // Schedule block break
                scheduleMiningAnimation(loc, targetBlock, -1, animationTicks);
                scheduleBlockBreak(loc, dispenser, pickaxe, targetBlock, animationTicks + 1);
            });
        } catch (NoSuchMethodError e) {
            // Fallback for non-Folia servers
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                performInstantMining(dispenser, pickaxe, targetBlock);
            });
        }
    }

    private void scheduleMiningAnimation(Location loc, Block block, int stage, long delay) {
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (a) -> {
            showMiningAnimation(block, stage);
        }, delay);
    }

    private void scheduleBlockBreak(Location loc, Dispenser dispenser, ItemStack pickaxe, Block block, long delay) {
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (a) -> {
            performInstantMining(dispenser, pickaxe, block);
        }, delay);
    }

    private void performInstantMining(Dispenser dispenser, ItemStack pickaxe, Block block) {
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
            // Find the actual pickaxe in the dispenser's inventory
            ItemStack[] contents = dispenser.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.equals(pickaxe)) {
                    short newDurability = (short) (item.getDurability() + 1);
                    item.setDurability(newDurability);
                    
                    // Update the item in the dispenser
                    dispenser.getInventory().setItem(i, item);
                    
                    // Check if pickaxe should break
                    if (newDurability >= item.getType().getMaxDurability()) {
                        block.getWorld().playSound(
                            dispenser.getLocation(),
                            org.bukkit.Sound.ENTITY_ITEM_BREAK,
                            1.0f,
                            1.0f
                        );
                        dispenser.getInventory().setItem(i, null);
                    }
                    break;
                }
            }
        }
    }

    private void showMiningAnimation(Block block, int stage) {
        // Stage -1 removes the animation, 0-9 shows progressive breaking
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                Location loc = block.getLocation().add(x, 0, z);
                if (loc.distanceSquared(block.getLocation()) <= 256) { // 16 blocks radius
                    block.getWorld().getPlayers().forEach(player -> 
                        player.sendBlockDamage(block.getLocation(), stage < 0 ? 0 : stage / 9.0f));
                }
            }
        }

        // Play digging sound every other stage (to avoid too much noise)
        if (stage >= 0 && stage % 2 == 0) {
            block.getWorld().playSound(
                block.getLocation(),
                block.getBlockData().getSoundGroup().getHitSound(),
                1.0f,
                0.8f
            );
        }
    }

    private float calculateMiningTicks(ItemStack pickaxe, Block block) {
        float baseSpeed = getBaseBreakingSpeed(pickaxe.getType(), block.getType());
        int efficiencyLevel = pickaxe.getEnchantmentLevel(Enchantment.EFFICIENCY);
        
        if (efficiencyLevel > 0) {
            baseSpeed += (efficiencyLevel * efficiencyLevel + 1);
        }

        return Math.max(1.5f / baseSpeed, 0.05f);
    }

    private float getBaseBreakingSpeed(Material tool, Material block) {
        // These values are approximate. Adjust as needed.
        if (isCorrectToolForBlock(tool, block)) {
            switch (tool) {
                case NETHERITE_PICKAXE: return 9.0f;
                case DIAMOND_PICKAXE: return 8.0f;
                case IRON_PICKAXE: return 6.0f;
                case STONE_PICKAXE: return 4.0f;
                case WOODEN_PICKAXE: return 2.0f;
                default: return 1.0f;
            }
        }
        return 0.3f; // Wrong tool penalty
    }

    private boolean isPickaxe(Material material) {
        return material == Material.DIAMOND_PICKAXE || 
               material == Material.IRON_PICKAXE || 
               material == Material.STONE_PICKAXE || 
               material == Material.WOODEN_PICKAXE || 
               material == Material.NETHERITE_PICKAXE;
    }

    private boolean canMineBlock(ItemStack pickaxe, Block block) {
        return block.getType().isSolid() && !block.getType().isAir() && 
               isCorrectToolForBlock(pickaxe.getType(), block.getType());
    }

    private boolean isCorrectToolForBlock(Material tool, Material block) {
        // Add more block types as needed
        return block.name().contains("STONE") || 
               block.name().contains("ORE") || 
               block.name().contains("BRICK") ||
               block.name().contains("CONCRETE") ||
               block.name().contains("TERRACOTTA");
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
} 