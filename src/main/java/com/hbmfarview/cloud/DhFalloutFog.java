package com.hbmfarview.cloud;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;

/**
 * Distant Horizons draws its far terrain with its own fog, so the fallout fog would not hide it. While the player
 * stands deep under a fallout cloud, its far fog is set to start at once and be fully thick (not saved to its
 * config); it gets its own settings back when the player leaves. Only touch this class when Distant Horizons is
 * installed.
 */
final class DhFalloutFog {
    private static final String SOURCE = "HBM Far View";
    private static boolean on;

    private DhFalloutFog() {
    }

    static void update(float fallout) {
        boolean want = on ? fallout > 0.2F : fallout > 0.35F;
        if (want == on) {
            return;
        }
        IDhApiFogConfig fog = DhApi.Delayed.configs.graphics().fog();
        IDhApiFarFogConfig far = fog.farFog();
        if (want) {
            fog.enableDhFog().setValue(true, SOURCE);
            far.farFogStartDistance().setValue(0.0F, SOURCE);
            far.farFogEndDistance().setValue(0.01F, SOURCE);
            far.farFogMinThickness().setValue(1.0F, SOURCE);
            far.farFogMaxThickness().setValue(1.0F, SOURCE);
        } else {
            fog.enableDhFog().clearValue();
            far.farFogStartDistance().clearValue();
            far.farFogEndDistance().clearValue();
            far.farFogMinThickness().clearValue();
            far.farFogMaxThickness().clearValue();
        }
        on = want;
    }
}
