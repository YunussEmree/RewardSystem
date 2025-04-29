package provanasservices.rewardsystem.service

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Service class for centralized logging throughout the plugin.
 * Manages different log levels and debug output.
 */
object LoggingService {
    private lateinit var logger: Logger
    private lateinit var plugin: JavaPlugin
    private var debugEnabled = false
    private var verboseEnabled = false
    
    /**
     * Initializes the logging service with the plugin instance.
     * Should be called during plugin startup.
     *
     * @param pluginInstance The main plugin instance
     */
    fun initialize(pluginInstance: JavaPlugin) {
        plugin = pluginInstance
        logger = plugin.logger
        updateDebugState()
    }
    
    /**
     * Updates the debug state from the configuration.
     * Call this after config reload to reflect changes.
     */
    fun updateDebugState() {
        debugEnabled = plugin.config.getBoolean("Debug.enabled", false)
        verboseEnabled = plugin.config.getBoolean("Debug.verbose", false)
        
        if (debugEnabled) {
            info("${ChatColor.YELLOW}Debug mode enabled${if (verboseEnabled) " (verbose)" else ""}")
        }
    }
    
    /**
     * Logs an informational message.
     *
     * @param message The message to log
     */
    fun info(message: String) {
        logger.info(message)
    }
    
    /**
     * Logs a warning message.
     *
     * @param message The warning message to log
     */
    fun warning(message: String) {
        logger.warning(message)
    }
    
    /**
     * Logs a severe error message.
     *
     * @param message The error message to log
     */
    fun severe(message: String) {
        logger.severe(message)
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     * Always logs if verbose mode is enabled.
     *
     * @param message The debug message to log
     */
    fun debug(message: String) {
        if (debugEnabled) {
            if (verboseEnabled) {
                // In verbose mode, show all debug messages in console
                logger.info("[DEBUG] $message")
            } else {
                // In normal debug mode, only log when explicitly requested
                if (message.startsWith("!")) {
                    logger.info("[DEBUG] ${message.substring(1)}")
                }
            }
        }
    }
    
    /**
     * Logs a debug warning message if debug mode is enabled.
     *
     * @param message The debug warning message to log
     */
    fun debugWarning(message: String) {
        if (debugEnabled) {
            logger.warning("[DEBUG] $message")
        }
    }
    
    /**
     * Logs a message at the specified level.
     *
     * @param level The log level
     * @param message The message to log
     */
    fun log(level: Level, message: String) {
        logger.log(level, message)
    }
    
    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    fun isDebugEnabled(): Boolean {
        return debugEnabled
    }
    
    /**
     * Checks if verbose debug mode is enabled.
     *
     * @return true if verbose debug mode is enabled, false otherwise
     */
    fun isVerboseEnabled(): Boolean {
        return verboseEnabled
    }
} 