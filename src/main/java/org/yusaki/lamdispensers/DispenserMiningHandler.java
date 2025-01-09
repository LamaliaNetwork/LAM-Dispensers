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
        Material originalType = block.getType(); // Store the original block type
        Location originalLocation = block.getLocation().clone(); // Store the original location
        
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (a) -> {
            // Check if the block still exists at the same location and is the same type
            if (block.getLocation().equals(originalLocation) && block.getType() == originalType) {
                performInstantMining(dispenser, pickaxe, block);
            }
            // If block changed, moved, or was broken, do nothing
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
                    // Check for Unbreaking enchantment
                    int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
                    
                    // Calculate if damage should be applied
                    boolean shouldTakeDamage = true;
                    if (unbreakingLevel > 0) {
                        // Unbreaking has a chance to prevent durability loss
                        // Formula: 100/(unbreaking level + 1)% chance to reduce durability
                        double chance = 1.0 / (unbreakingLevel + 1);
                        shouldTakeDamage = Math.random() < chance;
                    }
                    
                    if (shouldTakeDamage) {
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
        float hardness = block.getType().getHardness();
        if (hardness == 0) return 0.05f; // Instant break for zero hardness blocks
        
        float baseSpeed = getBaseBreakingSpeed(pickaxe.getType(), block.getType());
        int efficiencyLevel = pickaxe.getEnchantmentLevel(Enchantment.EFFICIENCY);
        
        // Calculate speed similar to vanilla mechanics
        float speed = baseSpeed;
        if (efficiencyLevel > 0) {
            speed += Math.pow(efficiencyLevel, 2) + 1;
        }

        // Calculate breaking time according to vanilla mechanics
        float hardnessMultiplier = isCorrectToolForBlock(pickaxe.getType(), block.getType()) ? 1.5f : 5.0f;
        float breakingTime = (hardness * hardnessMultiplier) / speed;
        
        
        // Cap minimum and maximum times
        return Math.max(0.05f, Math.min(breakingTime, 10f));
    }

    private float getBaseBreakingSpeed(Material tool, Material block) {
        switch (tool) {
            case NETHERITE_PICKAXE: return 10.0f;
            case DIAMOND_PICKAXE: return 8.0f;
            case IRON_PICKAXE: return 6.0f;
            case STONE_PICKAXE: return 4.0f;
            case WOODEN_PICKAXE: return 2.0f;
            default: return 1.0f;
        }
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
        // More specific tool-block matching
        if (!isPickaxe(tool)) return false;

        // Check block hardness and material type
        switch (block) {
            case OBSIDIAN:
            case CRYING_OBSIDIAN:
            case ANCIENT_DEBRIS:
                // Only diamond and netherite pickaxes
                return tool == Material.DIAMOND_PICKAXE || tool == Material.NETHERITE_PICKAXE;
            
            case DIAMOND_BLOCK:
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
            case GOLD_BLOCK:
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                // Iron pickaxe or better
                return tool == Material.IRON_PICKAXE || 
                       tool == Material.DIAMOND_PICKAXE || 
                       tool == Material.NETHERITE_PICKAXE;
            
            default:
                // General pickaxe-mineable blocks
                return block.name().contains("STONE") || 
                       block.name().contains("ORE") || 
                       block.name().contains("BRICK") ||
                       block.name().contains("CONCRETE") ||
                       block.name().contains("TERRACOTTA") ||
                       block.name().contains("DEEPSLATE") ||
                       block.name().contains("GRANITE") ||
                       block.name().contains("ANDESITE") ||
                       block.name().contains("DIORITE");
        }
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