package org.macver.pausechat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.shanerx.mojang.Mojang;

import java.util.UUID;

public class PlayerUtility {

    public static @Nullable String getUsernameOfUuid(@NotNull UUID uuid) {
        Mojang mojang = new Mojang();
        return mojang.getPlayerProfile(uuid.toString()).getUsername();
    }

}
