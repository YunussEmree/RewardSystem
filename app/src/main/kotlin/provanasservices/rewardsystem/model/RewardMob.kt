package provanasservices.rewardsystem.model

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.service.LoggingService

/**
 * Represents a mob configuration that triggers rewards when killed.
 * Contains all the settings and conditions for a reward to be given.
 */
class RewardMob {
    /** Unique identifier for this reward configuration */
    var id: String = ""
    
    /** Radius for broadcasting reward messages (-1 for server-wide) */
    var radius = -1
    
    /** Messages to display when this mob is killed */
    var rewardMessages: List<String>? = null
    
    /** Name of the mob to match (null means match any name) */
    var name: String? = null
    
    /** Type of the mob to match (null means match any type) */
    var type: String? = null
    
    /** Case-sensitive name matching */
    var caseSensitiveName = false
    
    /** Case-sensitive type matching */
    var caseSensitiveType = false
    
    /** World where this reward is active (null means any world) */
    var enabledWorld: String? = null
    
    /** WorldGuard region where this reward is active (null means any region) */
    var enabledRegion: String? = null
    
    /** Commands to execute for all players who damaged the mob */
    var allRewards: List<String>? = null
    
    /** Position-specific rewards (position -> command list) */
    var rewards = HashMap<Int, List<String>>()
    
    /** Position-specific chance rewards (position -> list of chance rewards) */
    val chanceRewards = HashMap<Int, List<ChanceReward>>()
    
    /** Chance-based rewards for all players who damaged the mob */
    var allChanceRewards = ArrayList<ChanceReward>()
    
    /** Minimum damage required to receive rewards */
    var minimumDamage = 0.0
    
    /** Minimum damage as percentage of total damage (0-100) */
    var minimumDamagePercent = 0.0
    
    /** Rewards given to the player who dealt the final hit */
    var lastHitRewards: List<String>? = null
    
    /** Cooldown in seconds before player can get rewards again from this mob */
    var cooldown = 0
    
    /** Cooldown type (SECONDS, MINUTES, HOURS, DAYS) */
    var cooldownType = CooldownType.SECONDS
    
    /** Player cooldown data (Player UUID -> Expiration timestamp) */
    var cooldowns: MutableMap<UUID, Long> = Collections.synchronizedMap(HashMap())
    
    /** Message to display when player is on cooldown */
    var cooldownMessage = ""
    
    /**
     * Checks if a mob's name matches this reward's configured name.
     * Handles color code stripping and case-insensitive comparison.
     * 
     * @param name The name to check against
     * @return true if name matches or if no name is configured
     */
    fun nameEquals(name: String): Boolean {
        // Log comparison detail if debug is enabled
        LoggingService.debug("Name comparison for ID $id - Input: '$name', Config: '${this.name}'")
        
        // If no name is configured, any name matches
        if (this.name == null || this.name!!.isEmpty()) {
            LoggingService.debug("Name check skipped - config name is null or empty")
            return true
        }
        
        // Clean names for consistent comparison
        val cleanInputName = cleanupName(name)
        val cleanConfigName = cleanupName(this.name!!)
        
        LoggingService.debug("Cleaned names - Input: '$cleanInputName', Config: '$cleanConfigName'")
        
        // Perform comparison based on case sensitivity setting
        val result = if (caseSensitiveName) {
            cleanInputName == cleanConfigName
        } else {
            cleanInputName.equals(cleanConfigName, ignoreCase = true)
        }
        
        LoggingService.debug("Name comparison result: $result (case-sensitive: $caseSensitiveName)")
        return result
    }
    
    /**
     * Checks if a mob's type matches this reward's configured type.
     * 
     * @param type The entity type name to check
     * @return true if type matches or if no type is configured
     */
    fun typeEquals(type: String): Boolean {
        LoggingService.debug("Type comparison - Input: '$type', Config: '${this.type}'")
        
        // If no type is configured, any type matches
        if (this.type == null || this.type!!.isEmpty()) {
            LoggingService.debug("Type check skipped - config type is null or empty")
            return true
        }
        
        // Perform comparison based on case sensitivity setting
        val result = if (caseSensitiveType) {
            this.type == type
        } else {
            this.type.equals(type, ignoreCase = true)
        }
        
        LoggingService.debug("Type check result: $result (case-sensitive: $caseSensitiveType)")
        return result
    }
    
    /**
     * Checks if a world name matches this reward's configured world.
     * 
     * @param world The world name to check
     * @return true if world matches or if no world is configured
     */
    fun worldEquals(world: String): Boolean {
        val plugin = Main.getInstance()
        if (plugin.config.getBoolean("Debug.enabled")) {
            LoggingService.debug("World comparison - Input: '$world', Config: '${this.enabledWorld}'")
        }
        
        return if (enabledWorld == null || enabledWorld!!.isEmpty()) true else enabledWorld.equals(world, ignoreCase = true)
    }
    
    /**
     * Checks if an entity's type matches this reward's configured type.
     * 
     * @param entityType The entity type to check
     * @param entity The entity itself (for additional type checks if needed)
     * @return true if type matches or if no type is configured
     */
    fun doesEntityTypeMatch(entityType: org.bukkit.entity.EntityType, entity: org.bukkit.entity.Entity): Boolean {
        if (this.type == null || this.type!!.isEmpty()) {
            LoggingService.debug("Type check skipped - config type is null or empty")
            return true
        }
        
        val entityTypeName = entityType.name
        LoggingService.debug("Entity type check - Entity type: $entityTypeName, Config type: ${this.type}")
        
        return typeEquals(entityTypeName)
    }
    
    /**
     * Checks if an entity's name matches this reward's configured name pattern.
     * 
     * @param entityName The entity name to check
     * @return true if name matches or if no name is configured
     */
    fun doesEntityNameMatch(entityName: String): Boolean {
        LoggingService.debug("Entity name check - Entity name: $entityName, Config name: ${this.name}")
        
        if (this.name == null || this.name!!.isEmpty()) {
            LoggingService.debug("Name check skipped - config name is null or empty")
            return true
        }
        
        return nameEquals(entityName)
    }
    
    /**
     * Helper method to clean up mob names for consistent comparison.
     * - Translates color codes
     * - Strips color codes
     * - Normalizes spaces
     * 
     * @param name The name to clean
     * @return Cleaned name string
     */
    private fun cleanupName(name: String?): String {
        if (name == null) return ""
        
        // Convert color codes
        var result = ChatColor.translateAlternateColorCodes('&', name)
        
        // Strip color codes
        result = ChatColor.stripColor(result)
        
        // Normalize whitespace
        result = result.trim().replace("\\s+".toRegex(), " ")
        
        return result
    }
    
    /**
     * Data class representing a chance-based reward command.
     * 
     * @property chance Fixed chance percentage (0-100) or null if using placeholder
     * @property commands Command to execute if chance check succeeds
     * @property chancePlaceholder Placeholder to evaluate for chance value
     */
    data class ChanceReward(
        val chance: Double?, 
        val commands: String, 
        val chancePlaceholder: String? = null
    )
    
    /**
     * Cooldown type enum for different time units
     */
    enum class CooldownType {
        SECONDS, MINUTES, HOURS, DAYS;
        
        /**
         * Converts a cooldown value to milliseconds based on the type
         */
        fun toMillis(value: Int): Long {
            return when (this) {
                SECONDS -> value * 1000L
                MINUTES -> value * 60 * 1000L
                HOURS -> value * 60 * 60 * 1000L
                DAYS -> value * 24 * 60 * 60 * 1000L
            }
        }
        
        /**
         * Formats remaining time in a human-readable format
         */
        fun formatRemaining(remainingMillis: Long): String {
            return when (this) {
                SECONDS -> "${remainingMillis / 1000} seconds"
                MINUTES -> "${remainingMillis / (60 * 1000)} minutes"
                HOURS -> "${remainingMillis / (60 * 60 * 1000)} hours"
                DAYS -> "${remainingMillis / (24 * 60 * 60 * 1000)} days"
            }
        }
    }
} 