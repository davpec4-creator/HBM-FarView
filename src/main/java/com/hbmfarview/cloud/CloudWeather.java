package com.hbmfarview.cloud;

import com.hbmfarview.FarViewClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Weather of the custom clouds. Everything here is visual and only on this client.
 *
 * When vanilla rain or thunder starts, the cover of the whole sky is lowered, so the clouds close into one
 * connected layer; after the rain they break apart again, step by step. Both speeds are in the config.
 *
 * Groups are large areas of closed, darker clouds: rain groups (dark grey, rain below, lightning flashes) and
 * fallout groups (black, falling dust below). Groups sit in cloud space, so they drift with the wind.
 * Rain groups appear on their own; where and when is computed from the game time, so every player in a world
 * sees the same ones. Fallout groups appear after a nuclear explosion, downwind of it, for a configured time.
 *
 * The strength of the weather is used in steps. Only when a step changes are the cells of that area rebuilt,
 * so a storm grows and fades in a few visible stages instead of rebuilding the clouds every tick.
 */
public final class CloudWeather {
    public static final int RAIN = 0;
    public static final int FALLOUT = 1;
    static final int TINT_MAX = 16;

    private static final int STEPS = 12;
    private static final double RAIN_CLOSING = 0.30;
    private static final double GROUP_CLOSING = 0.34;
    private static final int RAIN_TINT = 7;
    private static final int FALLOUT_TINT = 16;

    private static final int FADE_TICKS = 1200;
    private static final int COMMAND_FADE_TICKS = 200;
    private static final int REFRESH_TICKS = 20;

    private static final int REGION_CELLS = 128;
    private static final long PERIOD_TICKS = 24000L;
    private static final int REGION_REACH = 4;
    private static final int RAIN_MIN_TICKS = 6000;
    private static final int RAIN_EXTRA_TICKS = 8400;

    private static final int LIGHTNING_CHANCE = 240;
    private static final double FLASH_RADIUS = 160.0;
    private static final float FLASH_GAIN = 1.3F;
    private static final float[] FLASH_PATTERN = {1.0F, 0.5F, 0.15F, 0.85F, 0.3F};

    private static final Map<Long, Group> GROUPS = new HashMap<>();
    private static final List<FalloutEvent> EVENTS = new ArrayList<>();
    private static final List<Group> COMMAND_GROUPS = new ArrayList<>();
    private static final Map<Long, Long> SUPPRESSED = new HashMap<>();
    private static final Set<Integer> SEEN_NUKES = new HashSet<>();
    private static final List<double[]> FLASHES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static Group[] active = new Group[0];
    private static double rain;
    private static int rainStep;
    private static long time;
    private static int commandCount;
    private static double localRain;
    private static double localFallout;

    private CloudWeather() {
    }

    /** A large area of closed clouds. x, z and radius are in blocks of cloud space. */
    static final class Group {
        final long id;
        final int type;
        final double x;
        final double z;
        final double radius;
        final long start;
        final int fade;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        long end;
        boolean cleared;
        int step;

        Group(long id, int type, double x, double z, double radius, long start, long end, int fade) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.start = start;
            this.end = end;
            this.fade = fade;
            int reach = Mth.ceil(radius * 1.25 / CloudRenderer.CELL) + 1;
            int cellX = Mth.floor(x / CloudRenderer.CELL);
            int cellZ = Mth.floor(z / CloudRenderer.CELL);
            this.minX = cellX - reach;
            this.minZ = cellZ - reach;
            this.maxX = cellX + reach;
            this.maxZ = cellZ + reach;
        }

        double strength(long t) {
            if (t < start || t >= end) {
                return 0.0;
            }
            return Math.min(1.0, Math.min((t - start) / (double) fade, (end - t) / (double) fade));
        }

        /** 1 in the middle, 0 outside; the edge is uneven so the group is not a circle. */
        double falloff(int cellX, int cellZ) {
            double dx = (cellX + 0.5) * CloudRenderer.CELL - x;
            double dz = (cellZ + 0.5) * CloudRenderer.CELL - z;
            double edge = 0.8 + 0.4 * CloudShape.noise(cellX / 9.0, cellZ / 9.0, (int) id);
            double t = Math.sqrt(dx * dx + dz * dz) / (radius * edge);
            if (t >= 1.0) {
                return 0.0;
            }
            if (t <= 0.55) {
                return 1.0;
            }
            double u = (t - 0.55) / 0.45;
            return 1.0 - u * u * (3.0 - 2.0 * u);
        }

        boolean contains(int cellX, int cellZ) {
            return cellX >= minX && cellX <= maxX && cellZ >= minZ && cellZ <= maxZ;
        }
    }

    /** Fallout groups of one nuclear explosion, worked out once when the explosion is seen. */
    private static final class FalloutEvent {
        final double x;
        final double z;
        final long start;
        final List<Group> groups = new ArrayList<>();
        long end;

        FalloutEvent(double x, double z, long start) {
            this.x = x;
            this.z = z;
            this.start = start;
        }
    }

    // ---- used while building the clouds ----

    /** How much the cover threshold is lowered at this cell. */
    static double closing(int cellX, int cellZ) {
        double closing = rainStep / (double) STEPS * RAIN_CLOSING;
        for (Group group : active) {
            if (group.contains(cellX, cellZ)) {
                closing = Math.max(closing, group.step / (double) STEPS * GROUP_CLOSING * group.falloff(cellX, cellZ));
            }
        }
        return closing;
    }

    /** How dark the cloud at this cell is, from 0 (normal) to TINT_MAX (black). */
    static int tint(int cellX, int cellZ) {
        int tint = 0;
        for (Group group : active) {
            if (group.contains(cellX, cellZ)) {
                int max = group.type == FALLOUT ? FALLOUT_TINT : RAIN_TINT;
                tint = Math.max(tint, (int) Math.round(group.step / (double) STEPS * group.falloff(cellX, cellZ) * max));
            }
        }
        return tint;
    }

    static float tintFactor(int tint) {
        return 1.0F - tint * (0.88F / TINT_MAX);
    }

    /**
     * Brightness of a lightning flash over a rectangle of cloud space (blocks); 1 when there is none.
     * Used per tile by both renderers.
     */
    static float flash(double minX, double minZ, double maxX, double maxZ) {
        float best = 1.0F;
        for (double[] f : FLASHES) {
            int age = (int) (time - (long) f[2]);
            if (age < 0 || age >= FLASH_PATTERN.length) {
                continue;
            }
            double dx = Math.max(0.0, Math.max(minX - f[0], f[0] - maxX));
            double dz = Math.max(0.0, Math.max(minZ - f[1], f[1] - maxZ));
            double near = 1.0 - Math.sqrt(dx * dx + dz * dz) / FLASH_RADIUS;
            if (near > 0.0) {
                best = Math.max(best, 1.0F + FLASH_GAIN * FLASH_PATTERN[age] * (float) near);
            }
        }
        return best;
    }

    // ---- used by the effects under the clouds ----

    /** Strength of the rain from rain groups where the player is, 0 to 1. */
    public static float localRain() {
        return (float) localRain;
    }

    /** Strength of the fallout where the player is, 0 to 1. */
    public static float localFallout() {
        return (float) localFallout;
    }

    // ---- commands ----

    /** Puts a group of the given type over the player; it comes in within a few seconds. */
    public static void spawnOverPlayer(int type, double worldX, double worldZ) {
        int minutes = type == FALLOUT ? FarViewClientConfig.falloutMinMinutes() + RANDOM.nextInt(FarViewClientConfig.falloutMaxMinutes() - FarViewClientConfig.falloutMinMinutes() + 1) : 10;
        double radius = (type == FALLOUT ? 24 : 36) * CloudRenderer.CELL;
        long id = mix(0x5EED, ++commandCount) ^ 0x2C2C;
        COMMAND_GROUPS.add(new Group(id, type, worldX - wind(time), worldZ, radius, time, time + minutes * 1200L, COMMAND_FADE_TICKS));
        refresh(Mth.floor((worldX - wind(time)) / CloudRenderer.CELL), Mth.floor(worldZ / CloudRenderer.CELL));
    }

    /** Fades out all groups within a few seconds and stops the fallout of past explosions. */
    public static void clearGroups() {
        EVENTS.clear();
        COMMAND_GROUPS.clear();
        for (Group group : GROUPS.values()) {
            SUPPRESSED.put(group.id, group.end);
            if (group.end > time + COMMAND_FADE_TICKS) {
                group.end = time + COMMAND_FADE_TICKS;
            }
            group.cleared = true;
        }
    }

    // ---- tick ----

    static void reset() {
        GROUPS.clear();
        EVENTS.clear();
        COMMAND_GROUPS.clear();
        SUPPRESSED.clear();
        SEEN_NUKES.clear();
        FLASHES.clear();
        active = new Group[0];
        rain = 0.0;
        rainStep = 0;
        localRain = 0.0;
        localFallout = 0.0;
    }

    /** A nuclear explosion was seen. Decides once whether it brings fallout and plans its groups. */
    static void onNuke(Entity entity, long gameTime) {
        if (!SEEN_NUKES.add(entity.getId()) || !FarViewClientConfig.fallout()) {
            return;
        }
        double x = entity.getX();
        double z = entity.getZ();
        long start = gameTime - entity.tickCount;
        for (FalloutEvent event : EVENTS) {
            double dx = event.x - x;
            double dz = event.z - z;
            if (dx * dx + dz * dz < 96.0 * 96.0 && Math.abs(event.start - start) < 400) {
                return;
            }
        }
        // The seed only depends on the place, so every player gets the same decision and the same groups.
        long seed = mix(Mth.floor(x) >> 3, Mth.floor(z) >> 3);
        if (random(seed, 9) * 100.0 >= FarViewClientConfig.falloutChance()) {
            return;
        }
        FalloutEvent event = new FalloutEvent(x, z, start);
        plan(event, seed);
        if (!event.groups.isEmpty()) {
            EVENTS.add(event);
        }
    }

    /**
     * Fallout groups appear one after another while the generation time lasts. Each one starts downwind of the
     * explosion (the wind blows towards +X), not over the explosion itself, and within the maximum distance.
     */
    private static void plan(FalloutEvent event, long seed) {
        long generation = FarViewClientConfig.falloutMinutes() * 1200L;
        int minLife = FarViewClientConfig.falloutMinMinutes() * 1200;
        int maxLife = Math.max(minLife, FarViewClientConfig.falloutMaxMinutes() * 1200);
        double maxDistance = FarViewClientConfig.falloutDistance();
        long t = event.start + 200 + (long) (random(seed, 20) * 400);
        int n = 0;
        while (t < event.start + generation && n < 64) {
            long id = mix(seed, n + 1);
            double radius = (18 + random(id, 3) * 14) * CloudRenderer.CELL;
            double minDistance = Math.min(radius * 0.8 + 64.0, maxDistance);
            double distance = minDistance + random(id, 2) * Math.max(0.0, maxDistance - minDistance);
            double angle = (random(id, 1) - 0.5) * 2.0 * Math.toRadians(75.0);
            double worldX = event.x + Math.cos(angle) * distance;
            double worldZ = event.z + Math.sin(angle) * distance;
            long life = minLife + (long) (random(id, 4) * (maxLife - minLife));
            event.groups.add(new Group(id, FALLOUT, worldX - wind(t), worldZ, radius, t, t + life, FADE_TICKS));
            event.end = Math.max(event.end, t + life);
            t += 500 + (long) (random(id, 5) * 600);
            n++;
        }
    }

    static void tick(ClientLevel level, long gameTime, double camCloudX, double camY, double camZ, double cloudY) {
        time = gameTime;
        int camCellX = Mth.floor(camCloudX / CloudRenderer.CELL);
        int camCellZ = Mth.floor(camZ / CloudRenderer.CELL);

        // The clouds start to close as soon as it rains; how fast they close and open is set in the config.
        double target = FarViewClientConfig.rainClosesClouds() && level.getRainLevel(1.0F) > 0.0F ? 1.0 : 0.0;
        if (target > rain) {
            rain = Math.min(target, rain + perTick(FarViewClientConfig.rainCloseSeconds()));
        } else {
            rain = Math.max(target, rain - perTick(FarViewClientConfig.rainOpenSeconds()));
        }
        int step = (int) Math.round(rain * STEPS);
        if (step != rainStep) {
            rainStep = step;
            CloudLod.clear();
            CloudDirty.markAll();
        }

        if (gameTime % REFRESH_TICKS == 0) {
            refresh(camCellX, camCellZ);
        }

        List<Group> now = new ArrayList<>();
        Iterator<Group> it = GROUPS.values().iterator();
        while (it.hasNext()) {
            Group group = it.next();
            int q = (int) Math.round(group.strength(gameTime) * STEPS);
            if (q != group.step) {
                group.step = q;
                changed(group);
            }
            if (group.step > 0) {
                now.add(group);
            } else if (gameTime >= group.end) {
                it.remove();
            }
        }
        active = now.toArray(new Group[0]);

        lightning(gameTime);
        local(gameTime, camCloudX, camY, camZ, cloudY);
    }

    /** Works out which groups exist around the player now. */
    private static void refresh(int camCellX, int camCellZ) {
        Map<Long, Group> wanted = new HashMap<>();
        if (FarViewClientConfig.rainGroups()) {
            int regionX = Math.floorDiv(camCellX, REGION_CELLS);
            int regionZ = Math.floorDiv(camCellZ, REGION_CELLS);
            long period = Math.floorDiv(time, PERIOD_TICKS);
            for (int rx = regionX - REGION_REACH; rx <= regionX + REGION_REACH; rx++) {
                for (int rz = regionZ - REGION_REACH; rz <= regionZ + REGION_REACH; rz++) {
                    for (long p = period - 1; p <= period; p++) {
                        Group group = rainGroup(rx, rz, p);
                        if (group != null) {
                            wanted.put(group.id, group);
                        }
                    }
                }
            }
        }
        Iterator<FalloutEvent> events = EVENTS.iterator();
        while (events.hasNext()) {
            FalloutEvent event = events.next();
            if (time >= event.end) {
                events.remove();
                continue;
            }
            for (Group group : event.groups) {
                if (time >= group.start && time < group.end) {
                    wanted.put(group.id, group);
                }
            }
        }
        COMMAND_GROUPS.removeIf(group -> time >= group.end);
        for (Group group : COMMAND_GROUPS) {
            wanted.put(group.id, group);
        }
        SUPPRESSED.values().removeIf(until -> until < time);

        Iterator<Group> it = GROUPS.values().iterator();
        while (it.hasNext()) {
            Group group = it.next();
            if (!wanted.containsKey(group.id) && !group.cleared) {
                if (group.step > 0) {
                    group.step = 0;
                    changed(group);
                }
                it.remove();
            }
        }
        for (Group group : wanted.values()) {
            if (!SUPPRESSED.containsKey(group.id)) {
                GROUPS.putIfAbsent(group.id, group);
            }
        }
    }

    /** The rain group of one region and period of time, or null when there is none. */
    private static Group rainGroup(int regionX, int regionZ, long period) {
        long id = mix(mix(regionX, regionZ), period) ^ 0x7A1L;
        if (random(id, 0) * 100.0 >= FarViewClientConfig.rainGroupChance()) {
            return null;
        }
        long start = period * PERIOD_TICKS + (long) (random(id, 1) * PERIOD_TICKS * 0.6);
        long end = start + RAIN_MIN_TICKS + (long) (random(id, 2) * RAIN_EXTRA_TICKS);
        if (time < start || time >= end) {
            return null;
        }
        double regionBlocks = REGION_CELLS * (double) CloudRenderer.CELL;
        double x = (regionX + 0.2 + 0.6 * random(id, 3)) * regionBlocks;
        double z = (regionZ + 0.2 + 0.6 * random(id, 4)) * regionBlocks;
        double radius = (24 + random(id, 5) * 24) * CloudRenderer.CELL;
        return new Group(id, RAIN, x, z, radius, start, end, FADE_TICKS);
    }

    /** The cells of a group changed: they are found again by CloudLod and rebuilt. */
    private static void changed(Group group) {
        CloudLod.invalidate(group.minX, group.minZ, group.maxX, group.maxZ);
        CloudDirty.markArea(group.minX, group.minZ, group.maxX, group.maxZ);
    }

    /** Fully grown rain groups flash now and then at a random place inside them. */
    private static void lightning(long gameTime) {
        FLASHES.removeIf(f -> gameTime - (long) f[2] >= FLASH_PATTERN.length);
        if (!FarViewClientConfig.lightning()) {
            return;
        }
        for (Group group : active) {
            if (group.type == RAIN && group.step >= STEPS - 1 && RANDOM.nextInt(LIGHTNING_CHANCE) == 0) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                double distance = Math.sqrt(RANDOM.nextDouble()) * group.radius * 0.6;
                FLASHES.add(new double[]{group.x + Math.cos(angle) * distance, group.z + Math.sin(angle) * distance, gameTime});
            }
        }
    }

    /** Rain and fallout where the player is; changes smoothly so walking out of a group fades the effects. */
    private static void local(long gameTime, double camCloudX, double camY, double camZ, double cloudY) {
        double rainTarget = 0.0;
        double falloutTarget = 0.0;
        if (camY < cloudY + 24.0) {
            int cellX = Mth.floor(camCloudX / CloudRenderer.CELL);
            int cellZ = Mth.floor(camZ / CloudRenderer.CELL);
            for (Group group : GROUPS.values()) {
                if (!group.contains(cellX, cellZ)) {
                    continue;
                }
                double value = group.strength(gameTime) * group.falloff(cellX, cellZ);
                if (group.type == RAIN) {
                    rainTarget = Math.max(rainTarget, value);
                } else {
                    falloutTarget = Math.max(falloutTarget, value);
                }
            }
        }
        localRain += (rainTarget - localRain) * 0.05;
        localFallout += (falloutTarget - localFallout) * 0.05;
        if (localRain < 0.001) {
            localRain = 0.0;
        }
        if (localFallout < 0.001) {
            localFallout = 0.0;
        }
    }

    /** Change per tick for a transition that takes this many seconds; 0 seconds means at once. */
    private static double perTick(int seconds) {
        return seconds <= 0 ? 1.0 : 1.0 / (seconds * 20.0);
    }

    private static double wind(long gameTime) {
        return gameTime * CloudRenderer.WIND_BLOCKS_PER_TICK;
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    private static double random(long id, int salt) {
        return (mix(id, salt) >>> 11) / (double) (1L << 53);
    }
}
