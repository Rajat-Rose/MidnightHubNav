package com.midnightsmp.midnighthubnav;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MidnightHubNav extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    private int warmupSeconds;
    private boolean cancelOnMove;
    private boolean cancelOnDamage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        warmupSeconds = getConfig().getInt("warmup-seconds", 3);
        cancelOnMove = getConfig().getBoolean("cancel-on-move", true);
        cancelOnDamage = getConfig().getBoolean("cancel-on-damage", true);

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("spawn").setExecutor(this);
        getCommand("hub").setExecutor(this);
        getCommand("setspawn").setExecutor(this);
        getCommand("sethub").setExecutor(this);

        getLogger().info("MidnightHubNav enabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        String commandName = cmd.getName().toLowerCase();

        switch (commandName) {
            case "setspawn" -> {
                saveLocation("spawn", player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✅ Spawn location updated!");
                return true;
            }
            case "sethub" -> {
                saveLocation("hub", player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✅ Hub location updated!");
                return true;
            }
            case "spawn" -> {
                Location loc = getLocation("spawn");
                if (loc == null) loc = player.getWorld().getSpawnLocation();
                startTeleport(player, loc, "Spawn");
                return true;
            }
            case "hub" -> {
                Location loc = getLocation("hub");
                if (loc == null) {
                    player.sendMessage(ChatColor.RED + "Hub location is not set by admins!");
                    return true;
                }
                startTeleport(player, loc, "Hub");
                return true;
            }
        }

        return true;
    }

    private void startTeleport(Player player, Location targetLoc, String targetName) {
        UUID uuid = player.getUniqueId();

        if (pendingTeleports.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "You already have a pending teleport!");
            return;
        }

        if (player.hasPermission("midnighthubnav.bypass")) {
            player.teleport(targetLoc);
            player.sendMessage(ChatColor.GREEN + "⚡ Teleported to " + targetName + "!");
            return;
        }

        startLocations.put(uuid, player.getLocation());
        player.sendMessage(ChatColor.GOLD + "⏳ Teleporting to " + targetName + " in " + warmupSeconds + " seconds... Don't move!");

        BukkitTask task = new BukkitRunnable() {
            int timer = warmupSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTeleport(uuid);
                    return;
                }

                if (timer <= 0) {
                    player.teleport(targetLoc);
                    player.sendMessage(ChatColor.GREEN + "⚡ Teleported to " + targetName + "!");
                    cancelTeleport(uuid);
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + "Teleporting in " + timer + "...");
                timer--;
            }
        }.runTaskTimer(this, 0L, 20L);

        pendingTeleports.put(uuid, task);
    }

    private void cancelTeleport(UUID uuid) {
        if (pendingTeleports.containsKey(uuid)) {
            pendingTeleports.get(uuid).cancel();
            pendingTeleports.remove(uuid);
            startLocations.remove(uuid);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!cancelOnMove) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingTeleports.containsKey(uuid)) return;

        Location start = startLocations.get(uuid);
        Location current = player.getLocation();

        if (start != null && (start.getBlockX() != current.getBlockX() || start.getBlockY() != current.getBlockY() || start.getBlockZ() != current.getBlockZ())) {
            cancelTeleport(uuid);
            player.sendMessage(ChatColor.RED + "❌ Teleportation cancelled because you moved!");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!cancelOnDamage) return;

        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (pendingTeleports.containsKey(uuid)) {
                cancelTeleport(uuid);
                player.sendMessage(ChatColor.RED + "❌ Teleportation cancelled because you took damage!");
            }
        }
    }

    private void saveLocation(String key, Location loc) {
        getConfig().set(key + ".world", loc.getWorld().getName());
        getConfig().set(key + ".x", loc.getX());
        getConfig().set(key + ".y", loc.getY());
        getConfig().set(key + ".z", loc.getZ());
        getConfig().set(key + ".yaw", loc.getYaw());
        getConfig().set(key + ".pitch", loc.getPitch());
        saveConfig();
    }

    private Location getLocation(String key) {
        if (!getConfig().contains(key + ".world")) return null;
        String worldName = getConfig().getString(key + ".world");
        if (worldName == null || Bukkit.getWorld(worldName) == null) return null;

        return new Location(
                Bukkit.getWorld(worldName),
                getConfig().getDouble(key + ".x"),
                getConfig().getDouble(key + ".y"),
                getConfig().getDouble(key + ".z"),
                (float) getConfig().getDouble(key + ".yaw"),
                (float) getConfig().getDouble(key + ".pitch")
        );
    }
}
