package provanasservices.rewardsystem.Database

import java.util.*

/**
 * Interface for database operations.
 * Defines methods that must be implemented by all database helpers.
 */
interface DbHelper {
    /**
     * Connects to the database.
     */
    fun connect()
    
    /**
     * Disconnects from the database and releases resources.
     */
    fun disconnect()
    
    /**
     * Imports cooldowns from the database into the cooldown map.
     *
     * @param cooldownsMap The map to populate with cooldown data
     * @param rewardId The ID of the reward
     * @param currentTime The current time in milliseconds
     */
    fun importCooldowns(cooldownsMap: MutableMap<UUID, Long>, rewardId: String, currentTime: Long)
    
    /**
     * Exports cooldowns from the cooldown map to the database.
     *
     * @param cooldownsMap The map containing cooldown data
     * @param rewardId The ID of the reward
     */
    fun exportCooldowns(cooldownsMap: MutableMap<UUID, Long>, rewardId: String)
}