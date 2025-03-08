package provanasservices.rewardsystem.service

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.model.RewardMob
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import provanasservices.rewardsystem.util.MathEvaluator

/**
 * Service class for reward distribution and management.
 * Handles reward validation, distribution, and messaging.
 */
class RewardService(private val plugin: Main) {
    
    private val playerDamageMap = HashMap<String, Double>()
    
    /**
     * Filters players who are valid to receive rewards.
     * Checks permissions and minimum damage requirements.
     *
     * @param damageMap Map of player names to damage amounts
     * @param reward The reward configuration to validate against
     * @param totalDamage Total damage dealt to the entity
     * @return List of valid players
     */
    fun getValidPlayersForReward(
        damageMap: HashMap<String, Double>,
        reward: RewardMob,
        totalDamage: Double
    ): List<Player> {
        val validPlayers = ArrayList<Player>()
        
        // Check global minimum damage setting
        val globalMinDamage = Main.minimumDamageRequirement
        
        // Check mob-specific minimum damage setting
        val mobMinDamage = reward.minimumDamage
        
        // Calculate percentage-based minimum damage
        val percentMinDamage = if (reward.minimumDamagePercent > 0) {
            totalDamage * (reward.minimumDamagePercent / 100.0)
        } else {
            0.0
        }
        
        // Use the highest minimum damage value
        val effectiveMinDamage = maxOf(globalMinDamage, mobMinDamage, percentMinDamage)
        
        LoggingService.debug("Minimum damage requirements - Global: $globalMinDamage, Mob: $mobMinDamage, Percent: $percentMinDamage, Effective: $effectiveMinDamage")
        
        for ((playerName, damage) in damageMap) {
            // Check if player has contributed enough damage
            if (damage < effectiveMinDamage) {
                LoggingService.debug("Player $playerName didn't deal enough damage: $damage < $effectiveMinDamage")
                continue
            }
            
            // Get player object
            val player = Bukkit.getPlayerExact(playerName)
            if (player == null) {
                LoggingService.debug("Player $playerName is not online")
                continue
            }
            
            // Check cooldown
            if (isPlayerOnCooldown(player, reward)) {
                LoggingService.debug("Player $playerName is on cooldown for reward ${reward.id}")
                continue
            }
            
            // Store player's damage for later use
            playerDamageMap[player.name] = damage
            
            // Add to valid players
            validPlayers.add(player)
        }
        
        return validPlayers
    }
    
    /**
     * Checks if a player is on cooldown for a specific reward.
     *
     * @param player The player to check
     * @param reward The reward configuration
     * @return true if player is on cooldown, false otherwise
     */
    private fun isPlayerOnCooldown(player: Player, reward: RewardMob): Boolean {
        // Skip cooldown check if cooldown is disabled
        if (reward.cooldown <= 0) {
            return false
        }
        
        val playerUUID = player.uniqueId
        val expiry = reward.cooldowns[playerUUID]
        
        // If player has no cooldown, they are not on cooldown
        if (expiry == null) {
            return false
        }
        
        // Check if cooldown has expired
        val currentTime = System.currentTimeMillis()
        if (currentTime > expiry) {
            // Cooldown expired, remove from map
            reward.cooldowns.remove(playerUUID)
            return false
        }
        
        // Player is on cooldown, show remaining time if message is configured
        if (reward.cooldownMessage.isNotEmpty()) {
            val remainingMillis = expiry - currentTime
            val formattedTime = reward.cooldownType.formatRemaining(remainingMillis)
            val message = reward.cooldownMessage.replace("%time%", formattedTime)
            player.sendMessage(Main.translateColors(message))
        }
        
        // Player is on cooldown
        return true
    }
    
    /**
     * Distributes rewards to players based on their damage contribution.
     *
     * @param players List of valid players
     * @param entity The entity that was killed
     * @param reward The reward configuration
     * @param damageMap Map of player names to damage amounts
     * @param totalDamage Total damage dealt to the entity
     */
    fun distributeRewards(
        players: List<Player>,
        entity: LivingEntity,
        reward: RewardMob,
        damageMap: HashMap<String, Double>,
        totalDamage: Double
    ) {
        LoggingService.info("Distributing rewards for entity ${entity.type.name} (${entity.name}) with ID ${reward.id}")
        LoggingService.info("Players to reward: ${players.size} (${players.joinToString { it.name }})")
        
        try {
            // Set cooldowns for all players
            setupCooldowns(players, reward)
            LoggingService.debug("Cooldowns set up for ${players.size} players")
            
            // Copy damage data to our local map
            damageMap.forEach { (player, damage) -> 
                playerDamageMap[player] = damage
            }
            LoggingService.debug("Damage map copied: ${playerDamageMap.entries.joinToString { "${it.key}=${it.value}" }}")
            
            // Calculate damage ranks
            val damageRanks = calculateDamageRanks(damageMap)
            LoggingService.debug("Damage ranks calculated: ${damageRanks.entries.joinToString { "${it.key}=rank ${it.value}" }}")
            
            // Process "all" rewards
            val hasAllRewards = !(reward.allRewards.isNullOrEmpty() && reward.allChanceRewards.isEmpty())
            LoggingService.debug("Processing 'all' rewards: ${if (hasAllRewards) "yes" else "no all rewards configured"}")
            processAllRewards(players, reward, entity)
            
            // Process position-specific rewards
            val hasPositionRewards = reward.rewards.isNotEmpty() || reward.chanceRewards.isNotEmpty()
            LoggingService.debug("Processing position rewards: ${if (hasPositionRewards) "yes" else "no position rewards configured"}")
            processPositionRewards(players, reward, damageRanks, entity)
            
            // Send reward messages
            val hasMessages = !reward.rewardMessages.isNullOrEmpty()
            LoggingService.debug("Sending reward messages: ${if (hasMessages) "yes" else "no messages configured"}")
            sendRewardMessages(reward, entity, damageRanks)
            
            // Clear the damage map
            playerDamageMap.clear()
            
            LoggingService.info("Rewards successfully distributed for entity ${entity.type.name}")
        } catch (e: Exception) {
            LoggingService.severe("Error during reward distribution: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Sets up cooldowns for players.
     *
     * @param players List of players
     * @param reward The reward configuration
     */
    private fun setupCooldowns(players: List<Player>, reward: RewardMob) {
        // Skip if cooldown is disabled
        if (reward.cooldown <= 0) {
            return
        }
        
        val now = System.currentTimeMillis()
        val cooldownMillis = reward.cooldownType.toMillis(reward.cooldown)
        
        for (player in players) {
            reward.cooldowns[player.uniqueId] = now + cooldownMillis
        }
    }
    
    /**
     * Calculates player rankings based on damage dealt.
     *
     * @param damageMap Map of player names to damage amounts
     * @return Map of player names to their rank positions
     */
    private fun calculateDamageRanks(damageMap: HashMap<String, Double>): Map<String, Int> {
        // Sort players by damage (descending)
        val sortedEntries = damageMap.entries.sortedByDescending { it.value }
        
        // Create rank map
        val ranks = HashMap<String, Int>()
        var currentRank = 1
        
        for ((playerName, _) in sortedEntries) {
            ranks[playerName] = currentRank
            currentRank++
        }
        
        return ranks
    }
    
    /**
     * Processes rewards for all players regardless of rank.
     *
     * @param players List of valid players
     * @param reward The reward configuration
     * @param entity The entity that was killed
     */
    private fun processAllRewards(
        players: List<Player>,
        reward: RewardMob,
        entity: LivingEntity
    ) {
        // Skip if no "all" rewards configured
        val allRewards = reward.allRewards
        val allChanceRewards = reward.allChanceRewards
        if (allRewards.isNullOrEmpty() && allChanceRewards.isEmpty()) {
            return
        }
        
        for (player in players) {
            val damage = playerDamageMap[player.name] ?: 0.0
            
            // Process regular rewards
            allRewards?.forEach { command ->
                executeCommand(command, player, damage, entity)
            }
            
            // Process chance rewards
            processChanceRewards(allChanceRewards, player, damage, entity)
        }
    }
    
    /**
     * Processes position-specific rewards.
     *
     * @param players List of valid players
     * @param reward The reward configuration
     * @param damageRanks Map of player names to their rank positions
     * @param entity The entity that was killed
     */
    private fun processPositionRewards(
        players: List<Player>,
        reward: RewardMob,
        damageRanks: Map<String, Int>,
        entity: LivingEntity
    ) {
        for (player in players) {
            // Get player's ranking position, or assign last place if not found
            val rank = damageRanks[player.name] ?: damageRanks.size + 1
            val damage = playerDamageMap[player.name] ?: 0.0
            
            // Check if player is the last hitter
            val isLastHitter = Main.lastToucherMap[entity.uniqueId] == player.name
            
            // Process regular position rewards
            reward.rewards[rank]?.forEach { command ->
                executeCommand(command, player, damage, entity)
            }
            
            // Process last hit rewards (if configured)
            if (isLastHitter && reward.lastHitRewards?.isNotEmpty() == true) {
                LoggingService.debug("Processing last hit rewards for player ${player.name}")
                val lastHitCommands = reward.lastHitRewards ?: emptyList()
                lastHitCommands.forEach { command ->
                    executeCommand(command, player, damage, entity)
                }
            }
            
            // Process chance position rewards
            reward.chanceRewards[rank]?.let { chanceRewards ->
                processChanceRewards(chanceRewards, player, damage, entity)
            }
        }
    }
    
    /**
     * Processes chance-based rewards.
     *
     * @param chanceRewards List of chance rewards
     * @param player Player to receive rewards
     * @param damage Damage dealt by player
     * @param entity The entity that was killed
     */
    private fun processChanceRewards(
        chanceRewards: List<RewardMob.ChanceReward>,
        player: Player,
        damage: Double,
        entity: LivingEntity
    ) {
        LoggingService.info(">>>>>> Processing ${chanceRewards.size} chance rewards for player ${player.name} <<<<<<")
        
        for (chanceReward in chanceRewards) {
            try {
                var chance = chanceReward.chance
                val originalCommand = chanceReward.commands
                
                LoggingService.info("------------------------------------------")
                LoggingService.info("Processing chance command: \"${originalCommand}\"")
                LoggingService.info("Initial chance value: $chance, Chance placeholder: ${chanceReward.chancePlaceholder}")
                
                // Handle placeholder or math expression chance
                if (chance == null && chanceReward.chancePlaceholder != null) {
                    val placeholder = chanceReward.chancePlaceholder
                    
                    // Handle math expression format {math:...}
                    if (placeholder.startsWith("{math:") && placeholder.endsWith("}")) {
                        // Use MathEvaluator to evaluate the expression
                        try {
                            // Extract the expression from {math:...}
                            val expression = placeholder.substring(6, placeholder.length - 1)
                            LoggingService.info("Evaluating math expression for chance: $expression")
                            
                            // Replace placeholders - initially there may be placeholders like %player_level%
                            val processedExpression = PlaceholderService.setPlaceholders(player, expression)
                            LoggingService.info("Processed expression after placeholders: $processedExpression")
                            
                            // Calculate the mathematical expression
                            val result = MathEvaluator.evaluateExpression(processedExpression, player)
                            
                            // Convert the result to double
                            chance = result.toDoubleOrNull()
                            if (chance == null) {
                                LoggingService.warning("Math expression result is not a valid number: $result")
                                chance = 0.0
                            } else {
                                // Check and fix output in percentage format
                                if (result.endsWith("%")) {
                                    // If there's a percentage sign, remove it and extract the number
                                    val numericPart = result.substring(0, result.length - 1)
                                    chance = numericPart.toDoubleOrNull() ?: 0.0
                                    LoggingService.info("Found percentage symbol in result, extracted: $chance")
                                }
                                
                                LoggingService.info("Math expression evaluated: $expression = $chance")
                            }
                        } catch (e: Exception) {
                            LoggingService.warning("Failed to evaluate math expression: ${e.message}")
                            chance = 0.0
                        }
                    } else {
                        // Standard placeholder processing
                        try {
                            val processedValue = PlaceholderService.setPlaceholders(player, placeholder)
                            LoggingService.info("Processing standard placeholder: $placeholder -> $processedValue")
                            
                            // If the placeholder didn't change
                            if (processedValue == placeholder) {
                                LoggingService.warning("Placeholder didn't change: $placeholder. Defaulting to 0% chance")
                                chance = 0.0
                            } else {
                                chance = processedValue.toDoubleOrNull()
                                if (chance == null) {
                                    LoggingService.warning("Placeholder value is not a valid number: $processedValue")
                                    chance = 0.0
                                } else {
                                    LoggingService.info("Placeholder processed successfully: $placeholder = $chance")
                                }
                            }
                        } catch (e: Exception) {
                            LoggingService.warning("Error processing placeholder: ${e.message}")
                            chance = 0.0
                        }
                    }
                }
                
                // Skip if no valid chance
                if (chance == null || chance <= 0) {
                    LoggingService.info("Skipping chance reward - final chance value is ${chance ?: "null"} (not positive)")
                    continue
                }
                
                // Make sure chance is properly formatted as a percentage (0-100)
                if (chance > 100) {
                    LoggingService.info("Chance value $chance is greater than 100, capping at 100%")
                    chance = 100.0
                }
                
                // Check if reward should be given
                LoggingService.info("Running chance test with ${chance}% probability...")
                val shouldGive = shouldGiveChanceReward(chance)
                LoggingService.info("Chance calculation for player ${player.name}: ${chance}% chance - Result: ${if (shouldGive) "SUCCESS! Reward will be given." else "FAIL! No reward will be given."}")
                
                if (shouldGive) {
                    // Chance test successful, execute the command
                    val cleanCommand = stripChanceExpressions(chanceReward.commands)
                    LoggingService.info("CHANCE TEST PASSED! Player ${player.name} won chance reward with ${chance}% chance!")
                    LoggingService.info("Executing command: $cleanCommand (original: ${chanceReward.commands})")
                    executeCommand(cleanCommand, player, damage, entity)
                } else {
                    // Chance test failed, do not execute the command
                    LoggingService.info("CHANCE TEST FAILED! Player ${player.name} did NOT win chance reward with ${chance}% chance.")
                    LoggingService.info("Command will NOT be executed: ${chanceReward.commands}")
                }
                LoggingService.info("------------------------------------------")
            } catch (e: Exception) {
                LoggingService.severe("Error processing chance reward: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Determines if a chance-based reward should be given.
     *
     * @param chance The percentage chance (0-100)
     * @return true if reward should be given, false otherwise
     */
    private fun shouldGiveChanceReward(chance: Double): Boolean {
        try {
            // Log the input chance value
            LoggingService.info("Starting chance calculation with chance value: $chance%")
            
            // Check bounds
            if (chance <= 0.0) {
                LoggingService.info("Chance is 0 or negative ($chance), automatically failing")
                return false
            }
            
            if (chance >= 100.0) {
                LoggingService.info("Chance is 100 or greater ($chance), automatically succeeding")
                return true
            }
            
            // Get a random number between 0 and 100
            val randomPercent = ThreadLocalRandom.current().nextDouble(0.0, 100.0)
            
            // Success if random number is less than chance percentage
            val success = randomPercent < chance
            
            LoggingService.info("CHANCE SYSTEM: Chance=$chance%, Random roll=$randomPercent%, Result=${if (success) "SUCCESS" else "FAIL"}")
            
            return success
            
        } catch (e: Exception) {
            LoggingService.severe("Error in chance calculation: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Executes a command with placeholders.
     *
     * @param command The command to execute
     * @param player The player context
     * @param damage Damage dealt by player
     * @param entity The entity that was killed
     */
    private fun executeCommand(
        command: String,
        player: Player,
        damage: Double,
        entity: LivingEntity
    ) {
        try {
            // Log the original command
            LoggingService.info("Starting command execution with: $command")
            
            // Remove chance expressions and math expressions
            val cleanCommand = stripChanceExpressions(command)
            LoggingService.debug("Command after chance expression removal: $cleanCommand")
            
            // If the command has changed significantly, log a warning
            if (cleanCommand.length < command.length * 0.5) {
                LoggingService.warning("Command was significantly shortened after stripping chance expressions. Original: '$command', Cleaned: '$cleanCommand'")
            }
            
            // Replace basic placeholders
            var processedCommand = cleanCommand
                .replace("%player%", player.name)
                .replace("%damage%", damage.toInt().toString())
                .replace("%entity%", entity.type.name)
                .replace("%position%", entity.location.x.toInt().toString() + " " + 
                                    entity.location.y.toInt() + " " + 
                                    entity.location.z.toInt())
                .replace("%world%", entity.world.name)
            
            LoggingService.debug("Command after basic placeholder replacement: $processedCommand")
            
            // Use MathEvaluator to process any math expressions in the command
            processedCommand = MathEvaluator.processCommand(processedCommand, player, "round")
            LoggingService.debug("Command after math evaluation: $processedCommand")
            
            // Process with PlaceholderAPI if available
            if (Main.PLACEHOLDERAPI_ENABLED) {
                processedCommand = PlaceholderService.setPlaceholders(player, processedCommand)
                LoggingService.debug("Command after PlaceholderAPI processing: $processedCommand")
            }
            
            // Validate command before execution
            if (!isCommandSafe(processedCommand)) {
                LoggingService.severe("Potentially unsafe command blocked: $processedCommand")
                return
            }
            
            // Execute the command
            try {
                LoggingService.info("Executing command: $processedCommand")
                val result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                if (result) {
                    LoggingService.info("Command executed successfully")
                } else {
                    LoggingService.warning("Command did not execute successfully. Command was: $processedCommand")
                }
            } catch (e: Exception) {
                LoggingService.warning("Failed to execute command '$processedCommand': ${e.message}")
                // Show error details
                LoggingService.warning("Error details: ${e.javaClass.name} - ${e.stackTrace.joinToString("\n  ")}")
            }
        } catch (e: Exception) {
            LoggingService.severe("Error in executeCommand: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Strips chance expressions from a command string.
     * Removes percentage chances and math expressions from the end of the command.
     *
     * @param command The command string to clean
     * @return The cleaned command without chance expressions
     */
    private fun stripChanceExpressions(command: String): String {
        try {
            LoggingService.info("Stripping chance expressions from command: $command")
            
            // Strip simple percentage chances: "give %player% diamond 1 50.0%"
            val percentageMatch = Regex("(.*?)\\s+\\d+\\.?\\d*%$").find(command)
            if (percentageMatch != null) {
                val result = percentageMatch.groupValues[1].trim()
                LoggingService.info("Removed percentage chance expression, result: $result")
                return result
            }
            
            // Strip math expressions with percentage: "give %player% diamond 1 {math:...}%"
            val mathPercentMatch = Regex("(.*?)\\s+\\{math:.*?\\}%$").find(command)
            if (mathPercentMatch != null) {
                val result = mathPercentMatch.groupValues[1].trim()
                LoggingService.info("Removed math expression with percentage, result: $result")
                return result
            }
            
            // Strip math expressions without percentage: "give %player% diamond 1 {math:...}"
            val mathMatch = Regex("(.*?)\\s+\\{math:.*?\\}$").find(command)
            if (mathMatch != null) {
                val result = mathMatch.groupValues[1].trim()
                LoggingService.info("Removed math expression, result: $result")
                return result
            }
            
            // Strip placeholder chances: "give %player% diamond 1 %placeholder%"
            val placeholderMatch = Regex("(.*?)\\s+%[^%]+%$").find(command)
            if (placeholderMatch != null) {
                val result = placeholderMatch.groupValues[1].trim()
                LoggingService.info("Removed placeholder chance, result: $result")
                return result
            }
            
            // More general pattern to catch percentages anywhere in the command
            val generalPercentMatch = Regex("(.*?)\\s+\\d+\\.?\\d*%(?:\\s+|$)").find(command)
            if (generalPercentMatch != null) {
                val result = generalPercentMatch.groupValues[1].trim()
                LoggingService.info("Removed general percentage expression, result: $result")
                return result
            }
            
            // No chance expressions found, return original command
            LoggingService.info("No chance expressions found, using original command")
            return command
        } catch (e: Exception) {
            LoggingService.warning("Error while stripping chance expressions: ${e.message}")
            // Return original command on error
            return command
        }
    }
    
    /**
     * Checks if a command is safe to execute.
     * Ensures command starts with allowed prefix and doesn't contain dangerous patterns.
     * 
     * @param command The command to check
     * @return true if command is safe, false otherwise
     */
    private fun isCommandSafe(command: String): Boolean {
        // List of allowed command prefixes
        val allowedPrefixes = listOf(
            "give", "effect", "xp", "exp", "title", "tellraw", "msg", "message",
            "say", "tp", "teleport", "particle", "playsound", "advancement", "execute",
            "summon", "kill", "gamemode", "enchant", "clear", "spawnpoint"
        )
        
        // Extract the main command (remove arguments)
        val cmdParts = command.trim().split("\\s+".toRegex(), 2)
        if (cmdParts.isEmpty()) return false
        
        val mainCommand = cmdParts[0].lowercase()
        
        // Check if command starts with an allowed prefix
        val isAllowed = allowedPrefixes.any { prefix -> mainCommand == prefix }
        
        // Block potentially dangerous commands
        val hasDangerousPattern = command.contains("op ") || 
                                 command.contains("deop ") || 
                                 command.contains("stop") || 
                                 command.contains("reload") || 
                                 command.contains("ban") || 
                                 command.contains("pardon") ||
                                 command.contains("bukkit:") ||
                                 command.contains("minecraft:op") ||
                                 command.contains("pex") ||
                                 command.contains("luckperms") ||
                                 command.contains("permissions")
        
        return isAllowed && !hasDangerousPattern
    }
    
    /**
     * Sends reward messages to nearby players.
     *
     * @param reward The reward configuration
     * @param entity The entity that was killed
     * @param damageRanks Map of player names to their rank positions
     */
    private fun sendRewardMessages(
        reward: RewardMob,
        entity: LivingEntity,
        damageRanks: Map<String, Int>
    ) {
        val messages = reward.rewardMessages ?: return
        
        // Get top damage player
        val topDamagePlayer = damageRanks.entries
            .filter { (playerName, _) -> Bukkit.getPlayerExact(playerName) != null }
            .minByOrNull { it.value }
            ?.key
        
        // Get player who dealt the killing blow
        val lastToucher = Main.lastToucherMap[entity.uniqueId]
        
        // Get top damage dealers and their damages
        val sortedDamageList = playerDamageMap.entries
            .sortedByDescending { it.value }
            .take(3) // Take top 3 players
        
        // Create map for top player placeholders
        val topPlayerPlaceholders = HashMap<String, String>()
        
        // Fill placeholder map with top player data
        for (i in 0 until sortedDamageList.size) {
            val rank = i + 1
            val entry = sortedDamageList[i]
            topPlayerPlaceholders["%top_name_$rank%"] = entry.key
            // Format damage as integer
            topPlayerPlaceholders["%top_damage_$rank%"] = entry.value.toInt().toString()
        }
        
        // Fill missing placeholders with default values
        for (i in sortedDamageList.size + 1..3) {
            val rank = i
            topPlayerPlaceholders["%top_name_$rank%"] = "None"
            topPlayerPlaceholders["%top_damage_$rank%"] = "0"
        }
        
        for (message in messages) {
            // Skip empty messages
            if (message.isBlank()) continue
            
            // Replace basic placeholders in message
            var processedMessage = message
                .replace("%entity%", entity.type.name)
                .replace("%killer%", lastToucher ?: "Unknown")
                .replace("%top_damage%", topDamagePlayer ?: "Unknown")
            
            // Replace top player placeholders
            for ((placeholder, value) in topPlayerPlaceholders) {
                processedMessage = processedMessage.replace(placeholder, value)
            }
            
            // Send to each player with personal damage placeholder
            if (processedMessage.contains("%personal_damage%")) {
                // Need to customize message for each player
                val players = if (reward.radius <= 0) {
                    Bukkit.getOnlinePlayers()
                } else {
                    getNearbyPlayers(entity, reward.radius.toDouble())
                }
                
                for (player in players) {
                    // Get player's personal damage
                    val playerDamage = playerDamageMap[player.name]?.toInt()?.toString() ?: "0"
                    
                    // Replace personal damage placeholder
                    var playerMessage = processedMessage.replace("%personal_damage%", playerDamage)
                    
                    // Apply PlaceholderAPI if available
                    if (Main.PLACEHOLDERAPI_ENABLED) {
                        playerMessage = PlaceholderService.setPlaceholders(player, playerMessage)
                    }
                    
                    // Translate color codes
                    playerMessage = Main.translateColors(playerMessage)
                    
                    // Send message
                    player.sendMessage(playerMessage)
                }
            } else {
                // No personal placeholders, can send the same message to everyone
                
                // Apply PlaceholderAPI if needed for other placeholders
                if (Main.PLACEHOLDERAPI_ENABLED && Bukkit.getOnlinePlayers().isNotEmpty()) {
                    // Just use the first player for general placeholders
                    val anyPlayer = Bukkit.getOnlinePlayers().first()
                    processedMessage = PlaceholderService.setPlaceholders(anyPlayer, processedMessage)
                }
                
                // Translate color codes
                processedMessage = Main.translateColors(processedMessage)
                
                // Send message based on radius
                if (reward.radius <= 0) {
                    // Broadcast to entire server
                    Bukkit.broadcastMessage(processedMessage)
                } else {
                    // Send to nearby players only
                    val nearbyPlayers = getNearbyPlayers(entity, reward.radius.toDouble())
                    for (player in nearbyPlayers) {
                        player.sendMessage(processedMessage)
                    }
                }
            }
        }
    }
    
    /**
     * Gets players within a radius of an entity.
     *
     * @param entity The center entity
     * @param radius The radius to check
     * @return List of nearby players
     */
    private fun getNearbyPlayers(entity: LivingEntity, radius: Double): List<Player> {
        // Setting an upper limit on radius (very large values can cause performance issues)
        val maxRadius = 1000.0
        val effectiveRadius = if (radius > maxRadius) {
            LoggingService.warning("Radius $radius is too large, limiting to $maxRadius for performance reasons")
            maxRadius
        } else {
            radius
        }
        
        // Performance optimization: Return empty list if world is empty or has no players
        if (entity.world.players.isEmpty()) {
            return emptyList()
        }
        
        return entity.getNearbyEntities(effectiveRadius, effectiveRadius, effectiveRadius)
            .filterIsInstance<Player>()
    }
} 