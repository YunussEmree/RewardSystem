package provanasservices.rewardsystem

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.RegionContainer
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.ServerCommandEvent
import provanasservices.rewardsystem.model.RewardSystemMobDieEvent
import provanasservices.rewardsystem.service.CommandService
import provanasservices.rewardsystem.service.DamageTrackerService
import provanasservices.rewardsystem.service.LocationService
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.service.PlaceholderService
import provanasservices.rewardsystem.service.RewardService
import provanasservices.rewardsystem.model.RewardMob
import net.md_5.bungee.api.ChatColor
import org.bukkit.command.CommandSender
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern

/**
 * Main event listener class that handles Bukkit events for the RewardSystem plugin.
 * Delegates actual processing to appropriate service classes.
 */
class Events(private val plugin: Main) : Listener {
    
    // Services
    private val damageTrackerService: DamageTrackerService
    private val locationService = LocationService
    private val rewardService: RewardService
    
    init {
        // Initialize logging service
        LoggingService.initialize(plugin)
        
        // Initialize services that require plugin instance
        damageTrackerService = DamageTrackerService(plugin)
        rewardService = RewardService(plugin)
    }
    
    /**
     * Monitors player commands for debugging purposes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        LoggingService.debug("Player Command executed: ${event.message}")
    }

    /**
     * Monitors console commands for debugging purposes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onConsoleCommand(event: ServerCommandEvent) {
        LoggingService.debug("Console Command executed: ${event.command}")
    }

    /**
     * Handles entity damage events to track damage dealt by players.
     * 
     * @param event The entity damage event
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        try {
            // Skip if the entity being damaged is a player
            if (event.entity is Player) {
                return
            }
            
            // Process damage tracking
            damageTrackerService.trackDamage(event)
        } catch (e: Exception) {
            LoggingService.severe("Error in damage tracking: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Handles entity death events to distribute rewards.
     * 
     * @param event The entity death event
     */
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        try {
            val entity = event.entity
            val entityUUID = entity.uniqueId
            
            // Performance optimization: Quick checks
            
            // 1. If the entity is a player, exit immediately
            if (entity is Player) {
                return
            }
            
            // 2. If not in damage map and tracking is required, exit immediately
            if (!Main.damageMap.containsKey(entityUUID) && Main.getInstance().config.getBoolean("TrackAllMobs", false)) {
                return
            }
            
            // Enhanced debugging
                LoggingService.debug("Entity death event: ${entity.type.name} (${entity.name}) with UUID $entityUUID")


            
            // Get all reward configurations
            val rewardConfigs = Main.rewardsFromConfig
            if (rewardConfigs == null || rewardConfigs.isEmpty()) {
                LoggingService.debug("No reward configurations found")
                return
            }

            val currentTime = System.currentTimeMillis()
            val lastProcessTime = Main.lastRewardProcessTime
            val throttleInterval = Main.getInstance().config.getLong("ThrottleInterval", 0)
            
            if (throttleInterval > 0 && (currentTime - lastProcessTime) < throttleInterval) {
                LoggingService.debug("Throttling reward processing due to high frequency mob deaths")
                return
            }
            
            Main.lastRewardProcessTime = currentTime
            
            LoggingService.debug("Checking ${rewardConfigs.size} reward configurations")
            
            // Check if entity has damage tracking
            if (!Main.damageMap.containsKey(entityUUID)) {
                LoggingService.debug("Entity $entityUUID not found in damage map - no rewards will be given")
                return
            }
            
            // Look for a matching reward configuration for this entity
            var matchingReward: RewardMob? = null
            for (reward in rewardConfigs.values) {
                // Check entity type
                if (!reward.doesEntityTypeMatch(entity.type, entity)) {
                    continue
                }
                
                // Check entity name pattern (if configured)
                if (!reward.doesEntityNameMatch(entity.name)) {
                    continue
                }
                
                // Check location requirements (world/region)
                if (!LocationService.isValidLocation(entity.location, reward)) {
                    LoggingService.debug("Entity location doesn't match world/region requirements for reward ${reward.id}")
                    continue
                }
                
                // Found a match
                matchingReward = reward
                LoggingService.info("Found matching reward configuration: ${reward.id}")
                break
            }
            
            // If no matching reward found, log and return
            if (matchingReward == null) {
                LoggingService.debug("No matching reward configuration found for entity ${entity.type.name} (${entity.name})")
                return
            }
            
            // Get damage map for this entity
            val damageMap = Main.damageMap[entityUUID]
            if (damageMap == null || damageMap.isEmpty()) {
                LoggingService.debug("No damage map found for entity $entityUUID for reward ${matchingReward.id}")
                return
            }
            
            // Send a custom event
            val customEvent = RewardSystemMobDieEvent(entity, matchingReward, Main.lastToucherMap[entityUUID])
            Bukkit.getPluginManager().callEvent(customEvent)
            
            // Check if the event was cancelled
            if (customEvent.isCancelled) {
                LoggingService.debug("RewardSystemMobDieEvent was cancelled by another plugin")
                return
            }
            
            // Process rewards using the RewardService
            processRewards(matchingReward, entity, damageMap)

            // Clear tracking for this entity
            clearEntityTracking(entityUUID)
        } catch (e: Exception) {
            LoggingService.severe("Error processing entity death: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Handles player quit events to clean up tracking data.
     * 
     * @param event The player quit event
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        try {
            val playerName = event.player.name
            
            // Clean up damage tracking when player leaves
            Main.damageMap.values.forEach { damageMap ->
                damageMap.remove(playerName)
            }
        } catch (e: Exception) {
            LoggingService.severe("Error in player quit handler: ${e.message}")
        }
    }
    
    /**
     * Process rewards for an entity.
     *
     * @param reward The matching reward configuration
     * @param entity The entity that died
     * @param damageMap Map of player names to damage dealt
     */
    private fun processRewards(reward: RewardMob, entity: LivingEntity, damageMap: HashMap<String, Double>) {
        LoggingService.info("Processing rewards for entity ${entity.type.name} (${entity.name}) with ID ${reward.id}")
        LoggingService.debug("Damage map contains ${damageMap.size} players: ${damageMap.entries.joinToString { "${it.key}=${it.value}" }}")
        
        // Skip if damage map is empty
        if (damageMap.isEmpty()) {
            LoggingService.debug("Skipping rewards - damage map is empty")
            return
        }
        
        // Calculate total damage
        val totalDamage = damageMap.values.sum()
        LoggingService.debug("Total damage: $totalDamage, Minimum required: ${reward.minimumDamage}")
        
        // Get valid players for rewards
        val validPlayers = rewardService.getValidPlayersForReward(damageMap, reward, totalDamage)
        LoggingService.debug("Valid players for rewards: ${validPlayers.size} (${validPlayers.joinToString { player -> player.name }})")
        
        // Skip if no valid players
        if (validPlayers.isEmpty()) {
            LoggingService.debug("Skipping rewards - no valid players")
            return
        }
        
        // Send a custom event
        val customEvent = RewardSystemMobDieEvent(entity, reward, Main.lastToucherMap[entity.uniqueId])
        Bukkit.getPluginManager().callEvent(customEvent)
        LoggingService.debug("RewardSystemMobDieEvent fired for reward ${reward.id}")
        
        // Distribute rewards to valid players
        rewardService.distributeRewards(validPlayers, entity, reward, damageMap, totalDamage)
        LoggingService.debug("Rewards distributed successfully")
    }

    /**
     * Debug event for OP players to see entity information on hit.
     */
    @EventHandler
    fun debug(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        val damager = event.damager
        
        if (damager is Player && damager.isOp && LoggingService.isDebugEnabled()) {
            val world = entity.location.world?.name ?: "unknown"
            val type = entity.type.name
            val name = entity.name
            
            damager.sendMessage("§a§l-------[REWARD SYSTEM DEBUG MESSAGE]-------")
            damager.sendMessage(ChatColor.AQUA.toString() + "if you wont see this message, you should set Debug.enabled: false in config.yml")
            damager.sendMessage(ChatColor.AQUA.toString() + " ")
            damager.sendMessage(ChatColor.BLUE.toString() + "Mob world: " + world)
            damager.sendMessage(ChatColor.BLUE.toString() + "Mob type: " + type)
            damager.sendMessage(ChatColor.BLUE.toString() + "Mob name: " + name)
            
            if (entity is LivingEntity) {
                damager.sendMessage(ChatColor.RED.toString() + "Custom name: " + (entity.customName ?: "none"))
                damager.sendMessage(ChatColor.RED.toString() + "Is custom name visible: " + entity.isCustomNameVisible)
            }
            
            damager.sendMessage("§a§l------------------------------------------")
            
            LoggingService.info("OP player ${damager.name} hit entity - Type: $type, Name: $name, World: $world")
        }
    }

    /**
     * Helper method to dispatch commands with logging.
     */
    fun dispatchCommandWithLogging(sender: CommandSender, command: String) {
        // Command safety validation disabled per user request
        /*
        // Validate command before execution
        if (!isCommandSafe(command)) {
            LoggingService.severe("Potentially unsafe command blocked: $command")
            return
        }
        */
        
        LoggingService.debug("Plugin dispatched command: $command")
        Bukkit.dispatchCommand(sender, command)
    }
    
    /**
     * Checks if a command is safe to execute.
     * Only blocks extremely dangerous commands.
     * 
     * @param command The command to check
     * @return true if command is safe, false otherwise
     */
    private fun isCommandSafe(command: String): Boolean {
        // Simply return true to allow all commands
        return true
    }

    /**
     * Clears tracking data for an entity.
     *
     * @param entityUUID The UUID of the entity
     */
    private fun clearEntityTracking(entityUUID: UUID) {
        Main.damageMap.remove(entityUUID)
        
        // Handle the UUID map with explicit type specification
        Main.rewardsFromConfig?.forEach { (id, reward) ->
            Main.uuidMap[id]?.remove(entityUUID)
        }
        
        Main.lastToucherMap.remove(entityUUID)
    }
}