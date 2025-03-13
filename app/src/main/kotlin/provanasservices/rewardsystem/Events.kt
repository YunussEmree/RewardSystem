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
            LoggingService.info("Entity death event: ${entity.type.name} (${entity.name}) with UUID $entityUUID")
            
            // Get all reward configurations
            val rewardConfigs = Main.rewardsFromConfig
            if (rewardConfigs == null) {
                LoggingService.info("No reward configurations found")
                return
            }
            
            // 3. Performance optimization: Throttling for high frequency mob deaths
            val currentTime = System.currentTimeMillis()
            val lastProcessTime = Main.lastRewardProcessTime
            val throttleInterval = Main.getInstance().config.getLong("ThrottleInterval", 0)
            
            if (throttleInterval > 0 && (currentTime - lastProcessTime) < throttleInterval) {
                LoggingService.debug("Throttling reward processing due to high frequency mob deaths")
                return
            }
            
            Main.lastRewardProcessTime = currentTime
            
            LoggingService.info("Checking ${rewardConfigs.size} reward configurations")
            
            // Check if entity is in the damage map
            if (!Main.damageMap.containsKey(entityUUID)) {
                LoggingService.info("Entity $entityUUID not found in damage map - no rewards will be given")
            }
            
            // Process rewards for each applicable reward configuration
            var matchFound = false
            rewardConfigs.values.forEach { reward ->
                // Skip if entity type doesn't match (only if type is specified)
                if (reward.type != null && !reward.type!!.isEmpty() && !reward.typeEquals(entity.type.name)) {
                    LoggingService.debug("Entity type ${entity.type.name} doesn't match reward ${reward.id}")
                    return@forEach
                }
                
                // Name check if configured
                if (reward.name != null && !reward.name!!.isEmpty() && !reward.nameEquals(entity.name)) {
                    LoggingService.debug("Entity name '${entity.name}' doesn't match reward ${reward.id} name: ${reward.name}")
                    return@forEach
                }
                
                matchFound = true
                LoggingService.info("Found matching reward configuration: ${reward.id}")
                
                // Skip if entity's location isn't in a valid region or world
                if (!LocationService.isValidLocation(entity.location, reward)) {
                    LoggingService.info("Entity location doesn't match world/region requirements for reward ${reward.id}")
                    return@forEach
                }
                
                // Get damage map for this entity
                val entityDamageMap = Main.damageMap[entityUUID]
                if (entityDamageMap == null) {
                    LoggingService.info("No damage map found for entity $entityUUID for reward ${reward.id}")
                    return@forEach
                }
                
                // Process rewards for this entity and reward configuration
                processRewards(entity, entityDamageMap, reward)
                
                // Clean up tracking data
                Main.damageMap.remove(entityUUID)
                Main.uuidMap[reward.id]?.remove(entityUUID)
                Main.lastToucherMap.remove(entityUUID)
            }
            
            if (!matchFound) {
                LoggingService.info("No matching reward configuration found for entity ${entity.type.name} (${entity.name})")
            }
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
     * Processes rewards for a killed entity.
     * 
     * @param entity The entity that died
     * @param damageMap Map of player names to damage amounts
     * @param reward The reward configuration to process
     */
    private fun processRewards(entity: LivingEntity, damageMap: HashMap<String, Double>, reward: RewardMob) {
        // Enhanced debugging
        LoggingService.info("Processing rewards for entity ${entity.type.name} (${entity.name}) with ID ${reward.id}")
        LoggingService.info("Damage map contains ${damageMap.size} players: ${damageMap.entries.joinToString { "${it.key}=${it.value}" }}")
        
        // Skip if no players dealt damage
        if (damageMap.isEmpty()) {
            LoggingService.info("Skipping rewards - damage map is empty")
            return
        }
        
        // Prepare for reward distribution
        val totalDamage = damageMap.values.sum()
        LoggingService.info("Total damage: $totalDamage, Minimum required: ${reward.minimumDamage}")
        
        val validPlayers = rewardService.getValidPlayersForReward(damageMap, reward, totalDamage)
        LoggingService.info("Valid players for rewards: ${validPlayers.size} (${validPlayers.joinToString { it.name }})")
        
        // Skip if no valid players
        if (validPlayers.isEmpty()) {
            LoggingService.info("Skipping rewards - no valid players")
            return
        }
        
        // Fire custom event
        val customEvent = RewardSystemMobDieEvent(damageMap, reward.id)
        Bukkit.getPluginManager().callEvent(customEvent)
        LoggingService.info("RewardSystemMobDieEvent fired for reward ${reward.id}")
        
        try {
            // Distribute rewards
            rewardService.distributeRewards(validPlayers, entity, reward, damageMap, totalDamage)
            LoggingService.info("Rewards distributed successfully")
        } catch (e: Exception) {
            LoggingService.severe("Error distributing rewards: ${e.message}")
            e.printStackTrace()
        }
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
            damager.sendMessage(ChatColor.AQUA.toString() + "if you wont see this message, you should set debug: false in config.yml")
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
}