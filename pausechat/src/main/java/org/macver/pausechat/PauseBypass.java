package org.macver.pausechat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PauseBypass {

    private static final File file = new File(PauseChat.getPlugin().getDataFolder().getAbsolutePath() + "/pauseBypass.yml");

    public static void init() throws IOException {
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (created) PauseChat.getPlugin().getLogger().info("Created " + file.getName() + ".");
        }
    }

    @NotNull
    public static YamlConfiguration getConfig() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe(file.getName() + " cannot be read! Does the server have read/write access? " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(PauseChat.getPlugin());
        } catch (InvalidConfigurationException e) {
            Bukkit.getLogger().severe(file.getName() + " is not a valid configuration! Is it formatted correctly? " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(PauseChat.getPlugin());
        }
        return config;
    }

    public static void saveConfig(@NotNull YamlConfiguration config) {
        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe( file.getName() + " cannot be written! Does the server have read/write access? " + e.getMessage() + "\nHere is the data that was not saved:\n" + config.saveToString());
            Bukkit.getPluginManager().disablePlugin(PauseChat.getPlugin());
        }
    }

    @NotNull
    public static List<UUID> getPlayers() {
        YamlConfiguration config = getConfig();
        List<UUID> list = new ArrayList<>();
        List<String> stringList = config.getStringList("players");
        for (String s : stringList) {
            list.add(UUID.fromString(s));
        }
        return list;
    }

    public static void setPlayers(@NotNull List<UUID> list) {
        YamlConfiguration config = getConfig();
        config.set("players", list.stream().map(UUID::toString).toList());
        saveConfig(config);
    }

}
