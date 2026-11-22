package com.hbmfarview.cloud;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What a missile does to the custom clouds when it flies through them.
 *
 * Along its path it cuts a tunnel; the edge of the tunnel is ragged. Around the tunnel the cloud is pushed up
 * like a funnel: the cells next to the path rise the most and each ring further out rises one layer less. The
 * cells rise one layer at a time as the missile goes through, so the funnel grows behind it. After about a
 * minute the cells sink back in the same order they rose, and only then does the tunnel close, middle last.
 *
 * Holes and lift are kept in cloud space, so they drift with the wind together with the cloud.
 * Each cell has a bit mask of removed layers (bit = layer + OFFSET) and a lift in layers.
 */
public final class CloudHoles {
    static final int OFFSET = 4;
    static final int LIFT_MAX = 4;

    private static final double MISSILE_RADIUS = 9.0;
    private static final double STEP = 3.0;
    private static final int LIFT_RINGS = 4;
    private static final int RISE_TICKS = 2;
    private static final int RISE_DELAY = 3;
    private static final int HOLD_TICKS = 1200;
    private static final int SETTLE_TICKS = 8;
    private static final int FILL_EXTRA_TICKS = 60;
    private static final int FILL_DEPTH_TICKS = 240;
    private static final int FILL_RANDOM_TICKS = 120;

    private static final Map<Long, int[]> HOLES = new HashMap<>();
    private static final Map<Long, Lift> LIFTS = new HashMap<>();
    private static long tick;

    private CloudHoles() {
    }

    private static final class Lift {
        int target;
        int current;
        long start;
        long settle;
    }

    /** Bit mask of the removed layers of one cell (bit = layer + OFFSET), 0 when the cell is whole. */
    static int mask(int cellX, int cellZ) {
        int[] fill = HOLES.get(CloudLod.pack(cellX, cellZ));
        if (fill == null) {
            return 0;
        }
        int mask = 0;
        for (int bit = 0; bit < fill.length; bit++) {
            if (fill[bit] != 0) {
                mask |= 1 << bit;
            }
        }
        return mask;
    }

    /** How many layers the cell is pushed up by the funnel right now. */
    static int lift(int cellX, int cellZ) {
        Lift lift = LIFTS.get(CloudLod.pack(cellX, cellZ));
        return lift == null ? 0 : lift.current;
    }

    static void reset() {
        HOLES.clear();
        LIFTS.clear();
    }

    static void tick() {
        tick++;
        fillIn();
        moveLifts();
    }

    /** Cuts the cells along the path of the missile during the last tick. */
    static void cut(Entity missile, double wind, double cloudY) {
        double top = cloudY + (CloudShape.MAX_LIFT + CloudShape.MAX_LAYERS + 1) * CloudRenderer.LAYER_HEIGHT;
        double bottom = cloudY - 3 * CloudRenderer.LAYER_HEIGHT;
        double y0 = missile.yo;
        double y1 = missile.getY();
        if (Math.max(y0, y1) < bottom - MISSILE_RADIUS || Math.min(y0, y1) > top + MISSILE_RADIUS) {
            return;
        }
        double dx = missile.getX() - missile.xo;
        double dy = y1 - y0;
        double dz = missile.getZ() - missile.zo;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Mth.ceil(length / STEP));
        int[] column = new int[3];
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            double x = missile.xo + dx * t - wind;
            double y = y0 + dy * t - cloudY;
            double z = missile.zo + dz * t;
            carve(x, y, z, column);
        }
    }

    /**
     * Opens the cells around one point of the path and lifts the rings around it. The tunnel radius changes
     * from cell to cell, so its edge is ragged.
     */
    private static void carve(double x, double y, double z, int[] column) {
        int cell = CloudRenderer.CELL;
        int layerHeight = CloudRenderer.LAYER_HEIGHT;
        double liftReach = MISSILE_RADIUS + LIFT_RINGS * cell;
        int reach = Mth.ceil(liftReach / cell) + 1;
        int cx = Mth.floor(x / cell);
        int cz = Mth.floor(z / cell);
        int cy = Mth.floor(y / layerHeight);
        int layerReach = Mth.ceil(MISSILE_RADIUS * 1.5 / layerHeight);
        for (int i = cx - reach; i <= cx + reach; i++) {
            for (int k = cz - reach; k <= cz + reach; k++) {
                if (!CloudShape.column(i, k, column)) {
                    continue;
                }
                double ddx = (i + 0.5) * cell - x;
                double ddz = (k + 0.5) * cell - z;
                double flat = Math.sqrt(ddx * ddx + ddz * ddz);
                if (flat < liftReach && flat >= MISSILE_RADIUS * 0.8
                        && cy >= column[0] - 2 && cy < column[1] + 2) {
                    int ring = Math.max(0, (int) ((flat - MISSILE_RADIUS * 0.8) / cell));
                    raise(i, k, LIFT_MAX - ring, ring);
                }
                for (int layer = Math.max(column[0], cy - layerReach); layer < Math.min(column[1], cy + layerReach + 1); layer++) {
                    double ddy = (layer + 0.5) * layerHeight - y;
                    double distance = Math.sqrt(flat * flat + ddy * ddy);
                    double radius = MISSILE_RADIUS * (0.8 + 0.7 * random(i, k, layer));
                    if (distance < radius) {
                        open(i, k, layer, 1.0 - distance / radius);
                    }
                }
            }
        }
    }

    private static void raise(int cellX, int cellZ, int target, int ring) {
        if (target <= 0) {
            return;
        }
        long start = tick + (long) ring * RISE_DELAY;
        Lift lift = LIFTS.computeIfAbsent(CloudLod.pack(cellX, cellZ), k -> new Lift());
        if (lift.target == 0) {
            lift.start = start;
        }
        lift.target = Math.max(lift.target, target);
        lift.settle = Math.max(lift.settle, start + HOLD_TICKS);
    }

    /** The tunnel closes after the funnel has settled; the middle of it closes last. */
    private static void open(int cellX, int cellZ, int layer, double depth) {
        int bit = layer + OFFSET;
        if (bit < 0 || bit >= 32) {
            return;
        }
        long key = CloudLod.pack(cellX, cellZ);
        int[] fill = HOLES.computeIfAbsent(key, k -> new int[32]);
        int until = (int) (tick + HOLD_TICKS + LIFT_MAX * SETTLE_TICKS + FILL_EXTRA_TICKS
                + FILL_DEPTH_TICKS * depth + FILL_RANDOM_TICKS * random(cellX, cellZ, layer + 77));
        if (fill[bit] == 0) {
            CloudDirty.markCell(cellX, cellZ);
        }
        fill[bit] = Math.max(fill[bit], until);
    }

    private static void fillIn() {
        Iterator<Map.Entry<Long, int[]>> it = HOLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, int[]> entry = it.next();
            int[] fill = entry.getValue();
            boolean any = false;
            boolean changed = false;
            for (int bit = 0; bit < fill.length; bit++) {
                if (fill[bit] != 0 && fill[bit] <= tick) {
                    fill[bit] = 0;
                    changed = true;
                }
                any |= fill[bit] != 0;
            }
            long key = entry.getKey();
            if (changed) {
                CloudDirty.markCell((int) (key >> 32), (int) key);
            }
            if (!any) {
                it.remove();
            }
        }
    }

    /** Rises one layer every RISE_TICKS until the target, then after the hold sinks one layer every SETTLE_TICKS. */
    private static void moveLifts() {
        Iterator<Map.Entry<Long, Lift>> it = LIFTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Lift> entry = it.next();
            Lift lift = entry.getValue();
            boolean changed = false;
            if (tick < lift.settle) {
                if (tick >= lift.start && lift.current < lift.target && (tick - lift.start) % RISE_TICKS == 0) {
                    lift.current++;
                    changed = true;
                }
            } else if (lift.current > 0 && (tick - lift.settle) % SETTLE_TICKS == 0) {
                lift.current--;
                changed = true;
            }
            long key = entry.getKey();
            if (changed) {
                CloudDirty.markCell((int) (key >> 32), (int) key);
            }
            if (tick >= lift.settle && lift.current == 0) {
                it.remove();
            }
        }
    }

    private static double random(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65536.0;
    }
}
