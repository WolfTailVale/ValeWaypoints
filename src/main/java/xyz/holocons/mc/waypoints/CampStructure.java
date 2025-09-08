package xyz.holocons.mc.waypoints;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class CampStructure {
    private final WaypointsPlugin plugin;
    private final List<Location> placedBlocks;
    private final java.util.UUID ownerId;

    public CampStructure(WaypointsPlugin plugin, java.util.UUID ownerId) {
        this.plugin = plugin;
        this.ownerId = ownerId;
        this.placedBlocks = new ArrayList<>();
    }

    /**
     * Creates a CampStructure from saved data (used when loading from file)
     */
    public static CampStructure fromSavedData(WaypointsPlugin plugin, java.util.UUID ownerId, List<Location> structureBlocks) {
        CampStructure structure = new CampStructure(plugin, ownerId);
        structure.placedBlocks.addAll(structureBlocks);
        return structure;
    }

    public java.util.UUID getOwnerId() {
        return ownerId;
    }

    public boolean isPartOfStructure(Location location) {
        return placedBlocks.contains(location);
    }

    public List<Location> getStructureBlocks() {
        return new ArrayList<>(placedBlocks);
    }

    public boolean placeCampStructure(Location bannerLocation, Player player) {
        if (!plugin.getConfig().getBoolean("camp.structure.enabled", true)) {
            return true;
        }
        placedBlocks.clear();
        try {
            if (plugin.getConfig().getBoolean("camp.structure.place-campfire", true)) {
                if (!placeCampfire(bannerLocation, player)) {
                    rollbackPlacement();
                    return false;
                }
            }
            if (plugin.getConfig().getBoolean("camp.structure.place-tent", true)) {
                if (!placeTent(bannerLocation, player)) {
                    rollbackPlacement();
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place camp structure: " + e.getMessage());
            rollbackPlacement();
            return false;
        }
    }

    public void removeCampStructure() {
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (!block.getType().isAir()) {
                block.setType(Material.AIR);
            }
        }
        placedBlocks.clear();
    }

    private boolean placeCampfire(Location bannerLocation, Player player) {
        BlockFace playerFacing = getPlayerFacing(player).getOppositeFace();
        double[] offset = getRotatedOffset(2, 0, 3.5, playerFacing);
        Location tentCenter = bannerLocation.clone().subtract(offset[0], offset[1], offset[2]);
        Location campfireLocation = getRotatedLocation(tentCenter, -2, 0, 3.5, playerFacing);
        Location groundLocation = findGroundLevel(campfireLocation);
        if (groundLocation != null && isSpaceClear(groundLocation, 1, 1, 1)) {
            Block campfireBlock = groundLocation.getBlock();
            campfireBlock.setType(Material.CAMPFIRE);
            if (campfireBlock.getBlockData() instanceof org.bukkit.block.data.Lightable lightable) {
                lightable.setLit(true);
                campfireBlock.setBlockData(lightable);
            }
            placedBlocks.add(groundLocation);
            return true;
        }
        return false;
    }

    private boolean placeTent(Location bannerLocation, Player player) {
        BlockFace playerFacing = getPlayerFacing(player).getOppositeFace();
        double[] offset = getRotatedOffset(2, 0, 3.5, playerFacing);
        Location tentCenter = bannerLocation.clone().subtract(offset[0], offset[1], offset[2]);
        Location groundLocation = findGroundLevel(tentCenter);
        if (groundLocation != null && canPlaceTent(groundLocation)) {
            placeAdvancedTentStructure(groundLocation, playerFacing);
            return true;
        }
        return false;
    }

    private double[] getRotatedOffset(double x, double y, double z, BlockFace facing) {
        double rotatedX, rotatedZ;
        switch (facing) {
            case NORTH:
                rotatedX = -x;
                rotatedZ = -z;
                break;
            case EAST:
                rotatedX = z;
                rotatedZ = -x;
                break;
            case SOUTH:
                rotatedX = x;
                rotatedZ = z;
                break;
            case WEST:
                rotatedX = -z;
                rotatedZ = x;
                break;
            default:
                rotatedX = x;
                rotatedZ = z;
                break;
        }
        return new double[] { rotatedX, y, rotatedZ };
    }

    private BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0)
            yaw += 360;
        if (yaw >= 315 || yaw < 45)
            return BlockFace.SOUTH;
        else if (yaw >= 45 && yaw < 135)
            return BlockFace.WEST;
        else if (yaw >= 135 && yaw < 225)
            return BlockFace.NORTH;
        else
            return BlockFace.EAST;
    }

    private boolean canPlaceTent(Location center) {
        if (!plugin.getConfig().getBoolean("camp.structure.require-clear-space", true))
            return true;
        return isSpaceClear(center, 5, 3, 4);
    }

    private boolean isSpaceClear(Location center, int width, int height, int depth) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -halfDepth; z <= halfDepth; z++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (!checkLoc.getBlock().getType().isAir())
                        return false;
                }
            }
        }
        return true;
    }

    private void placeAdvancedTentStructure(Location center, BlockFace facing) {
        Material tentMaterial = getTentMaterial();
        placeLayer1(center, tentMaterial, facing);
        placeLayer2(center, tentMaterial, facing);
        placeLayer3(center, tentMaterial, facing);
    }

    private void placeLayer1(Location center, Material tentMaterial, BlockFace facing) {
        int[][] layer1Pattern = { { 0, 1, 1, 1, 0 }, { 1, 4, 0, 0, 1 }, { 1, 5, 0, 0, 1 }, { 1, 2, 3, 2, 1 } };
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 5; x++) {
                Location blockLoc = getRotatedLocation(center, x - 2, 0, z - 1.5, facing);
                if (layer1Pattern[z][x] == 1)
                    placeBlock(blockLoc, tentMaterial);
                else if (layer1Pattern[z][x] == 2) {
                    placeBlock(blockLoc, Material.OAK_FENCE);
                    Block fenceBlock = blockLoc.getBlock();
                    if (fenceBlock.getBlockData() instanceof org.bukkit.block.data.type.Fence fenceData) {
                        setFenceConnections(fenceData, facing);
                        fenceBlock.setBlockData(fenceData);
                    }
                } else if (layer1Pattern[z][x] == 3) {
                    placeBlock(blockLoc, Material.OAK_FENCE_GATE);
                    Block gateBlock = blockLoc.getBlock();
                    if (gateBlock.getBlockData() instanceof org.bukkit.block.data.type.Gate gateData) {
                        gateData.setFacing(facing.getOppositeFace());
                        gateData.setInWall(true);
                        gateBlock.setBlockData(gateData);
                    }
                } else if (layer1Pattern[z][x] == 4 || layer1Pattern[z][x] == 5) {
                    Material bedMaterial = getBedMaterial();
                    placeBlock(blockLoc, bedMaterial);
                    Block bedBlock = blockLoc.getBlock();
                    if (bedBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed bedData) {
                        bedData.setFacing(facing.getOppositeFace());
                        bedData.setPart(layer1Pattern[z][x] == 4 ? org.bukkit.block.data.type.Bed.Part.HEAD
                                : org.bukkit.block.data.type.Bed.Part.FOOT);
                        bedBlock.setBlockData(bedData);
                    }
                }
            }
        }
    }

    private void setFenceConnections(org.bukkit.block.data.type.Fence fenceData, BlockFace tentFacing) {
        if (tentFacing == BlockFace.NORTH || tentFacing == BlockFace.SOUTH) {
            fenceData.setFace(BlockFace.EAST, true);
            fenceData.setFace(BlockFace.WEST, true);
        } else {
            fenceData.setFace(BlockFace.NORTH, true);
            fenceData.setFace(BlockFace.SOUTH, true);
        }
    }

    private Location getRotatedLocation(Location center, double x, double y, double z, BlockFace facing) {
        double rotatedX, rotatedZ;
        switch (facing) {
            case NORTH:
                rotatedX = -x;
                rotatedZ = -z;
                break;
            case EAST:
                rotatedX = z;
                rotatedZ = -x;
                break;
            case SOUTH:
                rotatedX = x;
                rotatedZ = z;
                break;
            case WEST:
                rotatedX = -z;
                rotatedZ = x;
                break;
            default:
                rotatedX = x;
                rotatedZ = z;
                break;
        }
        return center.clone().add(rotatedX, y, rotatedZ);
    }

    private void placeLayer2(Location center, Material tentMaterial, BlockFace facing) {
        int[][] layer2Pattern = { { 0, 1, 1, 1, 0 }, { 1, 0, 0, 0, 1 }, { 1, 0, 0, 0, 1 }, { 1, 1, 0, 1, 1 } };
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 5; x++) {
                if (layer2Pattern[z][x] == 1) {
                    Location blockLoc = getRotatedLocation(center, x - 2, 1, z - 1.5, facing);
                    placeBlock(blockLoc, tentMaterial);
                }
            }
        }
    }

    private void placeLayer3(Location center, Material tentMaterial, BlockFace facing) {
        int[][] layer3Pattern = { { 0, 0, 0, 0, 0 }, { 0, 1, 1, 1, 0 }, { 1, 1, 1, 1, 1 }, { 0, 1, 1, 1, 0 } };
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 5; x++) {
                if (layer3Pattern[z][x] == 1) {
                    Location blockLoc = getRotatedLocation(center, x - 2, 2, z - 1.5, facing);
                    placeBlock(blockLoc, tentMaterial);
                }
            }
        }
    }

    private void placeBlock(Location location, Material material) {
        Block block = location.getBlock();
        if (block.getType().isAir()) {
            block.setType(material);
            placedBlocks.add(location.clone());
        }
    }

    private Material getTentMaterial() {
        // Prefer color from player's registered banner base color
        DyeColor color = getBannerBaseColor();
        if (color != null) {
            try {
                return Material.valueOf(color.name() + "_WOOL");
            } catch (IllegalArgumentException ignored) {
                // Fall through to config/default
            }
        }

        // Fallback to config for compatibility
        String materialName = plugin.getConfig().getString("camp.structure.tent-material", "WHITE_WOOL");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid tent material: " + materialName + ", using WHITE_WOOL");
            return Material.WHITE_WOOL;
        }
    }

    private Material getBedMaterial() {
        // Prefer color from player's registered banner base color
        DyeColor color = getBannerBaseColor();
        if (color != null) {
            try {
                return Material.valueOf(color.name() + "_BED");
            } catch (IllegalArgumentException ignored) {
                // Fall through to config/default
            }
        }

        // Fallback to config for compatibility
        String colorName = plugin.getConfig().getString("camp.structure.bed-color", "RED");
        try {
            DyeColor parsed = DyeColor.valueOf(colorName.toUpperCase());
            return Material.valueOf(parsed.name() + "_BED");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bed color: " + colorName + ", using RED_BED");
            return Material.RED_BED;
        }
    }

    /**
     * Determine the base color from the player's registered banner material.
     * Example: BLACK_BANNER -> DyeColor.BLACK
     */
    private DyeColor getBannerBaseColor() {
        try {
            var design = plugin.getCampBannerMap().getPlayerBannerDesign(ownerId);
            if (design == null) return null;
            Material mat = design.getMaterial();
            if (mat == null) return null;
            String name = mat.name();
            if (!name.endsWith("_BANNER")) return null;
            String colorName = name.substring(0, name.length() - "_BANNER".length());
            return DyeColor.valueOf(colorName);
        } catch (Exception e) {
            // Any parsing issues fall back to config/defaults
            return null;
        }
    }

    private Location findGroundLevel(Location startLocation) {
        Location checkLoc = startLocation.clone();
        for (int y = 0; y < 10; y++) {
            Block ground = checkLoc.getBlock();
            Block above = ground.getRelative(BlockFace.UP);
            if (!ground.getType().isAir() && above.getType().isAir())
                return above.getLocation();
            checkLoc.add(0, -1, 0);
        }
        return startLocation.getBlock().getType().isAir() ? startLocation : null;
    }

    private void rollbackPlacement() {
        removeCampStructure();
    }
}
