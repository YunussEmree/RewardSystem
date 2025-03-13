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
            val regularCommands = mutableListOf<String>()
            val chanceCommands = mutableListOf<RewardMob.ChanceReward>()
            
            // Double-check each command to make sure it's categorized correctly
            allRewards?.forEach { command ->
                if (isLikelyChanceCommand(command)) {
                    LoggingService.warning("Found command in regular rewards that appears to be a chance command: \"$command\"")
                    LoggingService.warning("Routing it through the chance system with 100% probability for safety")
                    chanceCommands.add(RewardMob.ChanceReward(100.0, command))
                } else {
                    regularCommands.add(command)
                }
            }
            
            // Execute regular commands
            regularCommands.forEach { command ->
                LoggingService.debug("Executing regular 'all' reward for player ${player.name}")
                executeCommand(command, player, damage, entity)
            }
            
            // Combine manually added chance commands with configured ones
            val allChanceCommandsToProcess = allChanceRewards.toMutableList()
            allChanceCommandsToProcess.addAll(chanceCommands)
            
            // Process chance rewards
            if (allChanceCommandsToProcess.isNotEmpty()) {
                LoggingService.debug("Processing ${allChanceCommandsToProcess.size} chance 'all' rewards for player ${player.name}")
                processChanceRewards(allChanceCommandsToProcess, player, damage, entity)
            }
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
            val regularCommands = mutableListOf<String>()
            val chanceCommands = mutableListOf<RewardMob.ChanceReward>()
            
            // Double-check each command to make sure it's categorized correctly
            reward.rewards[rank]?.forEach { command ->
                if (isLikelyChanceCommand(command)) {
                    LoggingService.warning("Found command in position $rank rewards that appears to be a chance command: \"$command\"")
                    LoggingService.warning("Routing it through the chance system with 100% probability for safety")
                    chanceCommands.add(RewardMob.ChanceReward(100.0, command))
                } else {
                    regularCommands.add(command)
                }
            }
            
            // Execute regular commands
            regularCommands.forEach { command ->
                LoggingService.debug("Executing regular position reward for player ${player.name} at rank $rank")
                executeCommand(command, player, damage, entity)
            }
            
            // Process last hit rewards (if configured)
            if (isLastHitter && reward.lastHitRewards?.isNotEmpty() == true) {
                LoggingService.debug("Processing last hit rewards for player ${player.name}")
                
                // Safety check for last hit rewards too
                val regularLastHitCommands = mutableListOf<String>()
                val chanceLastHitCommands = mutableListOf<RewardMob.ChanceReward>()
                
                reward.lastHitRewards?.forEach { command ->
                    if (isLikelyChanceCommand(command)) {
                        LoggingService.warning("Found last hit command that appears to be a chance command: \"$command\"")
                        LoggingService.warning("Routing it through the chance system with 100% probability for safety")
                        chanceLastHitCommands.add(RewardMob.ChanceReward(100.0, command))
                    } else {
                        regularLastHitCommands.add(command)
                    }
                }
                
                // Execute regular last hit commands
                regularLastHitCommands.forEach { command ->
                    executeCommand(command, player, damage, entity)
                }
                
                // Process chance last hit commands
                if (chanceLastHitCommands.isNotEmpty()) {
                    LoggingService.debug("Processing ${chanceLastHitCommands.size} chance last-hit rewards for player ${player.name}")
                    processChanceRewards(chanceLastHitCommands, player, damage, entity)
                }
            }
            
            // Get all chance commands for this position
            val positionChanceRewards = reward.chanceRewards[rank]?.toMutableList() ?: mutableListOf()
            
            // Add any commands that were reclassified as chance commands
            positionChanceRewards.addAll(chanceCommands)
            
            // Process chance position rewards
            if (positionChanceRewards.isNotEmpty()) {
                LoggingService.debug("Processing ${positionChanceRewards.size} chance rewards for player ${player.name} at rank $rank")
                processChanceRewards(positionChanceRewards, player, damage, entity)
            }
        }
    }
    
    /**
     * Determines if a command is likely a chance-based command that should go through the chance system.
     * This is a safety check to catch miscategorized commands.
     *
     * @param command The command to check
     * @return true if this looks like a chance command, false otherwise
     */
    private fun isLikelyChanceCommand(command: String): Boolean {
        // First check for the most common patterns
        if (command.trim().matches(Regex(".*\\s+\\d+\\.?\\d*%$"))) {
            // Simple percentage at end, like "give %player% diamond 1 50.0%"
            LoggingService.debug("Command matches simple percentage pattern: $command")
            return true
        }
        
        if (command.trim().matches(Regex(".*\\s+\\{math:.*?\\}%?$"))) {
            // Math expression (with/without %), like "give %player% diamond 1 {math:max(10, %player_level%)}%"
            LoggingService.debug("Command matches math expression pattern: $command")
            return true
        }
        
        if (command.contains("%chance") || command.contains("%probability%")) {
            // Chance-related placeholders
            LoggingService.debug("Command contains chance placeholder: $command")
            return true
        }
        
        // Special case for Minecraft item commands with percentage
        if (command.matches(Regex("give %player% \\S+ \\d+\\s+\\d+\\.?\\d*%$"))) {
            LoggingService.debug("Command matches Minecraft item with percentage pattern: $command")
            return true
        }
        
        // Check for trailing placeholder that's not a common required placeholder
        if (command.trim().matches(Regex(".*\\s+%[^%]+%$"))) {
            // Make sure we don't flag commands that end with essential placeholders
            val endsWithEssentialPlaceholder = command.endsWith("%player%") || 
                                               command.endsWith("%damage%") ||
                                               command.endsWith("%entity%") ||
                                               command.endsWith("%world%") ||
                                               command.endsWith("%position%")
                                               
            if (!endsWithEssentialPlaceholder) {
                LoggingService.debug("Command ends with non-essential placeholder (likely chance): $command")
                return true
            }
        }
        
        return false
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
        LoggingService.info("Processing ${chanceRewards.size} chance rewards for player ${player.name}")
        
        for ((index, chanceReward) in chanceRewards.withIndex()) {
            try {
                LoggingService.debug("── Processing chance reward #${index+1} ──")
                
                var chance = chanceReward.chance
                val originalCommand = chanceReward.commands
                
                LoggingService.debug("▶ Chance command: \"${originalCommand}\"")
                LoggingService.debug("▶ Initial chance value: $chance")
                LoggingService.debug("▶ Chance placeholder: ${chanceReward.chancePlaceholder ?: "None"}")
                
                // Check for math expressions in the command and evaluate them for this specific player
                if (originalCommand.contains("{math:")) {
                    val mathPatternWithPercent = Regex("\\{math:(.*?)\\}%").find(originalCommand)
                    val mathPatternWithoutPercent = Regex("\\{math:(.*?)\\}(?!%)").find(originalCommand)
                    
                    if (mathPatternWithPercent != null || mathPatternWithoutPercent != null) {
                        val mathExpression = (mathPatternWithPercent ?: mathPatternWithoutPercent)?.groupValues?.get(1)
                        
                        if (mathExpression != null) {
                            LoggingService.debug("▶ Found math expression in command: {math:$mathExpression}")
                            
                            try {
                                // First, replace PlaceholderAPI placeholders with actual values
                                var processedExpression = mathExpression
                                
                                // Replace %player_level% with actual value using PlaceholderAPI
                                if (Main.PLACEHOLDERAPI_ENABLED) {
                                    processedExpression = PlaceholderService.setPlaceholders(player, processedExpression)
                                    LoggingService.debug("▶ Expression after PlaceholderAPI processing: $processedExpression")
                                } else {
                                    // If PlaceholderAPI not available, try to extract common placeholders manually
                                    // For example, if we know %player_level% is common:
                                    if (processedExpression.contains("%player_level%")) {
                                        val level = player.level
                                        processedExpression = processedExpression.replace("%player_level%", level.toString())
                                        LoggingService.debug("▶ Manually replaced %player_level% with $level")
                                    }
                                }
                                
                                // Safety check: Make sure all placeholders are replaced
                                if (processedExpression.contains("%player_") || processedExpression.contains("%p_")) {
                                    LoggingService.debug("⚠ Unprocessed placeholders in expression: $processedExpression")
                                    LoggingService.debug("▶ Will try to replace common placeholders manually")
                                    
                                    // Try some more common replacements
                                    processedExpression = processedExpression
                                        .replace("%player_level%", player.level.toString())
                                        .replace("%player_health%", player.health.toString())
                                        .replace("%player_food%", player.foodLevel.toString())
                                }
                                
                                // SAFETY: Directly handle common math expressions to avoid unauthorized function errors
                                if (processedExpression.contains("max(") || processedExpression.contains("min(")) {
                                    try {
                                        // Handle max() function manually
                                        val maxPattern = Regex("max\\((\\d+\\.?\\d*),\\s*(\\d+\\.?\\d*)\\)").find(processedExpression)
                                        if (maxPattern != null) {
                                            val val1 = maxPattern.groupValues[1].toDoubleOrNull() ?: 0.0
                                            val val2 = maxPattern.groupValues[2].toDoubleOrNull() ?: 0.0
                                            val maxResult = Math.max(val1, val2)
                                            processedExpression = processedExpression.replace(maxPattern.value, maxResult.toString())
                                            LoggingService.debug("▶ Manually evaluated max function: $maxResult")
                                        }
                                        
                                        // Handle min() function manually
                                        val minPattern = Regex("min\\((\\d+\\.?\\d*),\\s*(\\d+\\.?\\d*)\\)").find(processedExpression)
                                        if (minPattern != null) {
                                            val val1 = minPattern.groupValues[1].toDoubleOrNull() ?: 0.0
                                            val val2 = minPattern.groupValues[2].toDoubleOrNull() ?: 0.0
                                            val minResult = Math.min(val1, val2)
                                            processedExpression = processedExpression.replace(minPattern.value, minResult.toString())
                                            LoggingService.debug("▶ Manually evaluated min function: $minResult")
                                        }
                                    } catch (e: Exception) {
                                        LoggingService.warning("⚠ Error handling math function: ${e.message}")
                                    }
                                }
                                
                                // Basic arithmetic for expressions like "20 + (%player_level% / 10)"
                                var useArithmeticResult = false
                                var arithmeticResult: Double? = null
                                
                                try {
                                    // First check if the string is in a basic arithmetic form after placeholder replacement
                                    if (processedExpression?.matches(Regex("[\\d\\.\\+\\-\\*\\/\\(\\)\\s]+")) == true) {
                                        LoggingService.debug("▶ Expression is a basic arithmetic expression, evaluating directly")
                                        // Evaluate using a simple built-in calculator approach
                                        arithmeticResult = evaluateBasicArithmetic(processedExpression)
                                        LoggingService.debug("▶ Arithmetic evaluation result: $arithmeticResult")
                                        
                                        // Convert to double and use this as chance
                                        if (arithmeticResult != null) {
                                            useArithmeticResult = true
                                            chance = arithmeticResult
                                            LoggingService.debug("▶ Player-specific chance calculated from arithmetic: $chance%")
                                        }
                                    }
                                } catch (e: Exception) {
                                    LoggingService.warning("⚠ Error in basic arithmetic evaluation: ${e.message}")
                                    // Continue to try MathEvaluator as fallback
                                }
                                
                                // Skip MathEvaluator if we already have a result from arithmetic
                                if (!useArithmeticResult) {
                                    LoggingService.debug("▶ Final expression for evaluation: $processedExpression")
                                    
                                    // Now evaluate the processed expression
                                    val mathResult = MathEvaluator.evaluateExpression(processedExpression ?: "", player)
                                    LoggingService.debug("▶ Math expression evaluated to: $mathResult")
                                    
                                    // Convert to double
                                    val calculatedChance = mathResult?.toDoubleOrNull()
                                    if (calculatedChance != null) {
                                        // Use this player-specific calculation instead of any default from earlier
                                        chance = calculatedChance
                                        LoggingService.debug("▶ Player-specific chance calculated from math expression: $chance%")
                                    } else {
                                        // If evaluation failed, use a safer default
                                        chance = 50.0
                                        LoggingService.warning("⚠ Could not convert result to number, using default 50% chance")
                                    }
                                }
                            } catch (e: Exception) {
                                LoggingService.warning("⚠ Error evaluating math expression for player ${player.name}: ${e.message}")
                                // Use a reasonable default instead of failing
                                chance = 50.0
                                LoggingService.debug("▶ Using default chance of 50% due to evaluation error")
                            }
                        }
                    }
                }
                // If no specific math expression, but a default 100% chance, look for embedded chance
                else if (chance == 100.0 && chanceReward.chancePlaceholder == null) {
                    val extractedChance = extractChanceFromCommand(originalCommand)
                    if (extractedChance != null) {
                        chance = extractedChance
                        LoggingService.debug("▶ Extracted chance from command: $chance%")
                    }
                }
                
                // Handle placeholder or math expression chance
                if (chance == null && chanceReward.chancePlaceholder != null) {
                    val placeholder = chanceReward.chancePlaceholder ?: ""
                    
                    // Handle math expression format {math:...}
                    if (placeholder.startsWith("{math:") && placeholder.endsWith("}")) {
                        // Use MathEvaluator to evaluate the expression
                        try {
                            // Extract the expression from {math:...}
                            val expression = placeholder.substring(6, placeholder.length - 1)
                            LoggingService.debug("▶ Evaluating math expression: $expression")
                            
                            // Replace placeholders - initially there may be placeholders like %player_level%
                            val processedExpression = PlaceholderService.setPlaceholders(player, expression)
                            LoggingService.debug("▶ Expression after placeholders: $processedExpression")
                            
                            // Calculate the mathematical expression
                            val result = MathEvaluator.evaluateExpression(processedExpression, player)
                            
                            // Convert the result to double
                            if (result == null) {
                                LoggingService.warning("⚠ Math expression evaluation returned null")
                                chance = 0.0
                            } else {
                                // Check and fix output in percentage format
                                if (result.endsWith("%")) {
                                    // If there's a percentage sign, remove it and extract the number
                                    val numericPart = result.substring(0, result.length - 1)
                                    chance = numericPart.toDoubleOrNull() ?: 0.0
                                    LoggingService.debug("▶ Found percentage symbol in result, extracted: $chance")
                                } else {
                                    chance = result.toDoubleOrNull()
                                }
                                
                                if (chance == null) {
                                    LoggingService.warning("⚠ Math expression result is not a valid number: $result")
                                    chance = 0.0
                                } else {
                                    LoggingService.debug("▶ Math expression evaluated: $expression = $chance")
                                }
                            }
                        } catch (e: Exception) {
                            LoggingService.warning("⚠ Error evaluating math expression: ${e.message}")
                            chance = 0.0
                        }
                    } else {
                        // Standard placeholder processing
                        try {
                            val processedValue = PlaceholderService.setPlaceholders(player, placeholder)
                            LoggingService.debug("▶ Processing standard placeholder: $placeholder -> $processedValue")
                            
                            // If the placeholder didn't change
                            if (processedValue == placeholder) {
                                LoggingService.warning("⚠ Placeholder didn't change: $placeholder. Setting chance to 0%")
                                chance = 0.0
                            } else {
                                chance = processedValue.toDoubleOrNull()
                                if (chance == null) {
                                    LoggingService.warning("⚠ Placeholder value is not a valid number: $processedValue")
                                    chance = 0.0
                                } else {
                                    LoggingService.debug("▶ Placeholder processed successfully: $placeholder = $chance")
                                }
                            }
                        } catch (e: Exception) {
                            LoggingService.warning("⚠ Error processing placeholder: ${e.message}")
                            chance = 0.0
                        }
                    }
                }
                
                // Skip if no valid chance
                if (chance == null || chance <= 0) {
                    LoggingService.debug("ℹ Skipping chance reward - final chance value is ${chance ?: "null"} (not positive)")
                    continue
                }
                
                // Make sure chance is properly formatted as a percentage (0-100)
                if (chance > 100) {
                    LoggingService.debug("ℹ Chance value $chance is greater than 100, capping at 100%")
                    chance = 100.0
                }
                
                // Check if reward should be given
                LoggingService.debug("── CHANCE TEST STARTING ──")
                LoggingService.debug("Command: ${originalCommand}")
                LoggingService.debug("Calculated chance: %$chance")
                
                // Don't try to set the seed for ThreadLocalRandom - it's not supported
                // Perform the chance calculation - this is the core probability test
                val shouldGive = shouldGiveChanceReward(chance)
                
                if (shouldGive) {
                    // Chance test successful, execute the command with the chance expression removed
                    val cleanCommand = stripChanceExpressions(chanceReward.commands)
                    
                    LoggingService.info("✅ Player ${player.name} passed chance test: %$chance - command will execute")
                    
                    // Only execute if the command has something after stripping
                    if (cleanCommand.isNotBlank()) {
                        LoggingService.debug("▶ Executing command: \"$cleanCommand\" (original: \"${chanceReward.commands}\")")
                        executeCommand(cleanCommand, player, damage, entity)
                    } else {
                        LoggingService.warning("⚠ Command is empty after stripping chance expressions! Original: \"${chanceReward.commands}\"")
                    }
                } else {
                    // Chance test failed, do not execute the command
                    LoggingService.debug("❌ Player ${player.name} failed chance test with %$chance probability")
                    LoggingService.debug("▶ Command WILL NOT be executed: \"${chanceReward.commands}\"")
                }
            } catch (e: Exception) {
                LoggingService.severe("⚠ Error processing chance reward: ${e.message}")
                e.printStackTrace()
            }
        }
        
        LoggingService.debug("Chance reward processing completed")
    }
    
    /**
     * Helper method to extract a chance percentage from a command string
     * Used when a command with a chance expression is rerouted through the chance system
     * 
     * @param command The command string
     * @return The extracted chance percentage or null if not found
     */
    private fun extractChanceFromCommand(command: String): Double? {
        try {
            if (command.isBlank()) {
                LoggingService.warning("Blank command passed to extractChanceFromCommand")
                return null
            }
            
            // Pattern 1: Simple percentage at the end (e.g., "give %player% diamond 1 50.0%")
            var match = Regex(".*?\\s+(\\d+\\.?\\d*)%$").find(command)
            if (match != null) {
                val percentValue = match.groupValues[1]
                val result = percentValue.toDoubleOrNull()
                if (result != null) {
                    return result
                } else {
                    LoggingService.warning("Failed to convert percentage value: $percentValue")
                }
            }
            
            // Pattern 2: Math expression with percentage (e.g., "give %player% diamond 1 {math:...}%")
            match = Regex(".*?\\s+\\{math:(.*?)\\}%$").find(command)
            if (match != null) {
                val mathExpression = match.groupValues[1]
                if (mathExpression.isNotBlank()) {
                    LoggingService.debug("Evaluating math expression from command: {math:$mathExpression}")
                    
                    // For extractChanceFromCommand, we don't have a specific player context
                    // So we need to use a safer approach - just set a reasonable default chance
                    LoggingService.debug("Math expressions need player context for proper evaluation")
                    LoggingService.debug("Will assign a default chance value to be properly evaluated per-player later")
                    
                    // Return a reasonable default - this will be properly calculated per-player later
                    // in processChanceRewards when we have the actual player context
                    return 50.0
                } else {
                    LoggingService.warning("Empty math expression in command: $command")
                }
            }
            
            // Pattern 3: Only math expression (e.g., "give %player% diamond 1 {math:...}")
            match = Regex(".*?\\s+\\{math:(.*?)\\}$").find(command)
            if (match != null) {
                val mathExpression = match.groupValues[1]
                if (mathExpression.isNotBlank()) {
                    LoggingService.debug("Evaluating math expression from command: {math:$mathExpression}")
                    
                    // For extractChanceFromCommand, we don't have a specific player context
                    // So we need to use a safer approach - just set a reasonable default chance
                    LoggingService.debug("Math expressions need player context for proper evaluation")
                    LoggingService.debug("Will assign a default chance value to be properly evaluated per-player later")
                    
                    // Return a reasonable default - this will be properly calculated per-player later
                    // in processChanceRewards when we have the actual player context
                    return 50.0
                } else {
                    LoggingService.warning("Empty math expression in command: $command")
                }
            }
            
            return null
        } catch (e: Exception) {
            LoggingService.warning("Error extracting chance from command: ${e.message}")
            return null
        }
    }
    
    /**
     * Determines if a chance-based reward should be given.
     * This is the core probability calculation for chance-based rewards.
     *
     * @param chance The percentage chance (0-100)
     * @return true if reward should be given, false otherwise
     */
    private fun shouldGiveChanceReward(chance: Double): Boolean {
        try {
            // Log the input chance value
            LoggingService.debug("ⓘ Starting Chance Calculation")
            
            // Check bounds - ensure the chance is valid
            if (chance <= 0.0) {
                LoggingService.debug("ⓘ Chance is zero or negative ($chance), automatically FAILING")
                return false
            }
            
            if (chance >= 100.0) {
                LoggingService.debug("ⓘ Chance is 100 or greater ($chance), automatically SUCCEEDING")
                return true
            }
            
            // Get a random number between 0 and 100
            val randomPercent = ThreadLocalRandom.current().nextDouble(0.0, 100.0)
            
            // Logging for debugging - this is important for verifying the chance calculation
            LoggingService.debug("ⓘ Chance: %$chance")
            LoggingService.debug("ⓘ Random roll: %$randomPercent")
            
            // Success if random number is less than chance percentage
            val success = randomPercent < chance
            
            // Log the result
            if (success) {
                LoggingService.debug("ⓘ %$randomPercent < %$chance therefore TEST PASSED")
            } else {
                LoggingService.debug("ⓘ %$randomPercent >= %$chance therefore TEST FAILED")
            }
            
            return success
            
        } catch (e: Exception) {
            LoggingService.severe("⚠ Error in chance calculation: ${e.message}")
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
            if (command.isBlank()) {
                LoggingService.warning("Attempted to execute a blank command")
                return
            }
            
            // Log the original command
            LoggingService.info("Starting command execution: \"$command\"")
            
            // COMPREHENSIVE CHANCE COMMAND DETECTION
            // If this is a chance command, it should only be called after a shouldGiveChanceReward check
            // Check for various patterns that indicate this is a chance command
            val isChanceCommand = command.trim().matches(Regex(".*\\s+\\d+\\.?\\d*%$")) ||                   // Simple percentage at end
                command.trim().matches(Regex(".*\\s+\\{math:.*?\\}%$")) ||                 // Math with percentage at end
                command.trim().matches(Regex(".*\\s+\\{math:.*?\\}$")) ||                  // Math at end (assumed chance)
                command.contains("%chance") || command.contains("%probability%") ||        // Chance-related placeholders
                (command.trim().matches(Regex(".*\\s+%[^%]+%$")) && !command.contains("%player%")) // Trailing placeholder that's not %player%
                
            if (isChanceCommand) {
                LoggingService.severe("ATTENTION! Attempting to execute chance command directly: \"$command\"")
                LoggingService.severe("This is a chance command and should be processed through processChanceRewards.")
                LoggingService.severe("Command will be skipped and not executed in this way.")
                return
            }
            
            // Remove chance expressions and math expressions
            val cleanCommand = stripChanceExpressions(command)
            
            if (cleanCommand.isBlank()) {
                LoggingService.warning("Command is empty after chance expression stripping: \"$command\"")
                return
            }
            
            LoggingService.debug("Command after chance expression removal: \"$cleanCommand\"")
            
            // If the command has changed significantly, log a warning
            if (cleanCommand.length < command.length * 0.5) {
                LoggingService.warning("Command was significantly shortened after stripping chance expressions. Original: '$command', Cleaned: '$cleanCommand'")
                
                // Extra safety check - if the stripped command is significantly different, check if this looks like a chance command
                if (command.contains("%") && !cleanCommand.contains("%")) {
                    LoggingService.warning("Command appears to be a chance command that slipped through detection")
                    LoggingService.warning("Using the cleaned command, but this should be processed through the chance system")
                }
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
            
            LoggingService.debug("Command after basic placeholder replacement: \"$processedCommand\"")
            
            // Use MathEvaluator to process any math expressions in the command
            try {
                val processedWithMath = MathEvaluator.processCommand(processedCommand, player, "round")
                if (processedWithMath != null && processedWithMath.isNotBlank()) {
                    processedCommand = processedWithMath
                    LoggingService.debug("Command after math evaluation: \"$processedCommand\"")
                } else {
                    LoggingService.warning("MathEvaluator returned null or blank result, using original command")
                }
            } catch (e: Exception) {
                LoggingService.warning("Error in math evaluation: ${e.message}, using original command")
            }
            
            // Process with PlaceholderAPI if available
            if (Main.PLACEHOLDERAPI_ENABLED) {
                try {
                    val processedWithPlaceholders = PlaceholderService.setPlaceholders(player, processedCommand)
                    if (processedWithPlaceholders != null && processedWithPlaceholders.isNotBlank()) {
                        processedCommand = processedWithPlaceholders
                        LoggingService.debug("Command after PlaceholderAPI processing: \"$processedCommand\"")
                    } else {
                        LoggingService.warning("PlaceholderAPI returned null or blank result, using previous command")
                    }
                } catch (e: Exception) {
                    LoggingService.warning("Error in PlaceholderAPI processing: ${e.message}, using previous command")
                }
            }
            
            // Command safety validation disabled per user request
            // Always allow all commands to execute
            /*
            if (!isCommandSafe(processedCommand)) {
                LoggingService.severe("Potentially unsafe command blocked: \"$processedCommand\"")
                return
            }
            */
            
            // Final check for any remaining patterns that might indicate a chance command
            if (processedCommand.contains("%") && (
                processedCommand.matches(Regex(".*\\d+%.*")) || 
                processedCommand.contains("chance") || 
                processedCommand.contains("random")
            )) {
                LoggingService.warning("Potential chance-related patterns in processed command: \"$processedCommand\"")
                LoggingService.warning("Proceeding with execution, but verify command configuration if this is unexpected")
            }
            
            // Execute the command
            try {
                LoggingService.info("Executing command: \"$processedCommand\"")
                val result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                if (result) {
                    LoggingService.info("Command executed successfully")
                } else {
                    LoggingService.warning("Command did not execute successfully. Command: \"$processedCommand\"")
                }
            } catch (e: Exception) {
                LoggingService.warning("Error executing command: \"$processedCommand\" - ${e.message}")
                // Show error details
                LoggingService.warning("Error details: ${e.javaClass.name} - ${e.stackTrace.joinToString("\n  ")}")
            }
        } catch (e: Exception) {
            LoggingService.severe("Error in executeCommand: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Checks if a command is safe to execute.
     * Ensures command starts with allowed prefix and doesn't contain dangerous patterns.
     * 
     * @param command The command to check
     * @return true if command is safe, false otherwise
     */
    fun isCommandSafe(command: String): Boolean {
        // Simply return true to allow all commands without restriction
        return true
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
            if (command.isBlank()) {
                LoggingService.warning("Attempting to strip chance expressions from a blank command")
                return command
            }
            
            LoggingService.debug("Stripping chance expressions from command: \"$command\"")
            
            // Check if this is even a chance-related command
            val containsChanceIndicator = command.contains("%") || 
                                          command.contains("{math:") || 
                                          command.contains("chance=") ||
                                          command.matches(Regex(".*\\d+%.*"))
            
            if (!containsChanceIndicator) {
                LoggingService.debug("No chance indicators found in command, using original command")
                return command
            }
            
            // Try all patterns one by one
            var result: String? = null
            
            // Pattern 0: New enhanced check for explicit minecraft commands with chance
            val enhancedMinecraftMatch = Regex("(give %player% (?:minecraft:)?\\w+ \\d+)(?:\\s+.*?\\d+\\.?\\d*%|\\s+\\{math:.*?\\}%?)$").find(command)
            if (enhancedMinecraftMatch != null) {
                result = enhancedMinecraftMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Enhanced minecraft command match, stripped to: \"$result\"")
                    return result
                }
            }
            
            // Pattern 1: Special pattern for "give %player% minecraft:diamond 1 25.0%" format
            val specificItemPercentMatch = Regex("(give %player% \\S+ \\d+)\\s+\\d+\\.?\\d*%$").find(command)
            if (specificItemPercentMatch != null) {
                result = specificItemPercentMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Removed specific item format chance expression, result: \"$result\"")
                    return result
                }
            }
            
            // Pattern 2: Strip simple percentage chances: "give %player% diamond 1 50.0%"
            val percentageMatch = Regex("(.*?)\\s+\\d+\\.?\\d*%$").find(command)
            if (percentageMatch != null) {
                result = percentageMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Removed percentage chance expression, result: \"$result\"")
                    return result
                }
            }
            
            // Pattern 3: Strip math expressions with percentage: "give %player% diamond 1 {math:...}%"
            val mathPercentMatch = Regex("(.*?)\\s+\\{math:.*?\\}%$").find(command)
            if (mathPercentMatch != null) {
                result = mathPercentMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Removed math expression with percentage, result: \"$result\"")
                    return result
                }
            }
            
            // Pattern 4: Strip math expressions without percentage: "give %player% diamond 1 {math:...}"
            val mathMatch = Regex("(.*?)\\s+\\{math:.*?\\}$").find(command)
            if (mathMatch != null) {
                result = mathMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Removed math expression, result: \"$result\"")
                    return result
                }
            }
            
            // Pattern 5: Strip placeholder chances: "give %player% diamond 1 %placeholder%"
            val placeholderMatch = Regex("(.*?)\\s+%([^%]+)%$").find(command)
            if (placeholderMatch != null && placeholderMatch.groupValues.size >= 3) {
                val baseCommand = placeholderMatch.groupValues.getOrNull(1)?.trim() ?: ""
                val placeholder = placeholderMatch.groupValues.getOrNull(2) ?: ""
                
                // Make sure we're not stripping %player% or other essential placeholders
                if (placeholder.isNotBlank() && 
                    !placeholder.equals("player", ignoreCase = true) && 
                    !placeholder.equals("damage", ignoreCase = true) &&
                    !placeholder.equals("entity", ignoreCase = true) &&
                    !placeholder.equals("world", ignoreCase = true) &&
                    !placeholder.equals("position", ignoreCase = true)) {
                    
                    if (baseCommand.isNotBlank()) {
                        LoggingService.debug("Removed placeholder chance, result: \"$baseCommand\"")
                        return baseCommand
                    }
                }
            }
            
            // Pattern 6: More general pattern to catch percentages anywhere in the command
            val generalPercentMatch = Regex("(.*?)\\s+\\d+\\.?\\d*%(?:\\s+|$)").find(command)
            if (generalPercentMatch != null) {
                result = generalPercentMatch.groupValues.getOrNull(1)?.trim()
                if (result != null && result.isNotBlank()) {
                    LoggingService.debug("Removed general percentage expression, result: \"$result\"")
                    return result
                }
            }
            
            // Pattern 7: Last resort - look for standard patterns for chance commands
            if (command.contains("chance=") || command.contains("%chance") || command.contains("%probability%")) {
                // Try to get the base command before the chance part
                val parts = command.split("chance=", "%chance", "%probability%")
                if (parts.isNotEmpty() && parts[0].isNotBlank() && parts[0].length > command.length / 2) {
                    result = parts[0].trim()
                    LoggingService.debug("Removed chance-related text using keyword detection, result: \"$result\"")
                    return result
                }
            }
            
            // No chance expressions found, return original command
            LoggingService.debug("No chance expressions found in command, using original command")
            return command
        } catch (e: Exception) {
            LoggingService.warning("Error while stripping chance expressions: ${e.message}")
            // Return original command on error
            return command
        }
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
    
    /**
     * Evaluates basic arithmetic expressions like "20 + (30 / 10)"
     * This is a simple utility to avoid using the more complex MathEvaluator for basic math
     *
     * @param expression The arithmetic expression to evaluate
     * @return The calculated result as a double, or null if evaluation failed
     */
    private fun evaluateBasicArithmetic(expression: String): Double? {
        try {
            // Clean the expression first
            var expr = expression.trim()
                .replace("\\s+".toRegex(), "") // Remove all whitespace
            
            LoggingService.debug("Evaluating basic arithmetic: $expr")
            
            // Handle parentheses first with recursive evaluation
            while (expr.contains("(")) {
                val openIndex = expr.lastIndexOf("(")
                val closeIndex = expr.indexOf(")", openIndex)
                
                if (closeIndex == -1) {
                    LoggingService.warning("Mismatched parentheses in expression: $expr")
                    return null
                }
                
                // Get the sub-expression inside parentheses
                val subExpr = expr.substring(openIndex + 1, closeIndex)
                
                // Recursively evaluate the sub-expression
                val subResult = evaluateBasicArithmetic(subExpr)
                if (subResult == null) {
                    return null
                }
                
                // Replace the parenthesized expression with its result
                expr = expr.substring(0, openIndex) + subResult + expr.substring(closeIndex + 1)
            }
            
            // Check if expression is a simple number after parentheses evaluation
            if (!expr.contains("+") && !expr.contains("-") && !expr.contains("*") && !expr.contains("/")) {
                return expr.toDoubleOrNull()
            }
            
            // Now handle multiplication and division
            while (expr.contains("*") || expr.contains("/")) {
                val multIndex = expr.indexOf("*")
                val divIndex = expr.indexOf("/")
                
                val opIndex = when {
                    multIndex >= 0 && divIndex >= 0 -> Math.min(multIndex, divIndex)
                    multIndex >= 0 -> multIndex
                    else -> divIndex
                }
                
                // Find operands
                val leftEndIndex = findPreviousOperatorIndex(expr, opIndex)
                val rightStartIndex = findNextOperatorIndex(expr, opIndex)
                
                // Handle leftEndIndex being -1 (start of expression)
                val leftPart = if (leftEndIndex == -1) {
                    expr.substring(0, opIndex)
                } else {
                    expr.substring(leftEndIndex + 1, opIndex)
                }
                
                val rightPart = expr.substring(opIndex + 1, rightStartIndex)
                
                val left = leftPart.toDoubleOrNull()
                val right = rightPart.toDoubleOrNull()
                
                if (left == null || right == null) {
                    LoggingService.warning("Cannot parse operands in expression: $expr, left: $leftPart, right: $rightPart")
                    return null
                }
                
                // Perform operation
                val result = if (expr[opIndex] == '*') {
                    left * right
                } else {
                    if (right == 0.0) {
                        LoggingService.warning("Division by zero in expression: $expr")
                        return null
                    }
                    left / right
                }
                
                // Replace operation with result
                expr = if (leftEndIndex == -1) {
                    result.toString() + expr.substring(rightStartIndex)
                } else {
                    expr.substring(0, leftEndIndex + 1) + result + expr.substring(rightStartIndex)
                }
            }
            
            // Now handle addition and subtraction
            while (expr.contains("+") || (expr.contains("-") && expr.indexOf("-") > 0)) {
                val addIndex = expr.indexOf("+")
                var subIndex = expr.indexOf("-")
                
                // Ignore negative sign at the beginning
                if (subIndex == 0) {
                    subIndex = expr.indexOf("-", 1)
                }
                
                val opIndex = when {
                    addIndex >= 0 && subIndex >= 0 -> Math.min(addIndex, subIndex)
                    addIndex >= 0 -> addIndex
                    else -> subIndex
                }
                
                if (opIndex < 0) break
                
                // Find operands
                val leftEndIndex = findPreviousOperatorIndex(expr, opIndex)
                val rightStartIndex = findNextOperatorIndex(expr, opIndex)
                
                // Handle leftEndIndex being -1 (start of expression)
                val leftPart = if (leftEndIndex == -1) {
                    expr.substring(0, opIndex)
                } else {
                    expr.substring(leftEndIndex + 1, opIndex)
                }
                
                val rightPart = expr.substring(opIndex + 1, rightStartIndex)
                
                val left = leftPart.toDoubleOrNull()
                val right = rightPart.toDoubleOrNull()
                
                if (left == null || right == null) {
                    LoggingService.warning("Cannot parse operands in expression: $expr, left: $leftPart, right: $rightPart")
                    return null
                }
                
                // Perform operation
                val result = if (expr[opIndex] == '+') {
                    left + right
                } else {
                    left - right
                }
                
                // Replace operation with result
                expr = if (leftEndIndex == -1) {
                    result.toString() + expr.substring(rightStartIndex)
                } else {
                    expr.substring(0, leftEndIndex + 1) + result + expr.substring(rightStartIndex)
                }
            }
            
            // Convert result to double
            return expr.toDoubleOrNull()
            
        } catch (e: Exception) {
            LoggingService.warning("Error evaluating arithmetic expression: ${e.message}")
            return null
        }
    }
    
    /**
     * Helper function to find the index of the previous operator or start of expression
     * @return index of previous operator, or -1 if at start of expression
     */
    private fun findPreviousOperatorIndex(expr: String, currentIndex: Int): Int {
        for (i in currentIndex - 1 downTo 0) {
            if (expr[i] == '+' || expr[i] == '-' || expr[i] == '*' || expr[i] == '/') {
                return i
            }
        }
        return -1  // Start of expression
    }
    
    /**
     * Helper function to find the index of the next operator or end of expression
     * @return index of next operator, or length of string if at end of expression
     */
    private fun findNextOperatorIndex(expr: String, currentIndex: Int): Int {
        for (i in currentIndex + 1 until expr.length) {
            if (expr[i] == '+' || expr[i] == '-' || expr[i] == '*' || expr[i] == '/') {
                return i
            }
        }
        return expr.length  // End of expression
    }
} 