package com.hbmfarview.client;

import com.hbmfarview.HbmFarView;
import com.hbmfarview.cloud.CloudWeather;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Client commands:
 * /farview config opens the same config screen as the button in the mod list.
 * /farview weather fallout|rain puts a fallout or rain group over the player, /farview weather clear removes
 * the groups. These only change the clouds this player sees.
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
                }))
                .then(Commands.literal("weather")
                        .then(Commands.literal("fallout").executes(context -> spawn(context, CloudWeather.FALLOUT, "fallout")))
                        .then(Commands.literal("rain").executes(context -> spawn(context, CloudWeather.RAIN, "rain")))
                        .then(Commands.literal("clear").executes(context -> {
                            CloudWeather.clearGroups();
                            reply(context, "clear");
                            return 1;
                        }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, int type, String name) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        CloudWeather.spawnOverPlayer(type, player.getX(), player.getZ());
        reply(context, name);
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> context, String name) {
        context.getSource().sendSuccess(() -> Component.translatable("hbm_farview.command.weather." + name), false);
    }

    private static void openConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        // The chat closes after the command, so the screen is opened one step later.
        ModList.get().getModContainerById(HbmFarView.MOD_ID).ifPresent(container ->
                minecraft.tell(() -> minecraft.setScreen(new ConfigurationScreen(container, null))));
    }
}
