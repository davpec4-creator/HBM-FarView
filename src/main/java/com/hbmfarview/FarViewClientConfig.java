package com.hbmfarview;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings that only change what this player sees. Saved in config/hbm_farview-client.toml.
 * Changes from the config screen apply at once, without a restart.
 */
public final class FarViewClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue BLAST_EFFECTS;
    private static final ModConfigSpec.BooleanValue MISSILES;
    private static final ModConfigSpec.BooleanValue MISSILE_SMOKE;
    private static final ModConfigSpec.BooleanValue CUSTOM_CLOUDS;

    private static final ModConfigSpec.BooleanValue RAIN_CLOSES_CLOUDS;
    private static final ModConfigSpec.IntValue RAIN_CLOSE_SECONDS;
    private static final ModConfigSpec.IntValue RAIN_OPEN_SECONDS;
    private static final ModConfigSpec.BooleanValue RAIN_GROUPS;
    private static final ModConfigSpec.IntValue RAIN_GROUP_CHANCE;
    private static final ModConfigSpec.BooleanValue LIGHTNING;
    private static final ModConfigSpec.BooleanValue FALLOUT;
    private static final ModConfigSpec.IntValue FALLOUT_CHANCE;
    private static final ModConfigSpec.IntValue FALLOUT_MINUTES;
    private static final ModConfigSpec.IntValue FALLOUT_DISTANCE;
    private static final ModConfigSpec.IntValue FALLOUT_FOG_DISTANCE;
    private static final ModConfigSpec.IntValue FALLOUT_MIN_MINUTES;
    private static final ModConfigSpec.BooleanValue BLAST_PUSHES_CLOUDS;
    private static final ModConfigSpec.IntValue BLAST_CLOUD_RADIUS;
    private static final ModConfigSpec.IntValue FALLOUT_MAX_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BLAST_EFFECTS = builder
                .comment("Show the mushroom cloud and the large blast effects from far away, beyond the render distance.")
                .define("blastEffects", true);
        MISSILES = builder
                .comment("Show NTM missiles in flight from far away, beyond the render distance.")
                .define("missiles", true);
        MISSILE_SMOKE = builder
                .comment("Draw the missile smoke from far away. Beyond 350 blocks it is thinner and the puffs are larger.")
                .define("missileSmoke", true);
        CUSTOM_CLOUDS = builder
                .comment("Replace the vanilla clouds with the Far View clouds. Turn off to get the original clouds back.")
                .define("customClouds", true);

        builder.comment("Weather of the Far View clouds. Only visual and only for this player; the world does not change.")
                .push("weather");
        RAIN_CLOSES_CLOUDS = builder
                .comment("Rain and thunderstorms slowly close the clouds into one layer. They break apart again after the rain.")
                .define("rainClosesClouds", true);
        RAIN_CLOSE_SECONDS = builder
                .comment("How many seconds the clouds take to close when the rain starts; they grow in small steps. 0 closes them at once.")
                .defineInRange("rainCloseSeconds", 6, 0, 600);
        RAIN_OPEN_SECONDS = builder
                .comment("How many seconds the clouds take to break apart after the rain. 0 opens them at once.")
                .defineInRange("rainOpenSeconds", 180, 0, 1800);
        RAIN_GROUPS = builder
                .comment("Large groups of darker clouds appear now and then, with rain below them.")
                .define("rainGroups", true);
        RAIN_GROUP_CHANCE = builder
                .comment("Chance in percent that a rain group appears in an area of about 1000 x 1000 blocks during one day.")
                .defineInRange("rainGroupChance", 15, 0, 100);
        LIGHTNING = builder
                .comment("Rain groups flash with lightning now and then.")
                .define("lightning", true);
        FALLOUT = builder
                .comment("After a nuclear explosion, groups of black clouds appear downwind of it. Dust falls under them.")
                .define("fallout", true);
        FALLOUT_CHANCE = builder
                .comment("Chance in percent that a nuclear explosion brings fallout.")
                .defineInRange("falloutChance", 100, 0, 100);
        FALLOUT_MINUTES = builder
                .comment("For how many minutes after the explosion new fallout groups appear.")
                .defineInRange("falloutMinutes", 10, 1, 60);
        FALLOUT_DISTANCE = builder
                .comment("Largest distance in blocks from the explosion at which a fallout group appears.")
                .defineInRange("falloutDistance", 2000, 300, 8000);
        FALLOUT_FOG_DISTANCE = builder
                .comment("How far you can see under a fallout cloud, in blocks.")
                .defineInRange("falloutFogDistance", 3, 2, 256);
        FALLOUT_MIN_MINUTES = builder
                .comment("Shortest time in minutes one fallout group stays.")
                .defineInRange("falloutMinMinutes", 15, 1, 120);
        FALLOUT_MAX_MINUTES = builder
                .comment("Longest time in minutes one fallout group stays.")
                .defineInRange("falloutMaxMinutes", 30, 1, 120);
        BLAST_PUSHES_CLOUDS = builder
                .comment("The pressure wave of a nuclear explosion pushes the clouds away and leaves a clear ring around the mushroom cloud.")
                .define("blastPushesClouds", true);
        BLAST_CLOUD_RADIUS = builder
                .comment("Radius in blocks of the clear ring the pressure wave leaves in the clouds.")
                .defineInRange("blastCloudRadius", 300, 50, 2000);
        builder.pop();
        SPEC = builder.build();
    }

    private FarViewClientConfig() {
    }

    public static boolean blastEffects() {
        return get(BLAST_EFFECTS);
    }

    public static boolean missiles() {
        return get(MISSILES);
    }

    public static boolean missileSmoke() {
        return get(MISSILE_SMOKE);
    }

    public static boolean customClouds() {
        return get(CUSTOM_CLOUDS);
    }

    public static boolean rainClosesClouds() {
        return get(RAIN_CLOSES_CLOUDS);
    }

    public static int rainCloseSeconds() {
        return get(RAIN_CLOSE_SECONDS);
    }

    public static int rainOpenSeconds() {
        return get(RAIN_OPEN_SECONDS);
    }

    public static boolean rainGroups() {
        return get(RAIN_GROUPS);
    }

    public static int rainGroupChance() {
        return get(RAIN_GROUP_CHANCE);
    }

    public static boolean lightning() {
        return get(LIGHTNING);
    }

    public static boolean fallout() {
        return get(FALLOUT);
    }

    public static int falloutChance() {
        return get(FALLOUT_CHANCE);
    }

    public static int falloutMinutes() {
        return get(FALLOUT_MINUTES);
    }

    public static int falloutDistance() {
        return get(FALLOUT_DISTANCE);
    }

    public static int falloutFogDistance() {
        return get(FALLOUT_FOG_DISTANCE);
    }

    public static int falloutMinMinutes() {
        return Math.min(get(FALLOUT_MIN_MINUTES), get(FALLOUT_MAX_MINUTES));
    }

    public static int falloutMaxMinutes() {
        return Math.max(get(FALLOUT_MIN_MINUTES), get(FALLOUT_MAX_MINUTES));
    }

    public static boolean blastPushesClouds() {
        return get(BLAST_PUSHES_CLOUDS);
    }

    public static int blastCloudRadius() {
        return get(BLAST_CLOUD_RADIUS);
    }

    private static boolean get(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
