package com.github.hoqhuuep.islandcraft.realestate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class RealEstateManager {
	private final RealEstateDatabase database;
	private final RealEstateConfig config;
	private final Map<String, IslandDeed> lastIsland;
	private final Map<String, Geometry> geometryMap;
	private final Set<SerializableLocation> loadedIslands;

	public RealEstateManager(final RealEstateDatabase database, final RealEstateConfig config) {
		this.database = database;
		this.config = config;
		lastIsland = new HashMap<String, IslandDeed>();
		geometryMap = new HashMap<String, Geometry>();
		loadedIslands = new HashSet<SerializableLocation>();
	}

	/**
	 * To be called when a chunk is loaded. Creates WorldGuard regions if they
	 * do not exist.
	 * 
	 * @param x
	 * @param z
	 */
	public void onLoad(final Location location, final long worldSeed) {
		final World world = location.getWorld();
		if (world == null) {
			// Not ready
			return;
		}
		final Geometry geometry = getGeometry(world.getName());
		if (geometry == null) {
			// Not an IslandCraft world
			return;
		}
		for (final SerializableLocation island : geometry.getOuterIslands(location)) {
			if (loadedIslands.contains(island)) {
				// Only load once, until server is rebooted
				continue;
			}
			IslandDeed deed = database.loadIsland(island);
			if (deed == null) {
				deed = new IslandDeed();
				deed.setId(new SerializableLocation(island.getWorld(), island.getX(), island.getY(), island.getZ()));
				deed.setInnerRegion(geometry.getInnerRegion(island));
				deed.setOuterRegion(geometry.getOuterRegion(island));
				deed.setOwner(null);
				deed.setTax(-1);
				if (geometry.isSpawn(island)) {
					deed.setStatus(IslandStatus.RESERVED);
					deed.setTitle("Spawn Island");
				} else if (geometry.isResource(island, worldSeed)) {
					deed.setStatus(IslandStatus.RESOURCE);
					deed.setTitle("Resource Island");
				} else {
					deed.setStatus(IslandStatus.NEW);
					deed.setTitle("New Island");
				}
				database.saveIsland(deed);
			}
			loadedIslands.add(island);
			Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
		}
	}

	/**
	 * To be called when a player tries to abandon the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onAbandon(final Player player) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ISLAND_ABANDON_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ISLAND_ABANDON_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);
		if (deed.getStatus() != IslandStatus.PRIVATE || !StringUtils.equals(deed.getOwner(), player.getName())) {
			player.sendMessage(config.M_ISLAND_ABANDON_OWNER_ERROR);
			return;
		}

		// Success
		deed.setStatus(IslandStatus.ABANDONED);
		deed.setTax(-1);
		database.saveIsland(deed);
		player.sendMessage(config.M_ISLAND_ABANDON);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	/**
	 * To be called when a player tries to examine the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onExamine(final Player player) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ISLAND_EXAMINE_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ISLAND_EXAMINE_OCEAN_ERROR);
			return;
		}

		final String world = island.getWorld();
		final int x = island.getX();
		final int z = island.getZ();
		final IslandDeed deed = database.loadIsland(island);
		final IslandStatus status = deed.getStatus();
		final String title = deed.getTitle();
		final String owner = deed.getOwner();
		final int tax = deed.getTax();
		final String taxString;
		if (tax < 0) {
			taxString = "infinite";
		} else {
			taxString = String.valueOf(tax) + " minecraft days";
		}
		if (status == IslandStatus.RESOURCE) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_RESOURCE, title, world, x, z));
		} else if (status == IslandStatus.RESERVED) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_RESERVED, title, world, x, z));
		} else if (status == IslandStatus.NEW) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_NEW, title, world, x, z));
		} else if (status == IslandStatus.ABANDONED) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_ABANDONED, owner, title, world, x, z));
		} else if (status == IslandStatus.REPOSSESSED) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_REPOSSESSED, owner, title, world, x, z));
		} else if (status == IslandStatus.PRIVATE) {
			player.sendMessage(String.format(config.M_ISLAND_EXAMINE_PRIVATE, owner, title, taxString, world, x, z));
		}
	}

	/**
	 * To be called when a player tries to purchase the island at their current
	 * location.
	 * 
	 * @param player
	 */
	public final void onPurchase(final Player player) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ISLAND_PURCHASE_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ISLAND_PURCHASE_OCEAN_ERROR);
			return;
		}

		final IslandDeed deed = database.loadIsland(island);
		final IslandStatus status = deed.getStatus();
		final String name = player.getName();

		if (IslandStatus.RESERVED == status) {
			player.sendMessage(config.M_ISLAND_PURCHASE_RESERVED_ERROR);
			return;
		}
		if (IslandStatus.RESOURCE == status) {
			player.sendMessage(config.M_ISLAND_PURCHASE_RESOURCE_ERROR);
			return;
		}
		if (IslandStatus.PRIVATE == status) {
			final String owner = deed.getOwner();
			if (StringUtils.equals(owner, name)) {
				player.sendMessage(config.M_ISLAND_PURCHASE_SELF_ERROR);
			} else {
				player.sendMessage(config.M_ISLAND_PURCHASE_OTHER_ERROR);
			}
			return;
		}
		// if config.MAX_ISLANDS_PER_PLAYER is -1 then infinite
		if (config.MAX_ISLANDS_PER_PLAYER > 0 && islandCount(name) >= config.MAX_ISLANDS_PER_PLAYER) {
			player.sendMessage(config.M_ISLAND_PURCHASE_MAX_ERROR);
			return;
		}

		final int cost = calculatePurchaseCost(name);

		if (!takeItems(player, config.PURCHASE_COST_ITEM, cost)) {
			// Insufficient funds
			player.sendMessage(String.format(config.M_ISLAND_PURCHASE_FUNDS_ERROR, Integer.toString(cost)));
			return;
		}

		// Success
		deed.setStatus(IslandStatus.PRIVATE);
		deed.setOwner(name);
		deed.setTitle("Private Island");
		deed.setTax(config.TAX_DAYS_INITIAL);
		database.saveIsland(deed);
		player.sendMessage(config.M_ISLAND_PURCHASE);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	public void onTax(final Player player) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ISLAND_TAX_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ISLAND_TAX_OCEAN_ERROR);
			return;
		}
		final String name = player.getName();
		final IslandDeed deed = database.loadIsland(island);
		if (deed.getStatus() != IslandStatus.PRIVATE || !deed.getOwner().equals(name)) {
			player.sendMessage(config.M_ISLAND_TAX_OWNER_ERROR);
			return;
		}

		final int newTax = deed.getTax() + config.TAX_DAYS_INCREASE;
		if (newTax > config.TAX_DAYS_MAX) {
			player.sendMessage(config.M_ISLAND_TAX_MAX_ERROR);
			return;
		}

		final int cost = calculateTaxCost(name);

		if (!takeItems(player, config.TAX_COST_ITEM, cost)) {
			// Insufficient funds
			player.sendMessage(String.format(config.M_ISLAND_TAX_FUNDS_ERROR, Integer.toString(cost)));
			return;
		}

		// Success
		deed.setTax(newTax);
		database.saveIsland(deed);
		player.sendMessage(config.M_ISLAND_TAX);
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	public void onDawn(final String world) {
		final Geometry geometry = getGeometry(world);
		if (geometry == null) {
			// Not an IslandCraft world
			return;
		}
		final List<IslandDeed> deeds = database.loadIslandsByWorld(world);
		for (final IslandDeed deed : deeds) {
			final int tax = deed.getTax();
			if (tax > 0) {
				// Decrement tax
				deed.setTax(tax - 1);
				database.saveIsland(deed);
				Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
			} else if (tax == 0) {
				final IslandStatus status = deed.getStatus();
				if (status == IslandStatus.PRIVATE) {
					// Repossess island
					deed.setStatus(IslandStatus.REPOSSESSED);
					deed.setTax(-1);
					database.saveIsland(deed);
					Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
				} else {
					// TODO regenerate island
					if (status == IslandStatus.REPOSSESSED || status == IslandStatus.ABANDONED) {
						deed.setStatus(IslandStatus.NEW);
						deed.setOwner(null);
						deed.setTitle("New Island");
						deed.setTax(-1);
						database.saveIsland(deed);
						Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
					}
				}
			}
			// tax < 0 => infinite
		}
	}

	/**
	 * To be called when the player tries to rename the island at their current
	 * location.
	 * 
	 * @param player
	 * @param title
	 */
	public final void onRename(final Player player, final String title) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ISLAND_RENAME_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ISLAND_RENAME_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);
		if (deed.getStatus() != IslandStatus.PRIVATE || !StringUtils.equals(deed.getOwner(), player.getName())) {
			player.sendMessage(config.M_ISLAND_RENAME_OWNER_ERROR);
			return;
		}

		// Success
		deed.setTitle(title);
		database.saveIsland(deed);
		player.sendMessage(config.M_ISLAND_RENAME);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	// public void onWarp(final Player player) {
	// final List<IslandInfo> islands = database.loadIslands();
	// Collections.shuffle(islands);
	// for (final IslandInfo island : islands) {
	// final IslandStatus type = island.getStatus();
	// if (type == IslandStatus.NEW || type == IslandStatus.ABANDONED || type ==
	// IslandStatus.REPOSSESSED) {
	// final Location islandLocation = island.getLocation();
	// player.teleport(islandLocation);
	// player.sendMessage(config.M_ISLAND_WARP);
	// return;
	// }
	// }
	// player.sendMessage(config.M_ISLAND_WARP_ERROR);
	// }

	private int calculatePurchaseCost(final String player) {
		return config.PURCHASE_COST_AMOUNT + islandCount(player) * config.PURCHASE_COST_INCREASE;
	}

	private int calculateTaxCost(final String player) {
		return config.TAX_COST_AMOUNT + (islandCount(player) - 1) * config.TAX_COST_INCREASE;
	}

	private int islandCount(final String player) {
		final List<IslandDeed> deeds = database.loadIslandsByOwner(player);
		int count = 0;
		for (final IslandDeed deed : deeds) {
			if (deed.getStatus() == IslandStatus.PRIVATE) {
				++count;
			}
		}
		return count;
	}

	private static final Integer FIRST = new Integer(0);

	private boolean takeItems(final Player player, final Material item, final int amount) {
		final PlayerInventory inventory = player.getInventory();
		if (!inventory.containsAtLeast(new ItemStack(item), amount)) {
			// Not enough
			return false;
		}
		final Map<Integer, ItemStack> result = inventory.removeItem(new ItemStack(item, amount));
		if (!result.isEmpty()) {
			// Something went wrong, refund
			final int missing = result.get(FIRST).getAmount();
			inventory.addItem(new ItemStack(item, amount - missing));
			return false;
		}
		// Success
		return true;
	}

	public void onMove(final Player player, final Location to) {
		final String name = player.getName();
		if (to == null) {
			lastIsland.remove(name);
			return;
		}
		final Geometry geometry = getGeometry(to.getWorld().getName());
		final IslandDeed toIsland;
		if (geometry != null) {
			final SerializableLocation toIslandLocation = geometry.getInnerIsland(to);
			if (toIslandLocation != null) {
				toIsland = database.loadIsland(toIslandLocation);
			} else {
				toIsland = null;
			}
		} else {
			toIsland = null;
		}
		final IslandDeed fromIsland = lastIsland.get(name);
		if (fromIsland != null) {
			if (toIsland == null || !equals(toIsland.getTitle(), fromIsland.getTitle()) || !equals(toIsland.getOwner(), fromIsland.getOwner())) {
				leaveIsland(player, fromIsland);
			}
		}
		if (toIsland != null) {
			if (fromIsland == null || !equals(toIsland.getTitle(), fromIsland.getTitle()) || !equals(toIsland.getOwner(), fromIsland.getOwner())) {
				enterIsland(player, toIsland);
			}
		}
		lastIsland.put(name, toIsland);
	}

	private void enterIsland(final Player player, final IslandDeed deed) {
		final IslandStatus status = deed.getStatus();
		final String title = deed.getTitle();
		final String owner = deed.getOwner();
		if (status == IslandStatus.RESOURCE) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_RESOURCE, title));
		} else if (status == IslandStatus.RESERVED) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_RESERVED, title));
		} else if (status == IslandStatus.NEW) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_NEW, title));
		} else if (status == IslandStatus.ABANDONED) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_ABANDONED, title, owner));
		} else if (status == IslandStatus.REPOSSESSED) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_REPOSSESSED, title, owner));
		} else if (status == IslandStatus.PRIVATE) {
			player.sendMessage(String.format(config.M_ISLAND_ENTER_PRIVATE, title, owner));
		}
	}

	private void leaveIsland(final Player player, final IslandDeed deed) {
		final IslandStatus status = deed.getStatus();
		final String title = deed.getTitle();
		final String owner = deed.getOwner();
		if (status == IslandStatus.RESOURCE) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_RESOURCE, title));
		} else if (status == IslandStatus.RESERVED) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_RESERVED, title));
		} else if (status == IslandStatus.NEW) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_NEW, title));
		} else if (status == IslandStatus.ABANDONED) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_ABANDONED, title, owner));
		} else if (status == IslandStatus.REPOSSESSED) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_REPOSSESSED, title, owner));
		} else if (status == IslandStatus.PRIVATE) {
			player.sendMessage(String.format(config.M_ISLAND_LEAVE_PRIVATE, title, owner));
		}
	}

	private boolean equals(final Object a, final Object b) {
		return (a == null && b == null) || (a != null && b != null && a.equals(b));
	}

	public void addGeometry(final String world, final Geometry geometry) {
		geometryMap.put(world, geometry);
	}

	private Geometry getGeometry(final String world) {
		return geometryMap.get(world);
	}

	public void setTax(final Player player, final int tax) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ICSET_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ICSET_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);

		// Success
		deed.setTax(tax);
		database.saveIsland(deed);
		player.sendMessage(config.M_ICSET);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	public void setTitle(final Player player, final String title) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ICSET_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ICSET_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);

		// Success
		deed.setTitle(title);
		database.saveIsland(deed);
		player.sendMessage(config.M_ICSET);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	public void setOwner(final Player player, final String owner) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ICSET_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ICSET_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);

		// Success
		deed.setOwner(owner);
		database.saveIsland(deed);
		player.sendMessage(config.M_ICSET);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	public void setStatus(final Player player, final IslandStatus status) {
		final Geometry geometry = getGeometry(player.getWorld().getName());
		if (geometry == null) {
			player.sendMessage(config.M_ICSET_WORLD_ERROR);
			return;
		}
		final Location location = player.getLocation();
		final SerializableLocation island = geometry.getInnerIsland(location);
		if (geometry.isOcean(island)) {
			player.sendMessage(config.M_ICSET_OCEAN_ERROR);
			return;
		}
		final IslandDeed deed = database.loadIsland(island);

		// Success
		deed.setStatus(status);
		database.saveIsland(deed);
		player.sendMessage(config.M_ICSET);
		onMove(player, player.getLocation());
		Bukkit.getPluginManager().callEvent(new IslandEvent(deed));
	}

	// private void regenerateRegion(final IslandDeed island, final Geometry
	// geometry) {
	// final Long oldSeed = null; // database.loadSeed(island.getId());
	// final SerializableRegion region = island.getInnerRegion();
	// final int minX = region.getMinX() >> 4;
	// final int minZ = region.getMinZ() >> 4;
	// final int maxX = region.getMaxX() >> 4;
	// final int maxZ = region.getMaxZ() >> 4;
	// if (null != oldSeed) {
	// database.saveSeed(island.getId(), new Long(new
	// Random(oldSeed.longValue()).nextLong()));
	// final World w2 = Bukkit.getWorld(region.getWorld());
	// for (int x = minX; x < maxX; ++x) {
	// for (int z = minZ; z < maxZ; ++z) {
	// w2.unloadChunk(x, z);
	// }
	// }
	// for (int x = minX; x < maxX; ++x) {
	// for (int z = minZ; z < maxZ; ++z) {
	// w2.regenerateChunk(x, z);
	// }
	// }
	// }
	// }
}
