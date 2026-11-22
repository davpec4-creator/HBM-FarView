package com.hbmfarview.cloud;

import net.minecraft.util.Mth;

/**
 * Shape of the custom clouds. The sky is a grid of cells; for every cell the noise gives a
 * height in layers (0 = clear sky). The middle of a cloud has the highest density, so it gets
 * the most layers and the cloud steps down towards its edges. A slow "type" noise decides
 * whether an area has flat clouds or tall heaped ones.
 *
 * Weather lowers the cover threshold (see CloudWeather): clouds grow, the gaps between them fill
 * with thinner cloud and the sky closes into one connected layer, which still has steps and heights.
 */
public final class CloudShape {
    public static final int MAX_LAYERS = 5;
    /** How many layers above the vanilla cloud height a cloud can sit (8 layers = 32 blocks). */
    public static final int MAX_LIFT = 8;

    private static final int SEED = 0x4B1D;
    private static final double COVER = 0.56;
    private static final double FULL = 0.20;

    private CloudShape() {
    }

    /**
     * Fills the column of one cell, in layers: out[0] = lowest layer, out[1] = top (exclusive),
     * out[2] = base of the cloud the cell belongs to. Returns false when the cell is clear sky.
     * The middle of a cloud is the tallest and also hangs lowest, so both the top and the bottom
     * step towards the edges. Each cloud sits at its own height, given by a slow noise.
     */
    public static boolean column(int cellX, int cellZ, int[] out) {
        double x = cellX;
        double z = cellZ;
        int pushed = 0;
        double piled = 0.0;
        if (CloudBlasts.any()) {
            // A pressure wave pushes the clouds outwards: read the shape from further in.
            double[] warp = new double[4];
            if (!CloudBlasts.warp(cellX, cellZ, warp)) {
                return false;
            }
            x = warp[0];
            z = warp[1];
            pushed = (int) warp[2];
            piled = warp[3];
        }
        double warpX = x + (noise(x / 18.0, z / 18.0, SEED + 10) - 0.5) * 7.0;
        double warpZ = z + (noise(x / 18.0, z / 18.0, SEED + 11) - 0.5) * 7.0;

        double density = noise(warpX / 13.0, warpZ / 13.0, SEED) * 0.62
                + noise(warpX / 5.5, warpZ / 5.5, SEED + 1) * 0.31
                + noise(x / 2.2, z / 2.2, SEED + 2) * 0.07;
        double closing = Math.max(CloudWeather.closing(cellX, cellZ), piled);
        double cover = COVER - closing;
        if (density < cover) {
            return false;
        }

        double type = noise(x / 45.0, z / 45.0, SEED + 4);
        int max = 2 + (int) Math.round(Mth.clamp((type - 0.3) / 0.4, 0.0, 1.0) * (MAX_LAYERS - 2));
        // The range grows with the closing, so a closed sky keeps thin and thick parts.
        double height = Mth.clamp((density - cover) / (FULL + closing), 0.0, 1.0);
        int up = Math.min(max, 1 + (int) (height * max));
        int maxDown = max / 2;
        int down = Math.min(maxDown, (int) (height * (maxDown + 1)));
        int base = Math.min(MAX_LIFT, (int) (noise(warpX / 32.0, warpZ / 32.0, SEED + 5) * (MAX_LIFT + 1)));

        base += pushed;
        out[0] = base - down;
        out[1] = base + up;
        out[2] = base;
        return true;
    }

    /** Small brightness change per cell, so large flat faces are not perfectly even. */
    public static float shade(int cellX, int cellZ, int layer) {
        return 1.0F - 0.04F * (float) hash(cellX, cellZ + layer * 7919, SEED + 3);
    }

    static double noise(double x, double z, int seed) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = hash(x0, z0, seed);
        double b = hash(x0 + 1, z0, seed);
        double c = hash(x0, z0 + 1, seed);
        double d = hash(x0 + 1, z0 + 1, seed);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
