package provanasservices.rewardsystem.service

import org.bukkit.entity.Arrow
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import provanasservices.rewardsystem.Main
import java.util.HashMap
import java.util.HashSet
import java.util.UUID

/**
 * Service class for tracking damage dealt to entities.
 * Tracks damage from both direct player attacks and arrows.
 */
class DamageTrackerService(private val plugin: Main) {
    
    /**
     * Tracks damage dealt to an entity by a player or player's projectile.
     * Updates the global damage map with player's damage contribution.
     *
     * @param event The damage event to process
     */
    fun trackDamage(event: EntityDamageByEntityEvent) {

        if (event.isCancelled) {
                LoggingService.debug("Skipping damage tracking - event was cancelled")
                return
            }

        val entity = event.entity
        val damager = event.damager
        val damage = event.finalDamage
        val entityUUID = entity.uniqueId
        
        // Skip if damage is zero or negative
        if (damage <= 0) {
            LoggingService.debug("Skipping damage tracking - damage is zero or negative")
            return
        }
        
        when (damager) {
            // Direct player damage
            is Player -> {
                LoggingService.debug("Player ${damager.name} dealt $damage damage directly to ${entity.type.name} (${entity.uniqueId})")
                recordDamage(entityUUID, damager.name, damage)
                
                // Add entity to UUID map for the relevant rewards
                addEntityToTracking(entity)
            }
            
            // Arrow damage (from player)
            is Arrow -> {
                val shooter = damager.shooter
                if (shooter is Player) {
                    LoggingService.debug("Player ${shooter.name} dealt $damage damage with arrow to ${entity.type.name} (${entity.uniqueId})")
                    recordDamage(entityUUID, shooter.name, damage)
                    
                    // Add entity to UUID map for the relevant rewards
                    addEntityToTracking(entity)
                }
            }
            
            // Log other damage causes for debugging
            else -> {
                LoggingService.debug("Entity damaged by non-player source: ${damager.type.name}")
            }
        }
    }
    
    /**
     * Adds an entity to the tracking maps for all relevant reward configurations.
     *
     * @param entity The entity to track
     */
    private fun addEntityToTracking(entity: Entity) {
        val rewardConfigs = Main.rewardsFromConfig ?: return
        val entityType = entity.type.name
        
        rewardConfigs.values.forEach { reward ->
            // If the reward applies to this entity type
            if (reward.typeEquals(entityType)) {
                // If name is specified, check name
                if (reward.name != null && !reward.nameEquals(entity.name)) {
                    return@forEach
                }
                
                // Get or create the UUID set for this reward
                val uuidSet = Main.uuidMap.getOrPut(reward.id) { HashSet() }
                
                // Add entity to the tracking set
                uuidSet.add(entity.uniqueId)
                LoggingService.debug("Added entity ${entity.type.name} (${entity.uniqueId}) to tracking for reward ${reward.id}")
            }
        }
    }
    
    /**
     * Records damage dealt by a player to an entity.
     * Updates the global damage map with the player's contribution.
     *
     * @param entityUUID The UUID of the damaged entity
     * @param playerName The name of the player who dealt damage
     * @param damage The amount of damage dealt
     */
    private fun recordDamage(entityUUID: UUID, playerName: String, damage: Double) {
        // Update last toucher for this entity
        Main.lastToucherMap[entityUUID] = playerName
        
        // Get or create damage map for this entity
        val entityDamageMap = Main.damageMap.getOrPut(entityUUID) { HashMap() }
        
        // Add damage to player's total
        val currentDamage = entityDamageMap.getOrDefault(playerName, 0.0)
        entityDamageMap[playerName] = currentDamage + damage
        
        LoggingService.debug("Player $playerName dealt $damage damage to entity $entityUUID (total: ${entityDamageMap[playerName]})")
    }
    
    /**
     * Clears damage records for an entity.
     * Used when an entity dies or the tracking data is no longer needed.
     *
     * @param entityUUID The UUID of the entity to clear records for
     */
    fun clearDamageRecords(entityUUID: UUID) {
        Main.damageMap.remove(entityUUID)
        Main.lastToucherMap.remove(entityUUID)
    }
} 