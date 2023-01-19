package io.github.stonley890;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.security.auth.login.LoginException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;
import org.shanerx.mojang.Mojang;

import io.github.stonley890.commands.CommandsManager;
import io.github.stonley890.data.PlayerMemory;
import io.github.stonley890.data.PlayerUtility;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

public class App extends JavaPlugin implements Listener {

    private static App plugin;
    private static boolean chatPaused;

    @Override
    public void onEnable() {

        plugin = this;

        // Start message & register events
        getLogger()
                .info("Pause Chat: A Spigot plugin that temporarily (or permanently) disables Minecraft chat.");
        getServer().getPluginManager().registerEvents(this, this);

        // Create config if needed
        getDataFolder().mkdir();
        saveDefaultConfig()

        // If chat was previously paused, restore and notify in console
        if (getConfig().getBoolean("chatPaused")) {
            chatPaused = true;
            Bukkit.getServer().getLogger()
                    .info("Chat is currently paused from last session! Use /pausechat to allow users to chat.");
        }
    }

    public static App getPlugin() {
        return plugin;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage();
        Player ply = event.getPlayer();
        if (event.getMessage().startsWith("/me")) {

            if (chatPaused) {
                File file = new File(getDataFolder().getAbsolutePath() + "/pauseBypass.yml");
                FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
                List<String> bypassedPlayers = new ArrayList<>(100);

                try {
                    fileConfig.load(file);
                } catch (IOException | InvalidConfigurationException e1) {
                    e1.printStackTrace();
                }

                bypassedPlayers = (List<String>) fileConfig.get("players");

                if (bypassedPlayers.contains(event.getPlayer().getUniqueId().toString())
                        || event.getPlayer().isOp()) {
                    TextChannel chatChannel = Bot.getJDA().getTextChannelById(CommandsManager.getChatChannel());
                    String action = cmd.replaceFirst("/me ", "");
                    chatChannel.sendMessage("**[" + ChatColor.stripColor(ply.getDisplayName()) + " **(" + ply.getName()
                            + ")**]** " + ChatColor.stripColor(action)).queue();
                } else {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // pausechat command
        if (label.equalsIgnoreCase("pausechat")) {
            TextChannel chatChannel = Bot.getJDA().getTextChannelById(CommandsManager.getChatChannel());
            // If chat is paused, unpause. If not, pause
            if (chatPaused == true) {
                chatPaused = false;
                getConfig().set("chatPaused", false);
                Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "Chat has been unpaused.");
                if (chatChannel != null) {
                    chatChannel.sendMessage("**Chat has been unpaused. Messages will now be sent to Minecraft**")
                            .queue();
                }
            } else {
                chatPaused = true;
                getConfig().set("chatPaused", true);
                Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "Chat has been paused.");
                if (chatChannel != null) {
                    chatChannel.sendMessage("**Chat has been paused. Messages will not be sent to Minecraft**").queue();
                }
            }
            saveConfig();
        } else if (label.equalsIgnoreCase("pausebypass")) {

            // Load pauseBypass.yml
            File file = new File(getDataFolder().getAbsolutePath() + "/pauseBypass.yml");
            FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);

            // Init saved players
            List<String> bypassedPlayers = new ArrayList<>(100);

            // If file does not exist, create one
            if (file.exists() == false) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "There was a problem accessing the file. Check logs for error.");
                    e.printStackTrace();
                }
            }

            // If file is empty, add a player to initialize
            if (fileConfig.get("players") == null) {
                Mojang mojang = new Mojang();
                mojang.connect();

                bypassedPlayers.add(mojang.getUUIDOfUsername("BogTheMudWing").replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"));
                fileConfig.set("players", bypassedPlayers);
                try {
                    fileConfig.save(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Load the file
            try {
                fileConfig.load(file);
            } catch (IOException | InvalidConfigurationException e1) {
                sender.sendMessage(ChatColor.RED + "There was a problem accessing the file. Check logs for error.");
                e1.printStackTrace();
            }
            // Get bypassed players
            bypassedPlayers = (List<String>) fileConfig.get("players");

            try {
                if (args[0].equalsIgnoreCase("add")) {
                    // Get player from UUID
                    Mojang mojang = new Mojang();
                    mojang.connect();

                    OfflinePlayer player = Bukkit
                            .getOfflinePlayer(UUID.fromString(mojang.getUUIDOfUsername(args[1]).replaceFirst(
                                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                                    "$1-$2-$3-$4-$5")));
                    // Add
                    if (bypassedPlayers.contains(player.getUniqueId().toString())) {
                        sender.sendMessage(ChatColor.RED + "That player is already allowed.");
                    } else {
                        bypassedPlayers.add(player.getUniqueId().toString());
                        sender.sendMessage(ChatColor.GOLD
                                + mojang.getPlayerProfile(player.getUniqueId().toString()).getUsername()
                                + " is now bypassing.");
                    }
                } else if (args[0].equalsIgnoreCase("remove")) {
                    // Get player from UUID
                    Mojang mojang = new Mojang();
                    mojang.connect();

                    OfflinePlayer player = Bukkit
                            .getOfflinePlayer(UUID.fromString(mojang.getUUIDOfUsername(args[1]).replaceFirst(
                                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                                    "$1-$2-$3-$4-$5")));
                    // Remove
                    if (bypassedPlayers.contains(player.getUniqueId().toString())) {
                        bypassedPlayers.remove(player.getUniqueId().toString());
                        sender.sendMessage(ChatColor.GOLD
                                + mojang.getPlayerProfile(player.getUniqueId().toString()).getUsername()
                                + " is no longer bypassing.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "That player is not allowed.");
                    }
                } else if (args[0].equalsIgnoreCase("list")) {
                    Mojang mojang = new Mojang();
                    mojang.connect();

                    // Build list
                    StringBuilder list = new StringBuilder();
                    ;
                    for (String players : bypassedPlayers) {
                        if (list.length() > 0) {
                            list.append(", ");
                        }
                        list.append(mojang.getPlayerProfile(players).getUsername());
                    }
                    sender.sendMessage(ChatColor.GOLD + "Players bypassing: " + list.toString());

                } else {
                    sender.sendMessage(
                            ChatColor.RED + "Incorrect arguements! /pausebypass <add|remove|list> <player>");

                }

                // Save changes
                fileConfig.set("players", bypassedPlayers);
                try {
                    fileConfig.save(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
                sender.sendMessage(
                        ChatColor.RED + "Missing arguments! /pausebypass <add|remove|list> <player>");
            }
        }
        return true;
    }

    @EventHandler
    public void onPlayerChatEvent(AsyncPlayerChatEvent event) {
        // IF chat is not paused AND the player is not an operator OR the player is an
        // operator, send message
        if (chatPaused == true) {
            File file = new File(getDataFolder().getAbsolutePath() + "/pauseBypass.yml");
            FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
            List<String> bypassedPlayers = new ArrayList<>(100);

            try {
                fileConfig.load(file);
            } catch (IOException | InvalidConfigurationException e1) {
                e1.printStackTrace();
            }

            bypassedPlayers = (List<String>) fileConfig.get("players");

            // If player is on soft whitelist or is op, allow. If not, kick player.
            if (bypassedPlayers.contains(event.getPlayer().getUniqueId().toString())
                    || event.getPlayer().isOp()) {
                
            } else {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
            }
        }
    }
}