package com.hbmfarview.client;

import com.hbmfarview.HbmFarView;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * The client command /farview config opens the same config screen as the button in the mod list.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class FarViewCommand {
    private FarViewCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("farview")
                .then(Commands.literal("config").executes(context -> {
                    openConfig();
                    return 1;
                })));
    }

    private static void openConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        // The chat closes after the command, so the screen is opened one step later.
        ModList.get().getModContainerById(HbmFarView.MOD_ID).ifPresent(container ->
                minecraft.tell(() -> minecraft.setScreen(new ConfigurationScreen(container, null))));
    }
}
