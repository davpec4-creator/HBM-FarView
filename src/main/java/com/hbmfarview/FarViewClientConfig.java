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

    private static boolean get(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }
}
