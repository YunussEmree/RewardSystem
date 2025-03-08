package provanasservices.rewardsystem.service

import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.model.RewardMob
import provanasservices.rewardsystem.util.ColorUtils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Service class for handling configuration loading and management.
 * Provides methods to load reward configurations from config files.
 */
object ConfigService {
    /**
     * Loads all reward mob configurations from the plugin config.
     * 
     * @param plugin The plugin instance
     * @return A map of reward ID to RewardMob objects
     */
    fun loadRewardsFromConfig(plugin: JavaPlugin): MutableMap<String, RewardMob> {
        val rewards = HashMap<String, RewardMob>()
        
        // Log config details
        LoggingService.info("Attempting to load config.yml from: ${plugin.dataFolder.absolutePath}")
        
        // Get all root sections for debugging
        val rootSections = plugin.config.getKeys(false)
        LoggingService.info("Found ${rootSections.size} root sections in config.yml: ${rootSections.joinToString()}")
        
        // Get rewards section from config
        val rewardsSection = plugin.config.getConfigurationSection("Rewards")
        if (rewardsSection == null) {
            LoggingService.warning("'Rewards' section not found in config.yml")
            LoggingService.warning("Please check that config.yml contains a 'Rewards:' section with proper formatting")
            return rewards
        }
        
        LoggingService.info("Found Rewards section with ${rewardsSection.getKeys(false).size} reward configurations")
        
        // Load each reward configuration
        for (rewardId in rewardsSection.getKeys(false)) {
            val rewardSection = rewardsSection.getConfigurationSection(rewardId) ?: continue
            
            try {
                // Create and configure reward object - try new format first, fallback to old
                val reward = createRewardFromConfig(rewardId, rewardSection)
                rewards[rewardId] = reward
                
                LoggingService.info("Loaded reward configuration: $rewardId with name: ${reward.name}")
            } catch (e: Exception) {
                LoggingService.severe("Error loading reward $rewardId: ${e.message}")
                e.printStackTrace()
            }
        }
        
        LoggingService.info("Loaded ${rewards.size} reward configurations")
        return rewards
    }
    
    /**
     * Creates a RewardMob object from the configuration section.
     * 
     * @param id The reward ID
     * @param config The configuration section containing reward settings
     * @return A configured RewardMob object
     */
    private fun createRewardFromConfig(id: String, config: ConfigurationSection): RewardMob {
        val reward = RewardMob()
        
        // Set basic properties
        reward.id = config.getString("id") ?: id
        reward.type = config.getString("type")?.takeIf { it.isNotEmpty() }
        reward.name = config.getString("name")?.takeIf { it.isNotEmpty() }
        reward.caseSensitiveName = config.getBoolean("caseSensitiveName", false)
        reward.caseSensitiveType = config.getBoolean("caseSensitiveType", true)
        reward.minimumDamage = config.getDouble("minimumDamage", 0.0)
        reward.minimumDamagePercent = config.getDouble("minimumDamagePercent", 0.0)
        reward.radius = config.getInt("radius", -1)
        reward.enabledWorld = config.getString("enabledWorld")?.takeIf { it.isNotEmpty() }
        
        // Process enabledRegion correctly - set as null if it's an empty string or null
        val regionValue = config.getString("enabledRegion")
        reward.enabledRegion = if (regionValue.isNullOrBlank()) null else regionValue
        
        LoggingService.debug("CONFIG: Reward ${reward.id} - Region value: '${regionValue}', Final value: '${reward.enabledRegion}'")
        
        reward.cooldown = config.getInt("cooldown", 0)
        
        // Handle cooldown type
        val cooldownTypeStr = config.getString("cooldownType", "SECONDS")?.uppercase()
        reward.cooldownType = try {
            RewardMob.CooldownType.valueOf(cooldownTypeStr ?: "SECONDS")
        } catch (e: IllegalArgumentException) {
            LoggingService.warning("Invalid cooldown type for $id: $cooldownTypeStr, using SECONDS")
            RewardMob.CooldownType.SECONDS
        }
        
        reward.cooldownMessage = config.getString("cooldownMessage", "") ?: ""
        
        // Load reward messages
        reward.rewardMessages = config.getStringList("rewardMessages")
        
        // Load "all" rewards
        reward.allRewards = config.getStringList("allRewards")
        
        // Load all chance rewards
        val allChanceSection = config.getConfigurationSection("allChanceRewards")
        if (allChanceSection != null) {
            for (key in allChanceSection.getKeys(false)) {
                val chanceSection = allChanceSection.getConfigurationSection(key) ?: continue
                
                val chance = chanceSection.getDouble("chance", -1.0)
                val chancePlaceholder = chanceSection.getString("chancePlaceholder")
                val commands = chanceSection.getString("commands") ?: continue
                
                reward.allChanceRewards.add(
                    RewardMob.ChanceReward(
                        if (chance >= 0) chance else null, 
                        commands, 
                        chancePlaceholder
                    )
                )
            }
        }
        
        // Load position-specific rewards
        val rewardsSection = config.getConfigurationSection("rewards")
        if (rewardsSection != null) {
            for (posKey in rewardsSection.getKeys(false)) {
                try {
                    val position = posKey.toInt()
                    val commands = rewardsSection.getStringList(posKey)
                    
                    if (commands.isNotEmpty()) {
                        reward.rewards[position] = commands
                    }
                } catch (e: NumberFormatException) {
                    LoggingService.warning("Invalid position in rewards section: $posKey")
                }
            }
        }
        
        // Load position-specific chance rewards
        val chanceRewardsSection = config.getConfigurationSection("chanceRewards")
        if (chanceRewardsSection != null) {
            for (posKey in chanceRewardsSection.getKeys(false)) {
                try {
                    val position = posKey.toInt()
                    val chanceEntries = chanceRewardsSection.getConfigurationSection(posKey)
                    
                    if (chanceEntries != null) {
                        val chanceRewards = mutableListOf<RewardMob.ChanceReward>()
                        
                        for (entryKey in chanceEntries.getKeys(false)) {
                            val entrySection = chanceEntries.getConfigurationSection(entryKey) ?: continue
                            
                            val chance = entrySection.getDouble("chance", -1.0)
                            val commands = entrySection.getString("commands") ?: continue
                            val chancePlaceholder = entrySection.getString("chancePlaceholder")
                            
                            chanceRewards.add(
                                RewardMob.ChanceReward(
                                    if (chance >= 0) chance else null, 
                                    commands, 
                                    chancePlaceholder
                                )
                            )
                        }
                        
                        if (chanceRewards.isNotEmpty()) {
                            reward.chanceRewards[position] = chanceRewards
                        }
                    }
                } catch (e: NumberFormatException) {
                    LoggingService.warning("Invalid position in chanceRewards section: $posKey")
                }
            }
        }
        
        // Load last hit rewards
        reward.lastHitRewards = config.getStringList("lastHitRewards")
        
        // Debug info
        LoggingService.debug("Reward $id configured with:")
        LoggingService.debug(" - Name: ${reward.name}")
        LoggingService.debug(" - Type: ${reward.type}")
        LoggingService.debug(" - Position rewards: ${reward.rewards.size}")
        LoggingService.debug(" - All rewards: ${reward.allRewards?.size ?: 0}")
        LoggingService.debug(" - Last hit rewards: ${reward.lastHitRewards?.size ?: 0}")
        
        return reward
    }
    
    /**
     * Fallback method to create a RewardMob object from the old-style configuration format.
     * This is kept for backward compatibility.
     * 
     * @param id The reward ID
     * @param config The configuration section containing reward settings
     * @return A configured RewardMob object
     */
    private fun createRewardFromOldConfig(id: String, config: ConfigurationSection): RewardMob {
        val reward = RewardMob()
        reward.id = id
        
        // Handle name check
        val nameCheckSection = config.getConfigurationSection("NameCheck")
        if (nameCheckSection?.getBoolean("enabled", false) == true) {
            reward.name = nameCheckSection.getString("name")
        }
        
        // Handle mob type check
        val typeCheckSection = config.getConfigurationSection("MobTypeCheck")
        if (typeCheckSection?.getBoolean("enabled", false) == true) {
            reward.type = typeCheckSection.getString("type")?.uppercase()
        }
        
        // Handle world check
        val worldCheckSection = config.getConfigurationSection("WorldCheck")
        if (worldCheckSection?.getBoolean("enabled", false) == true) {
            reward.enabledWorld = worldCheckSection.getString("worldName")
        }
        
        // Handle region check
        val regionCheckSection = config.getConfigurationSection("RegionCheck")
        if (regionCheckSection?.getBoolean("enabled", false) == true) {
            reward.enabledRegion = regionCheckSection.getString("regionName")
        }
        
        // Get message section
        val messageSection = config.getConfigurationSection("RewardMessage")
        if (messageSection != null) {
            reward.rewardMessages = messageSection.getStringList("message")
            reward.radius = messageSection.getInt("radius", -1)
        }
        
        // Get cooldown settings
        reward.cooldown = config.getInt("Cooldown", 0)
        reward.cooldownMessage = config.getString("CooldownMessage", "") ?: ""
        
        // Get minimum damage requirement
        reward.minimumDamage = config.getDouble("MinimumDamageRequirement", 0.0)
        
        // Get reward commands
        val rewardCommandsSection = config.getConfigurationSection("RewardCommands")
        if (rewardCommandsSection != null) {
            // Load "all" rewards
            reward.allRewards = rewardCommandsSection.getStringList("all")
                .filter { it != "none" }
            
            // Load position-specific rewards
            for (posKey in rewardCommandsSection.getKeys(false)) {
                if (posKey == "all") continue
                
                try {
                    val position = posKey.toInt()
                    val commands = rewardCommandsSection.getStringList(posKey)
                        .filter { it != "none" }
                    
                    if (commands.isNotEmpty()) {
                        // Parse commands for chance rewards
                        val (normalRewards, chanceRewards) = parseCommandsForChance(commands)
                        
                        // Add normal rewards
                        if (normalRewards.isNotEmpty()) {
                            reward.rewards[position] = normalRewards
                        }
                        
                        // Add chance rewards
                        if (chanceRewards.isNotEmpty()) {
                            reward.chanceRewards[position] = chanceRewards
                        }
                    }
                } catch (e: NumberFormatException) {
                    LoggingService.warning("Invalid position in rewards section: $posKey")
                }
            }
        }
        
        return reward
    }
    
    /**
     * Parses commands to extract chance-based rewards.
     * Any command with a percentage sign at the end is treated as a chance reward.
     *
     * @param commands List of command strings
     * @return Pair of normal commands and chance reward objects
     */
    private fun parseCommandsForChance(commands: List<String>): Pair<List<String>, List<RewardMob.ChanceReward>> {
        val normalRewards = mutableListOf<String>()
        val chanceRewards = mutableListOf<RewardMob.ChanceReward>()
        
        for (command in commands) {
            LoggingService.info("Parsing command for chance: $command")
            
            try {
                // Pattern 1: Simple percentage (e.g., "give %player% diamond 1 50.0%")
                val simplePercentMatch = Regex("(.*?)\\s+(\\d+\\.?\\d*)%$").find(command)
                if (simplePercentMatch != null) {
                    val (baseCommand, percentValue) = simplePercentMatch.destructured
                    val chance = percentValue.toDoubleOrNull()
                    if (chance != null) {
                        LoggingService.info("Found simple chance reward: $baseCommand with chance $chance%")
                        chanceRewards.add(RewardMob.ChanceReward(chance, baseCommand.trim()))
                        continue
                    }
                }
                
                // Pattern 2: Math expression with percentage (e.g., "give %player% diamond 1 {math:...}%")
                val mathPercentMatch = Regex("(.*?)\\s+\\{math:(.*?)\\}%$").find(command)
                if (mathPercentMatch != null) {
                    val (baseCommand, mathExpression) = mathPercentMatch.destructured
                    LoggingService.info("Found math-based chance reward: $baseCommand with expression: $mathExpression")
                    chanceRewards.add(RewardMob.ChanceReward(
                        chance = null,
                        commands = baseCommand.trim(),
                        chancePlaceholder = "{math:$mathExpression}"
                    ))
                    continue
                }
                
                // Pattern 3: Only math expression (e.g., "give %player% diamond 1 {math:...}")
                val mathOnlyMatch = Regex("(.*?)\\s+\\{math:(.*?)\\}$").find(command)
                if (mathOnlyMatch != null) {
                    val (baseCommand, mathExpression) = mathOnlyMatch.destructured
                    LoggingService.info("Found math expression reward: $baseCommand with expression: $mathExpression")
                    
                    // We assume this is a chance calculation
                    chanceRewards.add(RewardMob.ChanceReward(
                        chance = null,
                        commands = baseCommand.trim(),
                        chancePlaceholder = "{math:$mathExpression}"
                    ))
                    continue
                }
                
                // Pattern 4: Placeholder chance (e.g., "give %player% diamond 1 %chance_value%")
                // Find the trailing placeholder (if the entire command ends with a placeholder)
                val placeholderMatch = Regex("(.*?)\\s+%([^%]+)%$").find(command)
                if (placeholderMatch != null) {
                    val (baseCommand, placeholder) = placeholderMatch.destructured
                    LoggingService.info("Found placeholder at the end of command: $baseCommand with placeholder: %$placeholder%")
                    
                    // Process each placeholder as a chance
                    chanceRewards.add(RewardMob.ChanceReward(
                        chance = null,
                        commands = baseCommand.trim(),
                        chancePlaceholder = "%$placeholder%"
                    ))
                    continue
                }
                
                // If the command contains % but not at the end in a recognized pattern, check if it has a percentage like "50%" anywhere
                if (command.contains("%")) {
                    val generalPercentMatch = Regex("(.*?)\\s+(\\d+\\.?\\d*)%(?:\\s+|$)").find(command)
                    if (generalPercentMatch != null) {
                        val (baseCommand, percentValue) = generalPercentMatch.destructured
                        val chance = percentValue.toDoubleOrNull()
                        if (chance != null) {
                            LoggingService.info("Found percentage in command: $baseCommand with chance $chance%")
                            chanceRewards.add(RewardMob.ChanceReward(chance, baseCommand.trim()))
                            continue
                        }
                    }
                    
                    // If no percentage found but contains %, assume it's a normal command with placeholders
                    LoggingService.info("Command contains % but not in recognized chance format, treating as normal: $command")
                }
                
                // All other commands are processed as normal commands
                LoggingService.info("No chance format detected, treating as normal command: $command")
                normalRewards.add(command)
                
            } catch (e: Exception) {
                LoggingService.warning("Error parsing command for chance: $command - ${e.message}")
                // If there's an error in parsing, treat it as a normal command
                normalRewards.add(command)
            }
        }
        
        return Pair(normalRewards, chanceRewards)
    }
} 