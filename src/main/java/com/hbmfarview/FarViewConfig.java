package com.hbmfarview;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FarViewConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue RANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        RANGE = builder
                .comment("How far from the player (in blocks) the mushroom cloud, large blast effects and missiles can still be seen. Useful values are 100 to 4096. 0 turns the mod off.")
                .defineInRange("farTrackingRange", 4096, 0, 4096);
        SPEC = builder.build();
    }

    private FarViewConfig() {
    }

    public static int range() {
        return RANGE.get();
    }
}
