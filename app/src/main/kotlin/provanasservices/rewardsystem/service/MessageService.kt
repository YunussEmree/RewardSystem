package provanasservices.rewardsystem.service

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import provanasservices.rewardsystem.model.RewardMob
import provanasservices.rewardsystem.util.ColorUtils
import java.util.*

/**
 * Service for handling message formatting and distribution.
 * Centralizes message-related functionality like placeholder replacement and sending.
 */
object MessageService {
    
    /**
     * Sends reward messages to players when a mob is killed.
     * 
     * @param entity Entity that died
     * @param reward Reward configuration
     * @param damageMap Map of player names to damage values
     * @param finalDamager Name of player who dealt the final blow
     */
    fun sendRewardMessages(entity: Entity, reward: RewardMob, damageMap: HashMap<String, Double>, finalDamager: String) {
        if (damageMap.isEmpty()) return
        
        val noOne = Bukkit.getPluginManager().getPlugin("RewardSystem")?.config?.getString("no_one") ?: "No one"
        
        // Message to all players or nearby players based on radius
        if (reward.radius == -1) {
            sendMessageToAllPlayers(reward, damageMap, finalDamager, noOne)
        } else {
            sendMessageToNearbyPlayers(entity, reward, damageMap, finalDamager, noOne)
        }
    }
    
    /**
     * Sends reward messages to all online players.
     */
    private fun sendMessageToAllPlayers(
        reward: RewardMob, 
        damageMap: HashMap<String, Double>, 
        finalDamager: String,
        noOne: String
    ) {
        reward.rewardMessages?.forEach { message ->
            Bukkit.getOnlinePlayers().forEach { player ->
                player.sendMessage(
                    ColorUtils.translateColors(
                        replacePlaceholders(message, player.name, damageMap, finalDamager, noOne)
                    )
                )
            }
        }
    }
    
    /**
     * Sends reward messages to players near the entity.
     */
    private fun sendMessageToNearbyPlayers(
        entity: Entity, 
        reward: RewardMob, 
        damageMap: HashMap<String, Double>, 
        finalDamager: String,
        noOne: String
    ) {
        val radius = reward.radius.toDouble()
        
        // Get nearby players using LocationService
        val nearbyPlayers = LocationService.getNearbyPlayers(entity, radius)
        
        if (nearbyPlayers.isNotEmpty()) {
            reward.rewardMessages?.forEach { message ->
                nearbyPlayers.forEach { player ->
                    player.sendMessage(
                        ColorUtils.translateColors(
                            replacePlaceholders(message, player.name, damageMap, finalDamager, noOne)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Replaces placeholders in messages.
     * 
     * @param message Original message
     * @param playerName Player name
     * @param damageMap Damage map
     * @param finalDamager Last damager
     * @param noOne Message to show when no one is present
     * @return Processed message
     */
    fun replacePlaceholders(
        message: String,
        playerName: String,
        damageMap: HashMap<String, Double>,
        finalDamager: String,
        noOne: String
    ): String {
        val sortedEntries = damageMap.entries.sortedByDescending { it.value }
        var result = message
        
        // Replace %top_name_X% placeholders
        val namePattern = "%top_name_(\\d+)%".toRegex()
        for (match in namePattern.findAll(result)) {
            val index = match.groupValues[1].toInt() - 1
            if (index < sortedEntries.size) {
                result = result.replace(match.value, sortedEntries[index].key)
            } else {
                result = result.replace(match.value, noOne)
            }
        }
        
        // Replace %top_damage_X% placeholders
        val damagePattern = "%top_damage_(\\d+)%".toRegex()
        for (match in damagePattern.findAll(result)) {
            val index = match.groupValues[1].toInt() - 1
            if (index < sortedEntries.size) {
                result = result.replace(match.value, sortedEntries[index].value.toInt().toString())
            } else {
                result = result.replace(match.value, "0")
            }
        }
        
        // Replace other placeholders
        result = result.replace("%personal_damage%", (damageMap[playerName]?.toInt() ?: 0).toString())
        result = result.replace("%final_damager%", finalDamager)
        
        return result
    }
    
    /**
     * Formats and sends a cooldown message to a player.
     * 
     * @param player The player to send the message to
     * @param message The cooldown message template
     * @param remainingTime The remaining cooldown time in seconds
     */
    fun sendCooldownMessage(player: Player, message: String, remainingTime: Long) {
        player.sendMessage(
            ColorUtils.translateColors(message.replace("%time%", remainingTime.toString()))
        )
    }
    
    /**
     * Formats a command with player and damage placeholders.
     * 
     * @param command The command with placeholders
     * @param playerName The player's name
     * @param damage The damage amount
     * @return The formatted command
     */
    fun formatCommand(command: String, playerName: String, damage: Double): String {
        return command
            .replace("%player%", playerName)
            .replace("%damage%", damage.toString())
    }
} 