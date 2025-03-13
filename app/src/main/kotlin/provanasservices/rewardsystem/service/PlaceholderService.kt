package provanasservices.rewardsystem.service

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import provanasservices.rewardsystem.Main

/**
 * Service class that manages all operations related to PlaceholderAPI.
 * Checks the existence and availability of PlaceholderAPI,
 * safely processes placeholders.
 */
object PlaceholderService {
    /**
     * Checks if PlaceholderAPI is installed.
     * 
     * @return true if PlaceholderAPI is installed, false otherwise
     */
    fun isPlaceholderAPIEnabled(): Boolean {
        return try {
            Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        } catch (e: Exception) {
            logError("Error checking PlaceholderAPI status: ${e.message}")
            false
        }
    }
    
    /**
     * Safely processes normal placeholders (in %placeholder% format).
     * 
     * @param player Player context to be processed
     * @param text Text to be processed
     * @return Processed text or original text in case of error
     */
    fun setPlaceholders(player: Player, text: String): String {
        // Skip if PlaceholderAPI is not enabled
        if (!Main.PLACEHOLDERAPI_ENABLED) {
            return text
        }
        
        try {
            // Make sure PlaceholderAPI is available before trying to use it
            val placeholderPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI")
            if (placeholderPlugin == null || !placeholderPlugin.isEnabled) {
                LoggingService.warning("PlaceholderAPI plugin found but it's not enabled or not properly loaded")
                return text
            }
            
            // Use PlaceholderAPI to process placeholders
            val result = PlaceholderAPI.setPlaceholders(player, text)
            return result ?: text  // Return original text if result is null
        } catch (e: Exception) {
            // Log error and return original text
            LoggingService.warning("Error setting placeholders: ${e.message}")
            return text
        }
    }
    
    /**
     * Safely processes bracket placeholders (in {placeholder} format).
     * 
     * @param player Player context to be processed
     * @param text Text to be processed
     * @return Processed text or original text in case of error
     */
    fun setBracketPlaceholders(player: Player, text: String): String {
        if (!Main.PLACEHOLDERAPI_ENABLED) {
            return text
        }
        
        return try {
            // Make sure PlaceholderAPI is available before trying to use it
            val placeholderPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI")
            if (placeholderPlugin == null || !placeholderPlugin.isEnabled) {
                LoggingService.warning("PlaceholderAPI plugin found but it's not enabled or not properly loaded")
                return text
            }
            
            val result = PlaceholderAPI.setBracketPlaceholders(player, text)
            return result ?: text  // Return original text if result is null
        } catch (e: Exception) {
            logError("Error processing bracket placeholders: ${e.message}")
            text
        }
    }
    
    /**
     * Checks if PlaceholderAPI is installed and available.
     *
     * @return true if PlaceholderAPI is available, false otherwise
     */
    fun isPlaceholderAPIAvailable(): Boolean {
        return Main.PLACEHOLDERAPI_ENABLED
    }
    
    /**
     * Logs errors if debug mode is active.
     */
    private fun logError(message: String) {
        if (Bukkit.getPluginManager().getPlugin("RewardSystem")?.config?.getBoolean("Debug.enabled") == true) {
            Bukkit.getLogger().warning("[REWARDSYSTEM WARNING] $message")
        }
    }
} 