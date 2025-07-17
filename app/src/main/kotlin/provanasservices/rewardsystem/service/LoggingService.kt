package provanasservices.rewardsystem.service

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import java.util.logging.LogManager
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
        logLevel = try {
            LogLevel.valueOf(configLevel)
        } catch (e: IllegalArgumentException) {
            warning("Invalid log level in config: $configLevel. Using WARNING level.")
            LogLevel.WARNING
        }

        // Map our LogLevel to java.util.logging.Level
        val javaLevel = when (logLevel) {
            LogLevel.DEBUG   -> Level.FINE
            LogLevel.INFO    -> Level.INFO
            LogLevel.WARNING -> Level.WARNING
            LogLevel.SEVERE  -> Level.SEVERE
        }
        // Apply to plugin logger
        logger.level = javaLevel

        // ALSO apply to root logger and its handlers so FINE messages aren't filtered out
        val rootLogger = LogManager.getLogManager().getLogger("")
        rootLogger.level = javaLevel
        rootLogger.handlers.forEach { it.level = javaLevel }

        if (debugEnabled) {
            info("${ChatColor.YELLOW}Debug mode enabled${if (verboseEnabled) " (verbose)" else ""} with log level: $logLevel")
        }
    }

    /** Logs an informational message if allowed by current level */
    fun info(message: String) {
        if (debugEnabled && logLevel.value <= LogLevel.INFO.value) {
            logger.info(message)
        }
    }

    /** Logs a warning message if allowed by current level */
    fun warning(message: String) {
        if (debugEnabled && logLevel.value <= LogLevel.WARNING.value) {
            logger.warning(message)
        }
    }

    /** Logs a severe error message (always shown) */
    fun severe(message: String) {
        logger.severe(message)
    }

    /**
     * Logs a debug message if debug mode is enabled and level allows.
     * Also prints via println so you see it regardless of handler config.
     */
    fun debug(message: String) {
        // Immediate print for visibility
        println("[REWARD SYSTEM DEBUG] $message")
        if (debugEnabled && logLevel == LogLevel.DEBUG) {
            if (verboseEnabled) {
                logger.log(Level.FINE, "[DEBUG] $message")
            } else if (message.startsWith("!")) {
                logger.log(Level.FINE, "[DEBUG] ${message.substring(1)}")
            }
        }
    }

    /** Logs a debug-level warning message */
    fun debugWarning(message: String) {
        if (debugEnabled && logLevel == LogLevel.DEBUG) {
            logger.log(Level.WARNING, "[DEBUG] $message")
        }
    }

    /**
     * Logs at the specified Java level if allowed by our logLevel.
     * Useful for programmatic logging.
     */
    fun log(level: Level, message: String) {
        if (!debugEnabled && level != Level.SEVERE) return
        val shouldLog = when (level) {
            Level.FINE, Level.FINER, Level.FINEST -> logLevel.value <= LogLevel.DEBUG.value
            Level.INFO, Level.CONFIG              -> logLevel.value <= LogLevel.INFO.value
            Level.WARNING                         -> logLevel.value <= LogLevel.WARNING.value
            Level.SEVERE                          -> true
            else                                  -> true
        }
        if (shouldLog) {
            logger.log(level, message)
        }
    }

    fun isDebugEnabled() = debugEnabled
    fun isVerboseEnabled() = verboseEnabled
    fun getLogLevel()    = logLevel
}
