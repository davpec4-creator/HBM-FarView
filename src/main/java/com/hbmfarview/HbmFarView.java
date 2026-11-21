package com.hbmfarview;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(HbmFarView.MOD_ID)
public class HbmFarView {
    public static final String MOD_ID = "hbm_farview";

    public HbmFarView(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, FarViewConfig.SPEC);
    }
}
