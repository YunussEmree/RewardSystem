package provanasservices.rewardsystem.util

import provanasservices.rewardsystem.service.LoggingService

/**
 * Utility for more flexible logging that works both in Minecraft environment
 * and in standalone testing scenarios.
 * 
 * This class provides methods for logging that will:
 * - Use LoggingService in the Minecraft environment
 * - Fallback to println in standalone testing environments
 */
object LoggingUtility {
    
    // Whether we're running in the Minecraft environment
    private var isMinecraftEnvironment = false
    
    // Whether debug logging is enabled
    private var debugEnabled = false
    
    /**
     * Initialize the logging utility
     * 
     * @param inMinecraftEnv Whether we're running in the Minecraft environment
     * @param enableDebug Whether to enable debug logging
     */
    fun initialize(inMinecraftEnv: Boolean, enableDebug: Boolean = false) {
        isMinecraftEnvironment = inMinecraftEnv
        debugEnabled = enableDebug
    }
    
    /**
     * Log a debug message.
     * When in Minecraft environment, this will use LoggingService.debug
     * Otherwise, it will print to stdout with a [DEBUG] prefix
     * 
     * @param message The message to log
     * @param source The source of the log (for identification)
     */
    fun debug(message: String, source: String? = null) {
        val formattedMessage = if (source != null) "[$source] $message" else message
        
        if (isMinecraftEnvironment) {
            // In Minecraft environment, use LoggingService
            LoggingService.debug(formattedMessage)
        } else if (debugEnabled) {
            // In standalone environment, use println if debug is enabled
            println("[DEBUG] $formattedMessage")
        }
    }
    
    /**
     * Log an info message.
     * When in Minecraft environment, this will use LoggingService.info
     * Otherwise, it will print to stdout with an [INFO] prefix
     * 
     * @param message The message to log
     * @param source The source of the log (for identification)
     */
    fun info(message: String, source: String? = null) {
        val formattedMessage = if (source != null) "[$source] $message" else message
        
        if (isMinecraftEnvironment) {
            // In Minecraft environment, use LoggingService
            LoggingService.info(formattedMessage)
        } else {
            // In standalone environment, use println
            println("[INFO] $formattedMessage")
        }
    }
    
    /**
     * Log a warning message.
     * When in Minecraft environment, this will use LoggingService.warning
     * Otherwise, it will print to stdout with a [WARNING] prefix
     * 
     * @param message The message to log
     * @param source The source of the log (for identification)
     */
    fun warning(message: String, source: String? = null) {
        val formattedMessage = if (source != null) "[$source] $message" else message
        
        if (isMinecraftEnvironment) {
            // In Minecraft environment, use LoggingService
            LoggingService.warning(formattedMessage)
        } else {
            // In standalone environment, use println
            println("[WARNING] $formattedMessage")
        }
    }
    
    /**
     * Log an error message.
     * When in Minecraft environment, this will use LoggingService.severe
     * Otherwise, it will print to stdout with an [ERROR] prefix
     * 
     * @param message The message to log
     * @param source The source of the log (for identification)
     */
    fun error(message: String, source: String? = null) {
        val formattedMessage = if (source != null) "[$source] $message" else message
        
        if (isMinecraftEnvironment) {
            // In Minecraft environment, use LoggingService
            LoggingService.severe(formattedMessage)
        } else {
            // In standalone environment, use println
            println("[ERROR] $formattedMessage")
        }
    }
} 