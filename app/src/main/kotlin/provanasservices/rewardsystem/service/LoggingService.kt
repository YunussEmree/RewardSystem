package provanasservices.rewardsystem.service

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import java.util.logging.Logger

import java.util.logging.LogManager

/**
 * Service class for centralized logging throughout the plugin.
 * Manages different log levels and debug output.
 */
object LoggingService {
    private lateinit var logger: Logger
    private lateinit var plugin: JavaPlugin
    private var debugEnabled = false
    private var verboseEnabled = false
    private var logLevel = LogLevel.WARNING // Default log level
    
    /**
     * Enum to represent log levels with numeric values for comparison
     */
    enum class LogLevel(val value: Int) {
        DEBUG(0),
        INFO(1),
        WARNING(2),
        SEVERE(3)
    }
    
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
        debugEnabled   = plugin.config.getBoolean("Debug.enabled", false)
        verboseEnabled = plugin.config.getBoolean("Debug.verbose", false)
        val configLevel = plugin.config.getString("Debug.level", "WARNING")!!.uppercase()
        logLevel = try { LogLevel.valueOf(configLevel) } catch (e: IllegalArgumentException) {
            warning("Invalid log level: $configLevel, defaulting to WARNING.")
            LogLevel.WARNING
        }

        // Java Logger seviyesini hesapla
        val javaLevel = when (logLevel) {
            LogLevel.DEBUG   -> Level.FINE
            LogLevel.INFO    -> Level.INFO
            LogLevel.WARNING -> Level.WARNING
            LogLevel.SEVERE  -> Level.SEVERE
        }
        // Logger’a uygula
        logger.level = javaLevel

        // Root handler’ları da aynı seviyeye getir
        val rootLogger = LogManager.getLogManager().getLogger("")
        rootLogger.level = javaLevel
        rootLogger.handlers.forEach { it.level = javaLevel }

        if (debugEnabled) {
            info("Debug mode enabled${if (verboseEnabled) " (verbose)" else ""} at $logLevel")
        }
    }


    /**
     * Logs an informational message if the current log level allows it.
     *
     * @param message The message to log
     */
    fun info(message: String) {
        if (debugEnabled && logLevel.value <= LogLevel.INFO.value) {
            logger.info(message)
        }
    }
    
    /**
     * Logs a warning message if the current log level allows it.
     *
     * @param message The warning message to log
     */
    fun warning(message: String) {
        if (debugEnabled && logLevel.value <= LogLevel.WARNING.value) {
            logger.warning(message)
        }
    }
    
    /**
     * Logs a severe error message.
     * Severe messages are always logged regardless of level.
     *
     * @param message The error message to log
     */
    fun severe(message: String) {
        logger.severe(message)
    }
    
    /**
     * Logs a debug message if debug mode is enabled and the current log level allows it.
     *
     * @param message The debug message to log
     */
    fun debug(message: String) {
        // Only process debug messages if debug is enabled AND log level includes DEBUG
        if (debugEnabled && logLevel.value <= LogLevel.DEBUG.value) {
                    logger.fine("[DEBUG] $message")

        }
    }
    
    /**
     * Logs a debug warning message if debug mode is enabled and the current log level allows it.
     *
     * @param message The debug warning message to log
     */
    fun debugWarning(message: String) {
        if (debugEnabled && logLevel == LogLevel.DEBUG) {
            logger.log(Level.WARNING, "[DEBUG] $message")
        }
    }
    
    /**
     * Logs a message at the specified level if the current log level allows it.
     *
     * @param level The log level
     * @param message The message to log
     */
    fun log(level: Level, message: String) {
        // If debug is disabled, only log SEVERE messages
        if (!debugEnabled && level != Level.SEVERE) {
            return
        }
        
        // Map Java logging levels to our custom levels
        val shouldLog = when (level) {
            Level.FINE, Level.FINER, Level.FINEST -> logLevel.value <= LogLevel.DEBUG.value
            Level.INFO, Level.CONFIG -> logLevel.value <= LogLevel.INFO.value
            Level.WARNING -> logLevel.value <= LogLevel.WARNING.value
            Level.SEVERE -> true // Always log severe
            else -> true // Default to logging
        }
        
        if (shouldLog) {
            logger.log(level, message)
        }
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
    
    /**
     * Gets the current log level.
     *
     * @return The current log level
     */
    fun getLogLevel(): LogLevel {
        return logLevel
    }
} 