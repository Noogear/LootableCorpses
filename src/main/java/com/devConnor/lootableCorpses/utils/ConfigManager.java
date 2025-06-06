package com.devConnor.lootableCorpses.utils;

import com.devConnor.lootableCorpses.LootableCorpses;
import lombok.experimental.UtilityClass;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ConfigManager {

    private static FileConfiguration config;

    public static void setupConfig(LootableCorpses lootableCorpses) {
        ConfigManager.config = lootableCorpses.getConfig();

        lootableCorpses.saveDefaultConfig();
    }

    public static int getCorpseLifespanMillis() {
        return (config.getInt("remove-corpse-after-minutes") * 60) * 1000;
    }

    public static int getCorpseLifespanAfterInteractionMillis() {
        return (config.getInt("remove-corpse-after-interaction-minutes") * 60 * 20);
    }

    public static boolean isCorpseLifespanAfterInteractionSet() {
        try {
            config.getInt("remove-corpse-after-interaction-minutes");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLootingDisabled() {
        return getBoolean("disable-looting");
    }

    public static boolean isInstantRespawnEnabled() {
        return getBoolean("instant-respawn");
    }

    public static boolean shouldKillOnLeave() {
        return getBoolean("kill-on-leave");
    }

    private static boolean getBoolean(String path) {
        try {
            return config.getBoolean(path);
        } catch (Exception e) {
            return false;
        }
    }

    public static String getInventoryTitle() {
        return config.getString("inventory-title", "{player}'s inventory");
    }

    public static boolean isKeepCorpsesAboveTheVoid() {
        return getBoolean("keep-corpses-above-the-void");
    }

    public static boolean shouldDropInvOnDespawn() {
        return getBoolean("drop-inv-on-despawn");
    }

    public static List<String> getBlacklistedWorlds() {
        List<?> blacklistedWorlds = config.getList("blacklisted-worlds");
        if (blacklistedWorlds != null) {
            return blacklistedWorlds.stream()
                    .map(String.class::cast)
                    .toList();
        }

        return new ArrayList<>();
    }

    public static boolean spawnCorpseOnPlayerLeave() {
        return getBoolean("spawn-corpse-on-player-leave");
    }
}
