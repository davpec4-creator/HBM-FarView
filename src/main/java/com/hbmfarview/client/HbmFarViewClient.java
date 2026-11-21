package com.hbmfarview.client;

import com.hbmfarview.FarViewClientConfig;
import com.hbmfarview.HbmFarView;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client start of the mod: registers the client config and the "Config" button in the mod list.
 */
@Mod(value = HbmFarView.MOD_ID, dist = Dist.CLIENT)
public class HbmFarViewClient {
    public HbmFarViewClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, FarViewClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
