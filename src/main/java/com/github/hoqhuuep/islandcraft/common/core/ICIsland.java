package com.github.hoqhuuep.islandcraft.common.core;

public class ICIsland {
    private final ICLocation location;
    private final long seed;
    private final String owner;

    public ICIsland(final ICLocation location, final long seed, final String owner) {
        this.location = location;
        this.seed = seed;
        this.owner = owner;
    }

    public final String getOwner() {
        return this.owner;
    }

    public final long getSeed() {
        return this.seed;
    }

    public final ICLocation getLocation() {
        return this.location;
    }
}
