package com.github.hoqhuuep.islandcraft.worldguard;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.github.hoqhuuep.islandcraft.realestate.SerializableRegion;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class WorldGuardManager {
    private final WorldGuardPlugin worldGuard;

    public WorldGuardManager(final WorldGuardPlugin worldGuard) {
        this.worldGuard = worldGuard;
    }

    public void setPrivate(final SerializableRegion region, final String player) {
        final RegionManager regionManager = getRegionManager(region);
        if (regionManager == null) {
            return;
        }
        final ProtectedRegion protectedRegion = createProtectedRegion(regionManager, region, player);
        if (protectedRegion == null) {
            return;
        }
        if (regionManager.hasRegion(protectedRegion.getId()) && protectedRegion.getOwners().size() == 1 && protectedRegion.isOwner(player)) {
            return;
        }
        final DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(player);
        protectedRegion.setOwners(owners);
        addProtectedRegion(regionManager, protectedRegion);
    }

    public void setReserved(final SerializableRegion region) {
        final RegionManager regionManager = getRegionManager(region);
        if (regionManager == null) {
            return;
        }
        final ProtectedRegion protectedRegion = createProtectedRegion(regionManager, region, null);
        if (protectedRegion == null) {
            return;
        }
        if (regionManager.hasRegion(protectedRegion.getId()) && protectedRegion.getOwners().size() == 0) {
            return;
        }
        protectedRegion.setFlag(DefaultFlag.BUILD, State.DENY);
        addProtectedRegion(regionManager, protectedRegion);
    }

    public void setPublic(final SerializableRegion region) {
        final RegionManager regionManager = getRegionManager(region);
        if (regionManager == null) {
            return;
        }
        final ProtectedRegion protectedRegion = createProtectedRegion(regionManager, region, null);
        if (protectedRegion == null) {
            return;
        }
        if (regionManager.hasRegion(protectedRegion.getId()) && protectedRegion.getOwners().size() == 0) {
            return;
        }
        protectedRegion.setFlag(DefaultFlag.BUILD, State.ALLOW);
        addProtectedRegion(regionManager, protectedRegion);
    }

    private RegionManager getRegionManager(final SerializableRegion region) {
        final World world = Bukkit.getWorld(region.getWorld());
        if (world == null) {
            return null;
        }
        final RegionManager regionManager = worldGuard.getRegionManager(world);
        return regionManager;
    }

    private ProtectedRegion createProtectedRegion(final RegionManager regionManager, final SerializableRegion region, final String owner) {
        final int minX = region.getMinX();
        final int minY = region.getMinY();
        final int minZ = region.getMinZ();
        final int maxX = region.getMaxX();
        final int maxY = region.getMaxY();
        final int maxZ = region.getMaxZ();

        final BlockVector p1 = new BlockVector(minX, minY, minZ);
        final BlockVector p2 = new BlockVector(maxX - 1, maxY - 1, maxZ - 1);

        // Check if region already exists
        ProtectedRegion result = null;
        for (final ProtectedRegion existingRegion : regionManager.getApplicableRegions(p1)) {
            if (existingRegion.getMinimumPoint().equals(p1) && existingRegion.getMaximumPoint().equals(p2)) {
                // Existing region with exact same dimensions
                final String id = existingRegion.getId();
                if (result == null) {
                    if (owner == null) {
                        if (id.matches("^ic'\\w+('\\d+){6}$")) {
                            // Good enough, just use this one
                            result = existingRegion;
                            continue;
                        }
                    } else if (id.matches("^" + owner + "'\\d+$")) {
                        // Good enough, just use this one
                        result = existingRegion;
                        continue;
                    }
                }
                // Existing region that does not have correct id
                regionManager.removeRegion(id);
            }
        }
        if (result != null) {
            return result;
        }

        // Create a new region
        String id;
        if (owner == null) {
            id = "ic'" + region.getWorld() + "'" + minX + "'" + minY + "'" + minZ + "'" + maxX + "'" + maxY + "'" + maxZ;
        } else {
            // Should not run-away due to max-islands-per-player
            int i = 0;
            do {
                id = owner + "'" + i++;
            } while (regionManager.hasRegion(id));
        }
        return new ProtectedCuboidRegion(id, p1, p2);
    }

    private void addProtectedRegion(final RegionManager regionManager, final ProtectedRegion protectedRegion) {
        regionManager.addRegion(protectedRegion);
        try {
            regionManager.save();
        } catch (final ProtectionDatabaseException e) {
        }
    }
}
