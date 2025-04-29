package provanasservices.rewardsystem.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import provanasservices.rewardsystem.Main
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Service class that manages all operations related to placeholders.
 * Provides a fallback mechanism when PlaceholderAPI is not available.
 */
object PlaceholderService {
    private val percentPlaceholderPattern = Pattern.compile("%([^%]+)%")
    private val bracketPlaceholderPattern = Pattern.compile("\\{([^}]+)\\}")
    
    /**
     * Safely processes normal placeholders (in %placeholder% format).
     * 
     * @param player Player context to be processed
     * @param text Text to be processed
     * @return Processed text with placeholders replaced
     */
    fun setPlaceholders(player: Player, text: String): String {
        if (text.isEmpty()) return text
        
        try {
            // Just use our own implementation instead of trying to use PlaceholderAPI
            return processPlaceholders(player, text, percentPlaceholderPattern)
        } catch (e: Exception) {
            // Log error and return with basic placeholders
            LoggingService.warning("Error processing placeholders: ${e.message}")
            return processBasicPlaceholders(player, text)
        }
    }
    
    /**
     * Safely processes bracket placeholders (in {placeholder} format).
     * 
     * @param player Player context to be processed
     * @param text Text to be processed
     * @return Processed text with placeholders replaced
     */
    fun setBracketPlaceholders(player: Player, text: String): String {
        if (text.isEmpty()) return text
        
        try {
            // Just use our own implementation instead of trying to use PlaceholderAPI
            return processPlaceholders(player, text, bracketPlaceholderPattern)
        } catch (e: Exception) {
            LoggingService.warning("Error processing bracket placeholders: ${e.message}")
            return processBasicPlaceholders(player, text)
        }
    }
    
    /**
     * Processes placeholders using the specified pattern.
     * 
     * @param player Player context for placeholders
     * @param text Text to process
     * @param pattern Regex pattern for finding placeholders
     * @return Text with placeholders replaced
     */
    private fun processPlaceholders(player: Player, text: String, pattern: Pattern): String {
        var result = text
        val matcher: Matcher = pattern.matcher(text)
        val replacements = mutableMapOf<String, String>()
        
        // Find all placeholders that need replacement
        while (matcher.find()) {
            val placeholder = matcher.group(0) // The full placeholder
            val identifier = matcher.group(1) // The placeholder without delimiters
            
            if (!replacements.containsKey(placeholder)) {
                // Get the replacement value for this placeholder
                val replacement = getPlaceholderReplacement(player, identifier)
                replacements[placeholder] = replacement
            }
        }
        
        // Apply all replacements at once
        for ((placeholder, replacement) in replacements) {
            result = result.replace(placeholder, replacement)
        }
        
        return result
    }
    
    /**
     * Gets the replacement value for a placeholder identifier.
     * 
     * @param player Player context for the placeholder
     * @param identifier The placeholder identifier without delimiters
     * @return The replacement value for the placeholder
     */
    private fun getPlaceholderReplacement(player: Player, identifier: String): String {
        // Handle player-related placeholders
        when {
            identifier.equals("player", ignoreCase = true) -> return player.name
            identifier.equals("player_name", ignoreCase = true) -> return player.name
            identifier.equals("player_displayname", ignoreCase = true) -> return player.displayName
            identifier.equals("player_uuid", ignoreCase = true) -> return player.uniqueId.toString()
            identifier.equals("player_level", ignoreCase = true) -> return player.level.toString()
            identifier.equals("player_health", ignoreCase = true) -> return player.health.toString()
            identifier.equals("player_max_health", ignoreCase = true) -> return player.maxHealth.toString()
            identifier.equals("player_food", ignoreCase = true) -> return player.foodLevel.toString()
            identifier.equals("player_gamemode", ignoreCase = true) -> return player.gameMode.name
            identifier.equals("player_world", ignoreCase = true) -> return player.world.name
            identifier.equals("player_x", ignoreCase = true) -> return player.location.x.toInt().toString()
            identifier.equals("player_y", ignoreCase = true) -> return player.location.y.toInt().toString()
            identifier.equals("player_z", ignoreCase = true) -> return player.location.z.toInt().toString()
            identifier.equals("player_biome", ignoreCase = true) -> return player.location.block.biome.name
            
            // Server-related placeholders
            identifier.equals("server_name", ignoreCase = true) -> return Bukkit.getServer().name
            identifier.equals("server_online", ignoreCase = true) -> return Bukkit.getOnlinePlayers().size.toString()
            identifier.equals("server_max_players", ignoreCase = true) -> return Bukkit.getMaxPlayers().toString()
            identifier.equals("server_version", ignoreCase = true) -> return Bukkit.getVersion()
            
            // Add more placeholders as needed for your plugin
            
            // Check if we have special reward placeholders
            identifier.startsWith("damage", ignoreCase = true) -> {
                // Try to get damage from the plugin's context
                return getDamageValue(player).toString()
            }
            
            identifier.startsWith("top_name_", ignoreCase = true) -> {
                val position = identifier.substring(9).toIntOrNull() ?: 0
                return getTopPlayerName(position)
            }
            
            identifier.startsWith("top_damage_", ignoreCase = true) -> {
                val position = identifier.substring(11).toIntOrNull() ?: 0
                return getTopPlayerDamage(position).toString()
            }
            
            identifier.equals("killer", ignoreCase = true) -> return getKillerName()
            
            identifier.equals("personal_damage", ignoreCase = true) -> return getDamageValue(player).toString()
            
            // Fallback for unknown placeholders
            else -> return ""
        }
    }
    
    /**
     * Processes basic built-in placeholders.
     * This handles common placeholders used in the plugin.
     *
     * @param player Player context for placeholders
     * @param text Text to process
     * @return Text with basic placeholders replaced
     */
    private fun processBasicPlaceholders(player: Player, text: String): String {
        if (text.isEmpty()) return text
        
        return text
            .replace("%player%", player.name)
            .replace("%player_name%", player.name)
            .replace("%player_level%", player.level.toString())
            .replace("%player_health%", player.health.toString())
            .replace("%player_max_health%", player.maxHealth.toString())
            .replace("%player_food%", player.foodLevel.toString())
            .replace("%player_world%", player.world.name)
            .replace("%server_online%", Bukkit.getOnlinePlayers().size.toString())
            .replace("%player_x%", player.location.blockX.toString())
            .replace("%player_y%", player.location.blockY.toString())
            .replace("%player_z%", player.location.blockZ.toString())
            // Add any other basic placeholders your plugin frequently uses
    }
    
    /**
     * Helper method to get damage value for a player.
     * This is a placeholder - you should implement the actual logic.
     */
    private fun getDamageValue(player: Player): Int {
        // This should be replaced with your actual damage tracking logic
        // For now, just return a placeholder value
        return 10
    }
    
    /**
     * Helper method to get top player name at a position.
     * This is a placeholder - you should implement the actual logic.
     */
    private fun getTopPlayerName(position: Int): String {
        // This should be replaced with your actual top player tracking logic
        return when (position) {
            1 -> Main.getInstance().config.getString("no_one", "No one") ?: "No one"
            else -> Main.getInstance().config.getString("no_one", "No one") ?: "No one"
        }
    }
    
    /**
     * Helper method to get top player damage at a position.
     * This is a placeholder - you should implement the actual logic.
     */
    private fun getTopPlayerDamage(position: Int): Int {
        // This should be replaced with your actual top damage tracking logic
        return 0
    }
    
    /**
     * Helper method to get the killer name.
     * This is a placeholder - you should implement the actual logic.
     */
    private fun getKillerName(): String {
        // This should be replaced with your actual killer tracking logic
        return Main.getInstance().config.getString("no_one", "No one") ?: "No one"
    }
} 