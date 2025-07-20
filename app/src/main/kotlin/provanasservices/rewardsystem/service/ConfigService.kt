package provanasservices.rewardsystem.service

import org.bukkit.configuration.ConfigurationSection
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.model.RewardMob
import java.io.File
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
    fun loadRewardsFromConfig(plugin: Main): MutableMap<String, RewardMob> {
        val rewards = HashMap<String, RewardMob>()
        val configFile = File(plugin.dataFolder, "config.yml")
        
        LoggingService.debug("Attempting to load config.yml from: ${plugin.dataFolder.absolutePath}")
        
        if (!configFile.exists()) {
            plugin.saveDefaultConfig()
        }
        
        val config = plugin.config
        val rootSections = config.getKeys(false)
        LoggingService.debug("Found ${rootSections.size} root sections in config.yml: ${rootSections.joinToString()}")
        
        // Load global minimum damage setting
        Main.minimumDamageRequirement = config.getDouble("Settings.minimum_damage", 0.0)
        LoggingService.debug("Global minimum damage requirement: ${Main.minimumDamageRequirement}")
        
        // Load rewards from config
        val rewardsSection = config.getConfigurationSection("Rewards")
        if (rewardsSection == null) {
            LoggingService.warning("No Rewards section found in config.yml")
            return rewards
        }
        
        LoggingService.debug("Found Rewards section with ${rewardsSection.getKeys(false).size} reward configurations")
        
        for (rewardId in rewardsSection.getKeys(false)) {
            try {
                // Parse reward configuration from YAML
                val reward = parseRewardConfig(rewardId, rewardsSection.getConfigurationSection(rewardId))
                if (reward != null) {
                    rewards[rewardId] = reward
                    LoggingService.debug("Loaded reward configuration: $rewardId with name: ${reward.name}")
                } else {
                    LoggingService.warning("Failed to parse reward configuration for ID: $rewardId")
                }
            } catch (e: Exception) {
                LoggingService.severe("Error loading reward configuration for ID $rewardId: ${e.message}")
                LoggingService.severe("Stack trace: ${e.stackTraceToString()}")
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
                        val (normalRewards, chanceRewards) = parseChanceCommands(commands)
                        
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
    fun parseChanceCommands(commands: List<String>): Pair<MutableList<String>, MutableList<RewardMob.ChanceReward>> {
        val normalRewards = mutableListOf<String>()
        val chanceRewards = mutableListOf<RewardMob.ChanceReward>()
        
        LoggingService.debug("======== PARSING CHANCE COMMANDS - Total ${commands.size} commands ========")
        
        for (command in commands) {
            LoggingService.debug("EXAMINING COMMAND: \"$command\"")
            
            try {
                // Skip empty commands
                if (command.isBlank()) {
                    continue
                }
                
                // Always start with base assumption of a regular command
                var isChanceCommand = false
                var baseCommand = command
                var chance: Double? = null
                var chancePlaceholder: String? = null
                
                // PATTERN 1: Check if command contains a chance expression
                if (command.contains("%") || command.contains("{math:")) {
                    LoggingService.debug("INITIAL CHANCE COMMAND DETECTION: Command appears to contain a chance expression: \"$command\"")
                    
                    // MINECRAFT COMMAND WITH PERCENTAGE: "give %player% minecraft:diamond 1 50.0%" 
                    val minecraftCmdWithPercent = Regex("(give %player% \\S+ \\d+)\\s+(\\d+\\.?\\d*)%$").find(command)
                    if (minecraftCmdWithPercent != null) {
                        baseCommand = minecraftCmdWithPercent.groupValues[1]
                        chance = minecraftCmdWithPercent.groupValues[2].toDoubleOrNull()
                        
                        if (chance != null) {
                            isChanceCommand = true
                            LoggingService.debug("FOUND MINECRAFT COMMAND CHANCE: \"$baseCommand\" - Chance: $chance%")
                        }
                    }
                    
                    // PERCENTAGE AT END: "command 50.0%"
                    val percentageAtEnd = Regex("(.*?)\\s+(\\d+\\.?\\d*)%$").find(command)
                    if (percentageAtEnd != null && !isChanceCommand) {
                        baseCommand = percentageAtEnd.groupValues[1]
                        chance = percentageAtEnd.groupValues[2].toDoubleOrNull()
                        
                        if (chance != null) {
                            isChanceCommand = true
                            LoggingService.debug("FOUND PERCENTAGE CHANCE: \"$baseCommand\" - Chance: $chance%")
                        }
                    }
                    
                    // MATH EXPRESSION WITH PERCENTAGE: "command {math:...}%"
                    val mathWithPercent = Regex("(.*?)\\s+\\{math:(.*?)\\}%$").find(command)
                    if (mathWithPercent != null && !isChanceCommand) {
                        val mathExpression = mathWithPercent.groupValues[2]
                        
                        if (mathExpression.isNotBlank()) {
                            baseCommand = mathWithPercent.groupValues[1]
                            chancePlaceholder = "{math:$mathExpression}"
                            isChanceCommand = true
                            LoggingService.debug("FOUND MATH EXPRESSION CHANCE: \"$baseCommand\" - Expression: {math:$mathExpression}")
                        }
                    }
                    
                    // MATH EXPRESSION WITHOUT PERCENTAGE: "command {math:...}"
                    val mathExpression = Regex("(.*?)\\s+\\{math:(.*?)\\}$").find(command)
                    if (mathExpression != null && !isChanceCommand) {
                        val expression = mathExpression.groupValues[2]
                        
                        if (expression.isNotBlank()) {
                            baseCommand = mathExpression.groupValues[1]
                            chancePlaceholder = "{math:$expression}"
                            isChanceCommand = true
                            LoggingService.debug("FOUND MATH EXPRESSION: \"$baseCommand\" - Expression: {math:$expression}")
                        }
                    }
                    
                    // PLACEHOLDER CHANCE: "command %chance.value%"
                    val placeholderPattern = Regex("(.*?)\\s+%([^%]+)%$").find(command)
                    if (placeholderPattern != null && !isChanceCommand) {
                        val placeholder = placeholderPattern.groupValues[2]
                        
                        if (placeholder.isNotBlank() && 
                            (placeholder.startsWith("chance") || 
                             placeholder.startsWith("probability") || 
                             placeholder.contains("percent"))) {
                            
                            baseCommand = placeholderPattern.groupValues[1]
                            chancePlaceholder = "%$placeholder%"
                            isChanceCommand = true
                            LoggingService.debug("FOUND PLACEHOLDER CHANCE: \"$baseCommand\" - Placeholder: %$placeholder%")
                        }
                    }
                    
                    // Check if the command has any indicators of being a chance command
                    // This is a fallback for non-standard formats
                    if (!isChanceCommand) {
                        val words = command.split("\\s+".toRegex())
                        val lastWord = words.lastOrNull()
                        
                        if (lastWord != null && (lastWord.endsWith("%") || lastWord.contains("chance") || lastWord.contains("probability"))) {
                            LoggingService.debug("FOUND POTENTIAL CHANCE INDICATOR in command: \"$command\"")
                            
                            // Try to extract a number with percentage
                            val anyPercentage = Regex("(.*?)\\s+(\\d+\\.?\\d*)%$").find(command)
                            if (anyPercentage != null) {
                                val matchedChance = anyPercentage.groupValues[2].toDoubleOrNull()
                                
                                if (matchedChance != null) {
                                    baseCommand = anyPercentage.groupValues[1]
                                    chance = matchedChance
                                    isChanceCommand = true
                                    LoggingService.debug("FOUND GENERAL PERCENTAGE: \"$baseCommand\" - Chance: $chance%")
                                }
                            }
                        }
                    }
                }
                
                // Make decision based on detection results
                if (!isChanceCommand) {
                    if (command.contains("%") && !command.contains("{math:")) {
                        LoggingService.debug("NORMAL COMMAND: Contains % but not in chance format: \"$command\"")
                    } else {
                        LoggingService.debug("NORMAL COMMAND: No chance format detected: \"$command\"")
                    }
                    
                    normalRewards.add(command)
                } else {
                    // Create chance reward with parsed values
                    chanceRewards.add(RewardMob.ChanceReward(
                        commands = baseCommand,
                        chance = chance,
                        chancePlaceholder = chancePlaceholder
                    ))
                }
            } catch (e: Exception) {
                LoggingService.warning("Error parsing command \"$command\": ${e.message}")
                normalRewards.add(command) // Add as normal command in case of parsing error
            }
        }
        
        LoggingService.debug("CHANCE COMMAND PARSING COMPLETE: Found ${normalRewards.size} normal commands and ${chanceRewards.size} chance commands")
        
        LoggingService.debug("CHANCE COMMANDS FOUND:")
        chanceRewards.forEachIndexed { index, reward ->
            LoggingService.debug("  ${index+1}. Command: \"${reward.commands}\", Chance: ${reward.chance ?: "dynamic"}, Placeholder: ${reward.chancePlaceholder ?: "none"}")
        }
        
        LoggingService.debug("=======================================================")
        
        return Pair(normalRewards, chanceRewards)
    }
    
    /**
     * Parses a reward configuration from the configuration section.
     * 
     * @param id The reward ID
     * @param config The configuration section
     * @return A RewardMob object, or null if parsing failed
     */
    private fun parseRewardConfig(id: String, config: ConfigurationSection?): RewardMob? {
        if (config == null) {
            LoggingService.warning("Configuration section for reward $id is null")
            return null
        }
        
        try {
            return createRewardFromConfig(id, config)
        } catch (e: Exception) {
            LoggingService.warning("Error parsing reward $id: ${e.message}")
            LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
            return null
        }
    }
} 