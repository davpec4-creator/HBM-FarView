package com.hbmfarview;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

public final class FarView {
    private static final String[] IDS = {
            "nuke_torex",
            "cloud_fleija",
            "cloud_solinium",
            "cloud_rainbow",
            "entity_moonstone_blast"
    };

    private static final String[] NUKE_IDS = {
            "nuke_torex"
    };

    private static final String[] MISSILE_IDS = {
            "entity_missile_micro",
            "entity_missile_taint",
            "entity_missile_generic",
            "entity_missile_decoy",
            "entity_missile_incendiary",
            "entity_missile_bunker_buster",
            "entity_missile_strong",
            "entity_missile_incendiary_strong",
            "entity_missile_buster_strong",
            "entity_missile_emp_strong",
            "entity_missile_burst",
            "entity_missile_inferno",
            "entity_missile_nuclear",
            "entity_missile_mirv",
            "entity_missile_doomsday",
            "entity_missile_doomsday_rusted",
            "entity_missile_n2",
            "entity_missile_stealth",
            "entity_missile_ab"
    };

    private static Set<EntityType<?>> types;
    private static Set<EntityType<?>> missileTypes;
    private static Set<EntityType<?>> nukeTypes;
    private static final long SEEN_NANOS = 2_000_000_000L;

    private static Entity lastChecked;
    private static long lastSeen;
    private static boolean seen;

    private FarView() {
    }

    public static boolean isFarVisible(Entity entity) {
        Set<EntityType<?>> set = types;
        if (set == null) {
            set = resolve();
            types = set;
        }
        return set.contains(entity.getType());
    }

    /**
     * Like {@link #isFarVisible}, but also asks the client config whether this kind of effect should be shown.
     * Only call this on the client.
     */
    public static boolean isShownFar(Entity entity) {
        if (!isFarVisible(entity)) {
            return false;
        }
        return missileTypes.contains(entity.getType())
                ? FarViewClientConfig.missiles()
                : FarViewClientConfig.blastEffects();
    }

    /** True for the NTM missiles. */
    public static boolean isMissile(Entity entity) {
        if (types == null) {
            types = resolve();
        }
        return missileTypes.contains(entity.getType());
    }

    /** True for the mushroom cloud of a nuclear explosion; it starts the fallout weather. */
    public static boolean isNuke(Entity entity) {
        if (types == null) {
            types = resolve();
        }
        return nukeTypes.contains(entity.getType());
    }

    public static void markChecked(Entity entity) {
        boolean far = isShownFar(entity);
        lastChecked = far ? entity : null;
        if (far) {
            lastSeen = System.nanoTime();
            seen = true;
        }
    }

    public static boolean recentlySeen() {
        return seen && System.nanoTime() - lastSeen < SEEN_NANOS;
    }

    public static float farPlane() {
        int range = FarViewConfig.range();
        return range > 0 ? range * 1.5F : 0.0F;
    }

    public static Entity takeChecked() {
        Entity entity = lastChecked;
        lastChecked = null;
        return entity;
    }

    private static Set<EntityType<?>> resolve() {
        Set<EntityType<?>> missiles = new HashSet<>();
        add(missiles, MISSILE_IDS);
        missileTypes = missiles;
        Set<EntityType<?>> nukes = new HashSet<>();
        add(nukes, NUKE_IDS);
        nukeTypes = nukes;
        Set<EntityType<?>> set = new HashSet<>(missiles);
        add(set, IDS);
        return set;
    }

    private static void add(Set<EntityType<?>> set, String[] ids) {
        for (String id : ids) {
            BuiltInRegistries.ENTITY_TYPE
                    .getOptional(ResourceLocation.fromNamespaceAndPath("hbm", id))
                    .ifPresent(set::add);
        }
    }
}
