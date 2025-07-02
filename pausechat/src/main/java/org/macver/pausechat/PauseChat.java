package org.macver.pausechat;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.ResultingCommandExecutor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PauseChat extends JavaPlugin implements Listener {

    private static PauseChat plugin;
    private static boolean chatPaused;

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {

        plugin = this;

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Create config if needed
        boolean mkdir = getDataFolder().mkdir();
        if (mkdir) getLogger().info("Created data folder.");
        saveDefaultConfig();

        // If chat was previously paused, restore and notify in console
        if (getConfig().getBoolean("chatPaused")) {
            chatPaused = true;
            getLogger().info("Chat is currently paused from last session! Use /pausechat to allow users to chat.");
        }

        // Load pauseBypass.yml
        try {
            PauseBypass.init();
        } catch (IOException e) {
            getLogger().severe("Pause Chat was unable to initialize the pausebypass.yml file! Does the server have read/write access?");
        }

        // Initialize command
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).silentLogs(true));
        CommandAPI.onEnable();
        new CommandAPICommand("pausechat")
                .withHelp("Pause the chat.", "Suppresses messages from players.")
                .withPermission(CommandPermission.fromString("pausechat.manage.state"))
                .executesNative((sender, args) -> {
                    // Check if the chat is paused
                    if (chatPaused) {
                        // If the chat is paused, unpause it.
                        // Change settings
                        chatPaused = false;
                        plugin.getConfig().set("chatPaused", chatPaused);

                        // Broadcast to server
                        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "Chat has been unpaused.");
                    } else {
                        // If the chat is not paused, pause it.
                        // Change settings
                        chatPaused = true;
                        plugin.getConfig().set("chatPaused", chatPaused);

                        // Broadcast to server
                        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "Chat has been paused.");
                    }
                    plugin.saveConfig();
                })
                .register();
        new CommandAPICommand("pausebypass")
                .withHelp("Allow players to bypass chat pause.", "Allow players to chat even when chat is paused.")
                .withPermission(CommandPermission.fromString("pausechat.manage.bypass"))
                .executes((ResultingCommandExecutor) (sender, args) -> {
                    throw CommandAPI.failWithString("You must specify a subcommand: add, remove, or list!");
                })
                .withSubcommand(new CommandAPICommand("add")
                        .withArguments(new EntitySelectorArgument.ManyPlayers("players", false))
                        .executes((sender, args) -> {

                            // Get all players in the selection
                            Collection<Player> players = (Collection<Player>) args.get("players");
                            assert players != null;

                            // Fail if no players
                            if (players.isEmpty()) throw CommandAPI.failWithString("No players were selected.");

                            // Add the new players to the list
                            final List<UUID> playersList = PauseBypass.getPlayers();
                            playersList.addAll(players.stream().map(Player::getUniqueId).toList());
                            // Save changes
                            PauseBypass.setPlayers(playersList);

                            // Send message
                            String baseMessage = "Added %s to the bypass list.";
                            String target = players.size() + " players";
                            if (players.size() == 1) {
                                // Use name if only one player
                                target = players.iterator().next().getName();
                            }
                            sender.sendMessage(String.format(baseMessage, target));

                        })
                )
                .withSubcommand(new CommandAPICommand("remove")
                        .withArguments(new EntitySelectorArgument.ManyPlayers("players", false))
                        .executes((sender, args) -> {

                            // Get all players in the selection
                            Collection<Player> players = (Collection<Player>) args.get("players");
                            assert players != null;

                            // Fail if no players
                            if (players.isEmpty()) throw CommandAPI.failWithString("No players were selected.");

                            // Remove players if any
                            List<UUID> playersList = PauseBypass.getPlayers();
                            boolean removed = playersList.removeAll(players.stream().map(Player::getUniqueId).toList());

                            // Fail if none removed
                            if (!removed) throw CommandAPI.failWithString("No players were removed.");

                            // Save changes
                            PauseBypass.setPlayers(playersList);

                            // Send message
                            String baseMessage = "Removed %s from the bypass list.";
                            String target = players.size() + " players";
                            if (players.size() == 1) {
                                // Use name if only one player
                                target = players.iterator().next().getName();
                            }
                            sender.sendMessage(String.format(baseMessage, target));

                        })
                )
                .withSubcommand(new CommandAPICommand("list")
                        .executes((sender, args) -> {
                            // Build list
                            StringBuilder list = new StringBuilder();

                            for (UUID player : PauseBypass.getPlayers()) {
                                if (!list.isEmpty()) list.append(", ");
                                list.append(PlayerUtility.getUsernameOfUuid(player));
                            }
                            sender.sendMessage("Players bypassing: " + list);
                        })
                )
                .register();
    }

    public static PauseChat getPlugin() {
        return plugin;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/me")) {
            if (isChatBlocked(event.getPlayer())) {
                event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChatEvent(@NotNull AsyncPlayerChatEvent event) {
        if (isChatBlocked(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
            event.setCancelled(true);
        }
    }

    private static boolean isChatBlocked(@NotNull Player player) {
        return chatPaused && !isPlayerPauseImmune(player);
    }

    public static boolean isPlayerPauseImmune(@NotNull Player player) {
        List<UUID> bypassedPlayers = PauseBypass.getPlayers();
        // True if player has the pausechat.bypass permission or if on the bypass list.
        return player.hasPermission("pausechat.bypass") || bypassedPlayers.contains(player.getUniqueId());

    }
}