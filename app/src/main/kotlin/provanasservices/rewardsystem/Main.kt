package provanasservices.rewardsystem

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Database.DbHelper
import provanasservices.rewardsystem.Database.MysqlHelper
import provanasservices.rewardsystem.Database.SqliteHelper
import provanasservices.rewardsystem.model.RewardMob
import provanasservices.rewardsystem.service.CommandHandler
import provanasservices.rewardsystem.service.ConfigService
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.util.ColorUtils
import provanasservices.rewardsystem.util.Licence
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * Main plugin class for RewardSystem.
 * Handles plugin initialization, configuration, and lifecycle management.
 */
class Main : JavaPlugin() {
    /** The database helper instance */
    lateinit var dbHelper: DbHelper
    
    /** Thread pool for async database operations */
    private val dbThreadPool: ThreadPoolExecutor = Executors.newFixedThreadPool(2) as ThreadPoolExecutor
    
    override fun onEnable() {
        // Store singleton instance
        instance = this
        
        // Initialize logging service first so all subsequent log calls work
        LoggingService.initialize(this)
        LoggingService.info("${ChatColor.GREEN}RewardSystem initializing...")
        
        try {
            // Verify license before proceeding
            if (!verifyLicense()) {
                return
            }
            
            // Initialize plugin components
            initializePlugin()
            
            LoggingService.info("${ChatColor.GREEN}RewardSystem successfully enabled!")
        } catch (e: Exception) {
            LoggingService.severe("Failed to enable RewardSystem: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }
    
    override fun onDisable() {
        try {
            // Save all cooldowns to database
            saveCooldownsToDatabase()
            
            // Shutdown thread pool
            dbThreadPool.shutdown()
            try {
                if (!dbThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbThreadPool.shutdownNow()
                }
            } catch (e: InterruptedException) {
                dbThreadPool.shutdownNow()
            }
            
            // Close database connections
            if (::dbHelper.isInitialized) {
                dbHelper.disconnect()
            }
            
            LoggingService.info("${ChatColor.RED}RewardSystem disabled")
        } catch (e: Exception) {
            LoggingService.severe("Error during plugin shutdown: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Initializes all plugin components in the correct order.
     */
    private fun initializePlugin() {
        // Load configuration
        loadConfiguration()
        
        // Set up database
        setupDatabase()
        
        // Check for PlaceholderAPI
        setupPlaceholderAPI()
        
        // Register event listeners
        registerEvents()
        
        // Load reward configurations
        rewardsFromConfig = ConfigService.loadRewardsFromConfig(this)
        
        // Load cooldowns from database
        loadCooldownsFromDatabase()
        
        // Register commands
        registerCommands()
    }
    
    /**
     * Verifies that the plugin has a valid license.
     * 
     * @return true if license is valid, false otherwise
     */
    private fun verifyLicense(): Boolean {
        return if (!Licence.parseYAMLAndCheckLicenceCode(this)) {
            LoggingService.severe("PLUGIN LICENCE REJECTED!")
            LoggingService.severe("Contact the plugin developers: Discord 'blestit' or 'metumortis'")
            server.pluginManager.disablePlugin(this)
            false
        } else {
            LoggingService.info("${ChatColor.GREEN}PLUGIN LICENCE ACCEPTED!")
            LoggingService.info("${ChatColor.GREEN}For support, contact: Discord 'blestit' or 'metumortis'")
            true
        }
    }
    
    /**
     * Loads the plugin configuration files.
     */
    private fun loadConfiguration() {
        dataFolder.mkdirs() // Ensure plugin directory exists
        
        // Create default config if it doesn't exist
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            LoggingService.info("Config file doesn't exist, creating default config.yml")
            saveDefaultConfig() // This both creates the file and loads it
            
            // Important: Reload the config to ensure it's properly loaded in memory
            reloadConfig()
            LoggingService.info("Default config created and loaded")
        } else {
            reloadConfig() // Load existing config
            LoggingService.info("Existing config loaded")
        }
        
        // Update logging settings from config
        val debugEnabled = config.getBoolean("Debug.enabled", false)
        val debugLevel = config.getString("Debug.level", "WARNING") ?: "WARNING"
        
        // Force enable debug for chance system debugging
        config.set("Debug.enabled", true)
        config.set("Debug.level", "DEBUG")
        
        LoggingService.info("🔍 DEBUG FORCED ENABLED for chance system debugging")
        LoggingService.updateDebugState()
        
        // Set global minimum damage requirement from config
        minimumDamageRequirement = config.getDouble("MinimumDamageRequirement", 0.0)
        
        LoggingService.info("Configuration loaded")
    }
    
    /**
     * Sets up the database connection based on configuration.
     */
    private fun setupDatabase() {
        val databaseType = config.getString("Database.type", "sqlite")?.lowercase() ?: "sqlite"
        
        dbHelper = when (databaseType) {
            "mysql" -> MysqlHelper(this)
            else -> {
                if (databaseType != "sqlite") {
                    LoggingService.severe("Invalid database type in config.yml: $databaseType")
                    LoggingService.severe("Defaulting to SQLite database")
                }
                SqliteHelper(this)
            }
        }
        
        try {
            dbHelper.connect()
            LoggingService.info("Database connection established: $databaseType")
        } catch (e: Exception) {
            LoggingService.severe("Failed to connect to database: ${e.message}")
            throw e // Re-throw to abort plugin initialization
        }
    }
    
    /**
     * Checks for PlaceholderAPI and initializes integration if available.
     */
    private fun setupPlaceholderAPI() {
        PLACEHOLDERAPI_ENABLED = try {
            val isEnabled = server.pluginManager.isPluginEnabled("PlaceholderAPI")
            
            if (isEnabled) {
                LoggingService.info("${ChatColor.GREEN}PlaceholderAPI found and enabled!")
            } else {
                LoggingService.warning("PlaceholderAPI not found. Placeholder functionality will be limited.")
            }
            
            isEnabled
        } catch (e: Exception) {
            LoggingService.warning("Error checking for PlaceholderAPI: ${e.message}")
            false
        }
    }
    
    /**
     * Registers event listeners for the plugin.
     */
    private fun registerEvents() {
        server.pluginManager.registerEvents(Events(this), this)
        LoggingService.info("Event listeners registered")
    }
    
    /**
     * Registers commands for the plugin.
     */
    private fun registerCommands() {
        getCommand("rewardsystem")?.setExecutor(CommandHandler(this)) 
            ?: LoggingService.severe("Failed to register 'rewardsystem' command")
        
        LoggingService.info("Commands registered")
    }
    
    /**
     * Loads mob cooldowns from the database.
     * This is done asynchronously to avoid blocking the main thread.
     */
    private fun loadCooldownsFromDatabase() {
        dbThreadPool.execute {
            try {
                val now = System.currentTimeMillis()
                val rewards = rewardsFromConfig
                
                if (rewards.isNullOrEmpty()) {
                    LoggingService.warning("No reward configurations loaded, skipping cooldown import")
                    return@execute
                }
                
                var importCount = 0
                var errorCount = 0
                
                rewards.values.forEach { reward ->
                    try {
                        // Import cooldowns for this reward, using the cooldowns map from reward
                        dbHelper.importCooldowns(
                            cooldownsMap = reward.cooldowns, 
                            rewardId = reward.id, 
                            currentTime = now
                        )
                        importCount++
                    } catch (e: Exception) {
                        errorCount++
                        LoggingService.warning("Failed to import cooldowns for reward ${reward.id}: ${e.message}")
                        LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
                    }
                }
                
                LoggingService.info("Cooldowns loaded from database: $importCount successful, $errorCount failed")
            } catch (e: Exception) {
                LoggingService.warning("Failed to load cooldowns: ${e.message}")
                LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    /**
     * Saves mob cooldowns to the database.
     */
    private fun saveCooldownsToDatabase() {
        try {
            val rewards = rewardsFromConfig
            
            if (rewards.isNullOrEmpty()) {
                LoggingService.warning("No reward configurations loaded, skipping cooldown export")
                return
            }
            
            // We run this on the main thread to avoid disrupting the user experience
            // but in the future this could also be done asynchronously
            var exportCount = 0
            var errorCount = 0
            
            rewards.values.forEach { reward ->
                try {
                    if (reward.cooldowns.isNotEmpty()) {
                        // Export cooldowns for this reward, using the cooldowns map from reward
                        dbHelper.exportCooldowns(
                            cooldownsMap = reward.cooldowns, 
                            rewardId = reward.id
                        )
                        exportCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                    LoggingService.warning("Failed to export cooldowns for reward ${reward.id}: ${e.message}")
                    LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
                }
            }
            
            LoggingService.info("Cooldowns saved to database: $exportCount successful, $errorCount failed")
        } catch (e: Exception) {
            LoggingService.warning("Failed to save cooldowns: ${e.message}")
            LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    companion object {
        /** Singleton instance */
        private lateinit var instance: Main
        
        /** Returns the singleton instance */
        fun getInstance(): Main = instance
        
        /** Minimum damage requirement for rewards */
        var minimumDamageRequirement: Double = 0.0
        
        /** Whether PlaceholderAPI is installed and enabled */
        var PLACEHOLDERAPI_ENABLED = false
        
        /** Map of reward configurations by ID */
        @JvmField
        var rewardsFromConfig: MutableMap<String, RewardMob>? = null
        
        /** Track last player to damage each entity */
        @JvmField
        val lastToucherMap = HashMap<UUID, String>()
        
        /** Track damage dealt by each player to each entity */
        @JvmField
        var damageMap = HashMap<UUID, HashMap<String, Double>>()
        
        /** Track entities monitored for each reward ID */
        @JvmField
        val uuidMap = HashMap<String, HashSet<UUID>>()
        
        /** Last time rewards were processed (for throttling) */
        @JvmField
        var lastRewardProcessTime: Long = 0
        
        /**
         * Translates color codes in a string.
         * 
         * @param text The text to translate
         * @return Text with color codes processed
         */
        @JvmStatic
        fun translateColors(text: String?): String = ColorUtils.translateColors(text)
    }
}
