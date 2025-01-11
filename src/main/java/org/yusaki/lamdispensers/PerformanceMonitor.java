package org.yusaki.lamdispensers;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Location;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PerformanceMonitor implements CommandExecutor, TabCompleter {
    private final LamDispensers plugin;
    private final DispenserMiningHandler miningHandler;

    public PerformanceMonitor(LamDispensers plugin, DispenserMiningHandler miningHandler) {
        this.plugin = plugin;
        this.miningHandler = miningHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lamdispensers.performance")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "memory":
                showMemoryUsage(sender);
                break;
            case "tasks":
                showActiveTasks(sender);
                break;
            case "gc":
                runGC(sender);
                break;
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== LamDispensers Performance Monitor ===");
        sender.sendMessage(ChatColor.YELLOW + "/ldperf memory " + ChatColor.WHITE + "- Show memory usage");
        sender.sendMessage(ChatColor.YELLOW + "/ldperf tasks " + ChatColor.WHITE + "- Show active mining tasks");
        sender.sendMessage(ChatColor.YELLOW + "/ldperf gc " + ChatColor.WHITE + "- Run garbage collection");
    }

    private void showMemoryUsage(CommandSender sender) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        sender.sendMessage(ChatColor.GOLD + "=== Memory Usage ===");
        sender.sendMessage(ChatColor.YELLOW + "Heap Memory:");
        sender.sendMessage(ChatColor.WHITE + "  Used: " + formatBytes(heapUsage.getUsed()));
        sender.sendMessage(ChatColor.WHITE + "  Committed: " + formatBytes(heapUsage.getCommitted()));
        sender.sendMessage(ChatColor.WHITE + "  Max: " + formatBytes(heapUsage.getMax()));
        
        sender.sendMessage(ChatColor.YELLOW + "Non-Heap Memory:");
        sender.sendMessage(ChatColor.WHITE + "  Used: " + formatBytes(nonHeapUsage.getUsed()));
        sender.sendMessage(ChatColor.WHITE + "  Committed: " + formatBytes(nonHeapUsage.getCommitted()));
        
        Runtime runtime = Runtime.getRuntime();
        sender.sendMessage(ChatColor.YELLOW + "Total Memory: " + ChatColor.WHITE + formatBytes(runtime.totalMemory()));
        sender.sendMessage(ChatColor.YELLOW + "Free Memory: " + ChatColor.WHITE + formatBytes(runtime.freeMemory()));
    }

    private void showActiveTasks(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Active Tasks ===");
        
        // Show total counts
        sender.sendMessage(ChatColor.YELLOW + "Active Mining Operations: " + ChatColor.WHITE + miningHandler.getActiveMiningCount());
        sender.sendMessage(ChatColor.YELLOW + "Active Tool Operations: " + ChatColor.WHITE + miningHandler.getActiveToolCount());
        
        // Show detailed mining operations
        sender.sendMessage(ChatColor.YELLOW + "\nActive Mining Locations:");
        for (Location loc : miningHandler.getActiveMiningLocations()) {
            sender.sendMessage(ChatColor.WHITE + "  - " + formatLocation(loc));
        }
        
        // Show detailed tool operations
        sender.sendMessage(ChatColor.YELLOW + "\nActive Tool Operations:");
        for (String toolOp : miningHandler.getActiveToolOperations()) {
            sender.sendMessage(ChatColor.WHITE + "  - " + formatToolOperation(toolOp));
        }
    }

    private String formatLocation(Location loc) {
        return String.format("World: %s, X: %d, Y: %d, Z: %d", 
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private String formatToolOperation(String toolOp) {
        String[] parts = toolOp.split(":");
        String location = parts[0].substring(parts[0].indexOf("{")); // Clean up the Location toString
        String tool = parts[1].replace("_", " ").toLowerCase();
        return "Location: " + location + ", Tool: " + tool;
    }

    private void runGC(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Running garbage collection...");
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        System.gc();
        
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long freed = memBefore - memAfter;
        
        sender.sendMessage(ChatColor.GREEN + "Garbage collection complete!");
        sender.sendMessage(ChatColor.YELLOW + "Memory freed: " + ChatColor.WHITE + formatBytes(freed));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("memory", "tasks", "gc", "help"));
            completions.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
            return completions;
        }
        return new ArrayList<>();
    }
} 