package provanasservices.rewardsystem.service

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.RegionContainer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import provanasservices.rewardsystem.model.RewardMob

/**
 * Service for location-related operations including WorldGuard integration.
 * Handles region checking, world validation, and entity location validation.
 */
object LocationService {
    /**
     * Checks if an entity is in a valid location for a reward.
     * Validates world and region if specified in the reward configuration.
     * 
     * @param entity The entity to check
     * @param reward The reward configuration with location requirements
     * @return true if entity is in a valid location, false otherwise
     */
    fun isEntityInValidLocation(entity: Entity, reward: RewardMob): Boolean {
        // If no world restriction set in config, entity is valid regardless of location
        if (reward.enabledWorld == null || reward.enabledWorld!!.isEmpty()) {
            return true
        }
        
        // Check if entity is in the specified world
        val world = Bukkit.getWorld(reward.enabledWorld!!)
        if (world == null) {
            LoggingService.debugWarning("World '${reward.enabledWorld}' not found, skipping reward")
            return false
        }
        
        // If no region restriction, only world check is needed
        if (reward.enabledRegion == null || reward.enabledRegion!!.isEmpty()) {
            return true
        }
        
        // Check if entity is in the specified region
        return isEntityInRegion(entity, world, reward.enabledRegion!!)
    }
    
    /**
     * Checks if an entity is in a specific WorldGuard region.
     * 
     * @param entity The entity to check
     * @param world The world containing the region
     * @param regionName The name of the region to check
     * @return true if entity is in the region, false otherwise
     */
    private fun isEntityInRegion(entity: Entity, world: World, regionName: String): Boolean {
        try {
            return isLocationInRegion(entity.location, world, regionName)
        } catch (e: Exception) {
            LoggingService.debugWarning("Error checking if entity is in region: ${e.message}")
            return false
        }
    }
    
    /**
     * Checks if a location is within a WorldGuard region.
     * 
     * @param location The location to check
     * @param world The world containing the region
     * @param regionName The name of the region to check
     * @return true if location is in the region, false otherwise
     */
    fun isLocationInRegion(location: Location, world: World, regionName: String): Boolean {
        try {
            // Get WorldGuard instance
            val worldGuard = WorldGuard.getInstance()
            val container = worldGuard.platform.regionContainer
            
            // Validate container exists
            if (container == null) {
                LoggingService.debugWarning("WorldGuard region container is null")
                return false
            }
            
            // Get regions for world
            val regions = container.get(BukkitAdapter.adapt(world)) ?: return false
            
            // Get specific region
            val region = regions.getRegion(regionName) ?: return false
            
            // Check if location is inside region
            return region.contains(BukkitAdapter.asBlockVector(location))
        } catch (e: Exception) {
            LoggingService.debugWarning("Error checking if location is in region: ${e.message}")
            return false
        }
    }

    /**
     * Checks if a location is valid for a specific reward.
     * Validates both world and region requirements.
     *
     * @param location The location to check
     * @param reward The reward configuration with location requirements
     * @return true if the location is valid for the reward
     */
    fun isValidLocation(location: Location, reward: RewardMob): Boolean {
        LoggingService.debug("LOCATION CHECK - Reward ${reward.id} - enabledWorld: '${reward.enabledWorld}', enabledRegion: '${reward.enabledRegion}'")
        
        // Check world restriction
        if (!isValidWorld(location, reward)) {
            LoggingService.debug("Entity in world ${location.world?.name} doesn't match reward ${reward.id} world: ${reward.enabledWorld}")
            return false
        }
        
        // Check if enabledRegion is null, empty, or consists of only whitespace
        val regionEmpty = reward.enabledRegion == null || reward.enabledRegion!!.trim().isEmpty()
        LoggingService.debug("LOCATION CHECK - Reward ${reward.id} - Region check needed: ${!regionEmpty}")
        
        // Check region restriction
        if (!regionEmpty && !isValidRegion(location, reward)) {
            LoggingService.debug("Entity not in required region for reward ${reward.id}")
            return false
        }
        
        return true
    }
    
    /**
     * Checks if the location's world matches the reward world configuration.
     *
     * @param location The location to check
     * @param reward The reward configuration
     * @return true if the world matches or no world restriction exists
     */
    private fun isValidWorld(location: Location, reward: RewardMob): Boolean {
        // If no world restriction is set, any world is valid
        if (reward.enabledWorld == null || reward.enabledWorld!!.trim().isEmpty()) {
            return true
        }
        
        val worldName = location.world?.name ?: return false
        return reward.worldEquals(worldName)
    }
    
    /**
     * Checks if a location is in a valid region for the reward.
     *
     * @param location The location to check
     * @param reward The reward configuration with region requirements
     * @return true if the location is in a valid region or no region is specified
     */
    private fun isValidRegion(location: Location, reward: RewardMob): Boolean {
        // If no region restriction is set, any region is valid
        if (reward.enabledRegion == null || reward.enabledRegion!!.trim().isEmpty()) {
            LoggingService.debug("No region restriction for reward ${reward.id}, skipping region check")
            return true
        }
        
        LoggingService.info("Checking region '${reward.enabledRegion}' for reward ${reward.id} in world ${location.world?.name}")
        
        // Get WorldGuard plugin instance
        try {
            // Skip region check if WorldGuard is not available
            if (!isWorldGuardAvailable()) {
                LoggingService.info("WorldGuard not available for region checks, skipping region validation")
                return true
            }
            
            // Get region container from WorldGuard
            val container = WorldGuard.getInstance().platform.regionContainer
            if (container == null) {
                LoggingService.info("WorldGuard region container is null, skipping region validation")
                return true
            }
            
            // Get region manager for this world
            val regions = container.get(BukkitAdapter.adapt(location.world))
            if (regions == null) {
                LoggingService.info("No region manager for world ${location.world?.name}, skipping region validation")
                return true
            }
            
            // Get regions at the location
            val regionIds = regions.getApplicableRegionsIDs(BukkitAdapter.asBlockVector(location))
            LoggingService.debug("Entity location regions: ${regionIds.joinToString()}")
            
            // Check if the location is in the specified region
            for (regionId in regionIds) {
                if (regionId.equals(reward.enabledRegion, ignoreCase = true)) {
                    LoggingService.debug("Region match found!")
                    return true
                }
            }
            
            // Location is not in any applicable region
            LoggingService.info("Entity is not in region '${reward.enabledRegion}', reward will be skipped")
            return false
        } catch (e: Exception) {
            LoggingService.warning("Error checking region: ${e.message}")
            // In case of error, allow the reward to be given (fail open)
            return true
        }
    }
    
    /**
     * Checks if an entity is in a valid death location for a reward.
     * Similar to isEntityInValidLocation but with stricter validation for death events.
     * 
     * @param entity The entity to check
     * @param reward The reward configuration with location requirements
     * @return true if entity is in a valid death location, false otherwise
     */
    fun isEntityInValidDeathLocation(entity: Entity, reward: RewardMob): Boolean {
        // If no world restriction, entity is valid regardless of death location
        if (reward.enabledWorld == null || reward.enabledWorld!!.trim().isEmpty()) {
            return true
        }
        
        // Check if entity is in the specified world
        val world = Bukkit.getWorld(reward.enabledWorld!!) ?: return false
        
        // If no region restriction, only world check is needed
        if (reward.enabledRegion == null || reward.enabledRegion!!.trim().isEmpty()) {
            return true
        }
        
        // Check if entity is in the specified region
        return try {
            val container = WorldGuard.getInstance().platform.regionContainer
            val regions = container.get(BukkitAdapter.adapt(world)) ?: return false
            val region = regions.getRegion(reward.enabledRegion) ?: return false
            
            region.contains(BukkitAdapter.asBlockVector(entity.location))
        } catch (e: Exception) {
            LoggingService.debugWarning("Error checking region for death location: ${e.message}")
            false
        }
    }
    
    /**
     * Gets nearby players to an entity within a specified radius.
     * 
     * @param entity The central entity
     * @param radius The radius to check
     * @return List of players within the specified radius
     */
    fun getNearbyPlayers(entity: Entity, radius: Double): List<Player> {
        return entity.getNearbyEntities(radius, radius, radius)
            .filterIsInstance<Player>()
    }

    /**
     * Checks if WorldGuard plugin is available on the server.
     * 
     * @return true if WorldGuard is available, false otherwise
     */
    private fun isWorldGuardAvailable(): Boolean {
        return try {
            // Check if WorldGuard class exists
            Class.forName("com.sk89q.worldguard.WorldGuard")
            
            // Attempt to get an instance (will throw exception if unavailable)
            WorldGuard.getInstance()
            true
        } catch (e: Exception) {
            LoggingService.debug("WorldGuard is not available: ${e.message}")
            false
        }
    }
} 