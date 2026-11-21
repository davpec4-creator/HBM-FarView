package com.hbmfarview.client;

import com.hbmfarview.HbmFarView;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Minecraft draws terrain layer by layer and then the entities. After the last terrain layer
 * the depth of the Distant Horizons terrain is added, so far hills can hide the mushroom cloud.
 */
@EventBusSubscriber(modid = HbmFarView.MOD_ID, value = Dist.CLIENT)
public final class DhRenderHook {
    private DhRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            DhCompat.beforeEntities(event.getProjectionMatrix());
        }
    }
}
