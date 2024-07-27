package io.github.stonley890.pausechat;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.executors.NativeResultingCommandExecutor;
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
    public void onEnable() {

        plugin = this;

        // Start message & register events
        getLogger()
                .info("Pause Chat: A Spigot plugin that temporarily (or permanently) disables Minecraft chat.");
        getServer().getPluginManager().registerEvents(this, this);

        // Create config if needed
        getDataFolder().mkdir();
        saveDefaultConfig();

        // If chat was previously paused, restore and notify in console
        if (getConfig().getBoolean("chatPaused")) {
            chatPaused = true;
            Bukkit.getServer().getLogger()
                    .info("Chat is currently paused from last session! Use /pausechat to allow users to chat.");
        }

        // Load pauseBypass.yml
        try {
            PauseBypass.init();
        } catch (IOException e) {
            Bukkit.getLogger().severe("Pause Chat was unable to initialize the pausebypass.yml file! Does the server have read/write access?");
        }

        // Initialize command
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).silentLogs(true));
        CommandAPI.onEnable();
        new CommandAPICommand("pausechat")
                .withHelp("Pause the chat.", "Suppresses messages from players.")
                .withPermission(CommandPermission.OP)
                .executesNative((sender, args) -> {
                    if (chatPaused) {
                        // Change settings
                        chatPaused = false;
                        plugin.getConfig().set("chatPaused", chatPaused);

                        // Broadcast to server
                        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "Chat has been unpaused.");
                    } else {
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
                .withPermission(CommandPermission.OP)
                .executes((ResultingCommandExecutor) (sender, args) -> {
                    throw CommandAPI.failWithString("You must specify a subcommand: add, remove, or list!");
                })
                .withSubcommand(new CommandAPICommand("add")
                        .withArguments(new EntitySelectorArgument.ManyPlayers("players"))
                        .executes((sender, args) -> {
                            Collection<Player> players = (Collection<Player>) args.get("players");
                            List<UUID> playersList = PauseBypass.getPlayers();
                            assert players != null;
                            playersList.addAll(players.stream().map(Player::getUniqueId).toList());
                            PauseBypass.setPlayers(playersList);
                            sender.sendMessage("Added " + players.size() + " player(s) to the bypass list.");
                        })
                )
                .withSubcommand(new CommandAPICommand("remove")
                        .withArguments(new EntitySelectorArgument.ManyPlayers("players"))
                        .executes((sender, args) -> {
                            Collection<Player> players = (Collection<Player>) args.get("players");
                            List<UUID> playersList = PauseBypass.getPlayers();
                            assert players != null;
                            boolean removed = playersList.removeAll(players.stream().map(Player::getUniqueId).toList());
                            PauseBypass.setPlayers(playersList);
                            if (removed) sender.sendMessage("Removed " + players.size() + " player(s) from the bypass list.");
                            else throw CommandAPI.failWithString("No players were removed.");
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

            if (chatPaused) {
                List<UUID> bypassedPlayers = PauseBypass.getPlayers();

                if (!bypassedPlayers.contains(event.getPlayer().getUniqueId())
                        && !event.getPlayer().isOp()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChatEvent(AsyncPlayerChatEvent event) {
        // IF chat is not paused AND the player is not an operator OR the player is an
        // operator, send message
        if (chatPaused) {
            List<UUID> bypassedPlayers = PauseBypass.getPlayers();

            // If player is on soft whitelist or is op, allow. If not, kick player.
            if (!bypassedPlayers.contains(event.getPlayer().getUniqueId())
                    && !event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Chat is currently paused.");
            }
        }
    }
}