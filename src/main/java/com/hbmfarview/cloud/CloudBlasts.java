package com.hbmfarview.cloud;

import com.hbmfarview.FarViewClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The pressure wave of a nuclear explosion pushes the custom clouds away. A ring opens outwards from the blast
 * over about 20 seconds;
 * inside it the sky is clear, and the clouds it passed are pressed into a band just outside the ring. The band
 * is lifted most at its inner edge, so it rises like a cone around the mushroom cloud. When the ring has reached
 * its full size it stays while the mushroom cloud is there; after the mushroom cloud is gone the ring slowly
 * shrinks and the clouds flow back.
 *
 * This works by moving where the cloud shape is read from (see CloudShape.column), so the clouds keep their
 * shape, only pushed outwards. The ring is kept in cloud space and drifts with the wind. The radius is used in
 * whole cells; each time it changes, the cells around the ring are rebuilt and the changed cells fade (see
 * CloudRenderer), which makes the animation.
 */
final class CloudBlasts {
    /** How long the ring takes to open fully. It is fast at first and slows down, like the rising mushroom cloud. */
    private static final int EXPAND_TICKS = 400;
    private static final int LIFT_LAYERS = 3;
    private static final double PILE = 0.28;
    private static final int RETURN_TICKS = 2400;
    private static final int MAX_HOLD_TICKS = 12000;

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static Blast[] active = new Blast[0];

    private CloudBlasts() {
    }

    private static final class Blast {
        final int entityId;
        final double x;
        final double z;
        final long start;
        final double maxRadius;
        final double band;
        boolean held = true;
        long release;
        double releaseRadius;
        int cells;
        int appliedCells;

        Blast(int entityId, double x, double z, long start, double maxRadius) {
            this.entityId = entityId;
            this.x = x;
            this.z = z;
            this.start = start;
            this.maxRadius = maxRadius;
            this.band = Math.max(48.0, maxRadius * 0.35);
        }

        double radius() {
            return appliedCells * (double) CloudRenderer.CELL;
        }
    }

    static boolean any() {
        return active.length > 0;
    }

    static void reset() {
        BLASTS.clear();
        SEEN.clear();
        active = new Blast[0];
    }

    /** A nuclear explosion was seen; its pressure wave starts at the mushroom cloud. */
    static void onNuke(Entity entity, long gameTime, double wind) {
        if (!SEEN.add(entity.getId()) || !FarViewClientConfig.blastPushesClouds()) {
            return;
        }
        long start = gameTime - Math.min(entity.tickCount, 200);
        BLASTS.add(new Blast(entity.getId(), entity.getX() - wind, entity.getZ(), start, FarViewClientConfig.blastCloudRadius()));
    }

    static void tick(ClientLevel level, long gameTime) {
        Iterator<Blast> it = BLASTS.iterator();
        List<Blast> now = new ArrayList<>();
        while (it.hasNext()) {
            Blast blast = it.next();
            double x = Math.min(1.0, (gameTime - blast.start) / (double) EXPAND_TICKS);
            double grown = blast.maxRadius * (1.0 - (1.0 - x) * (1.0 - x));
            if (blast.held) {
                Entity entity = level.getEntity(blast.entityId);
                if (entity == null || entity.isRemoved() || gameTime - blast.start > MAX_HOLD_TICKS) {
                    blast.held = false;
                    blast.release = gameTime;
                    blast.releaseRadius = grown;
                }
            }
            double radius = grown;
            if (!blast.held) {
                double back = 1.0 - (gameTime - blast.release) / (double) RETURN_TICKS;
                radius = Math.min(grown, blast.releaseRadius * Math.max(0.0, back));
            }
            int cells = Mth.floor(radius / CloudRenderer.CELL);
            if (cells != blast.appliedCells) {
                double reach = Math.max(cells, blast.appliedCells) * (double) CloudRenderer.CELL;
                blast.appliedCells = cells;
                changed(blast, reach);
            }
            blast.cells = cells;
            if (!blast.held && cells == 0 && blast.appliedCells == 0) {
                it.remove();
            } else if (blast.appliedCells > 0) {
                now.add(blast);
            }
        }
        active = now.toArray(new Blast[0]);
    }

    /**
     * Where the shape of this cell is read from. out[0], out[1] = cell position to read the shape at,
     * out[2] = extra layers the cell is lifted, out[3] = how much thicker the pushed clouds are (they pile up
     * at the ring). Returns false when the ring has cleared the cell.
     */
    static boolean warp(int cellX, int cellZ, double[] out) {
        double px = (cellX + 0.5) * CloudRenderer.CELL;
        double pz = (cellZ + 0.5) * CloudRenderer.CELL;
        int lift = 0;
        double pile = 0.0;
        for (Blast blast : active) {
            double dx = px - blast.x;
            double dz = pz - blast.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            double r = blast.radius();
            if (d >= r + blast.band) {
                continue;
            }
            if (d < r) {
                return false;
            }
            // The band from r to r + band holds everything from the middle out to r + band.
            double t = (d - r) / blast.band;
            double s = t * (r + blast.band);
            px = blast.x + dx / d * s;
            pz = blast.z + dz / d * s;
            double strength = (1.0 - t) * Math.min(1.0, r / blast.band);
            lift = Math.max(lift, (int) Math.round(LIFT_LAYERS * strength));
            pile = Math.max(pile, PILE * strength);
        }
        out[0] = px / CloudRenderer.CELL - 0.5;
        out[1] = pz / CloudRenderer.CELL - 0.5;
        out[2] = lift;
        out[3] = pile;
        return true;
    }

    private static void changed(Blast blast, double radius) {
        int reach = Mth.ceil((radius + blast.band) / CloudRenderer.CELL) + 1;
        int cx = Mth.floor(blast.x / CloudRenderer.CELL);
        int cz = Mth.floor(blast.z / CloudRenderer.CELL);
        CloudLod.invalidate(cx - reach, cz - reach, cx + reach, cz + reach);
        CloudDirty.markArea(cx - reach, cz - reach, cx + reach, cz + reach);
    }
}
