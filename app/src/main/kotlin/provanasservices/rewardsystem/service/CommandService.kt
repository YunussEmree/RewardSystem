package provanasservices.rewardsystem.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import provanasservices.rewardsystem.util.MathEvaluator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern

/**
 * Service class that manages command execution operations.
 * Parses commands, checks permissions, and executes them.
 */
public object CommandService {
    /**
     * Storage for math expressions to be evaluated later as chance percentages
     */
    private val mathChanceExpressions = ConcurrentHashMap<String, String>()

    // Pattern for detecting chance-based commands (e.g., "command 50%")
    private val chancePattern = Pattern.compile("(.*?)\\s+(\\d+\\.?\\d*)%$")

    // Pattern for detecting math expressions in chance commands (e.g., "command {math:1+1}%")
    private val mathChancePattern = Pattern.compile("(.*?)\\s+\\{math:(.*?)\\}%$")

    /**
     * Processes a command and evaluates necessary placeholders and mathematical expressions.
     *
     * @param command Command to be processed
     * @param player Player
     * @param damage Damage value
     * @param roundingMode Rounding mode for mathematical operations
     * @return Processed command
     */
    fun processCommand(command: String, player: Player, damage: Double, roundingMode: String): String {
        // Replace basic placeholders - format damage as integer
        val basicReplaced = command
            .replace("%player%", player.name)
            .replace("%damage%", damage.toInt().toString())

        // Process with PlaceholderAPI additionally
        val processedCmd = PlaceholderService.setBracketPlaceholders(player, basicReplaced)

        // Process mathematical expressions
        return MathEvaluator.processCommand(processedCmd, player, roundingMode)
    }

    /**
     * Evaluates a mathematical expression for chance calculations.
     *
     * @param command The command that contains a stored math expression
     * @param player Player for placeholder context
     * @param roundingMode Rounding mode to use
     * @return The calculated chance value or 0.0 if evaluation fails
     */
    fun evaluateChanceExpression(command: String, player: Player, roundingMode: String = "none"): Double {
        val expression = mathChanceExpressions[command] ?: return 0.0

        LoggingService.debug("Evaluating chance expression: $expression for player ${player.name}")
        println("Evaluating chance expression: $expression for player ${player.name}")
        val processedExpression = MathEvaluator.evaluateExpression(expression, player, roundingMode)

        return try {
            val result = processedExpression.toDouble()
            LoggingService.debug("Chance calculation result: $result% (from expression: $expression)")
            result
        } catch (e: NumberFormatException) {
            LoggingService.debug("Failed to convert math result to chance: $processedExpression")
            0.0
        }
    }

    /**
     * Executes a command safely and checks necessary permissions.
     *
     * @param player Player in the context for running the command
     * @param command Command to be executed
     * @param permission Required permission (null if no permission needed)
     * @param debug Debug mode status
     * @return true if permission check is successful, false otherwise
     */
    fun executeCommand(player: Player, command: String, permission: String?, debug: Boolean): Boolean {
        // Permission check
        if (permission != null && !player.hasPermission(permission)) {
            if (debug) {
                LoggingService.debug("Player ${player.name} doesn't have permission: $permission for command: $command")
            }
            return false
        }
        // Execute command
        if (debug) {
            LoggingService.debug("Plugin dispatched command: $command")
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        return true
    }

    /**
     * Parses a command string and processes chance-based commands.
     * If the command includes a percentage chance, it will randomly determine
     * if the command should execute based on that chance.
     *
     * @param command The command string to parse
     * @param player The player associated with the command (for math evaluation context)
     * @return The processed command, or null if the chance check failed
     */
    fun parseCommandWithChance(command: String, player: Player): String? {
        // First check if it's a math-based chance command
        val mathMatcher = mathChancePattern.matcher(command)
        if (mathMatcher.find()) {
            val baseCommand = mathMatcher.group(1).trim()
            val mathExpression = mathMatcher.group(2).trim()

            LoggingService.debug("Found math-based chance command: $baseCommand with expression {math:$mathExpression}%")

            // Store the math expression for evaluation
            val commandId = System.nanoTime().toString()
            mathChanceExpressions[commandId] = mathExpression

            // Evaluate the expression to get the chance percentage
            val chancePercentage = evaluateChanceExpression(commandId, player)

            // Randomly determine if command should run based on chance
            val shouldRun = chancePercentage > 0 && shouldExecute(chancePercentage)

            // Clean up stored expression
            mathChanceExpressions.remove(commandId)

            if (!shouldRun) {
                // Chance check failed
                LoggingService.debug("Chance check failed for command: $baseCommand (${chancePercentage}%)")
                return null
            }

            // Chance check passed, return the base command
            LoggingService.debug("Chance check passed for command: $baseCommand (${chancePercentage}%)")
            return baseCommand
        }

        // Check if it's a standard chance command
        val matcher = chancePattern.matcher(command)
        if (matcher.find()) {
            val baseCommand = matcher.group(1).trim()
            val chancePercentage = matcher.group(2).toDouble()

            LoggingService.debug("Found standard chance command: $baseCommand with chance $chancePercentage%")

            // Randomly determine if command should run based on chance
            val shouldRun = chancePercentage > 0 && shouldExecute(chancePercentage)

            if (!shouldRun) {
                // Chance check failed
                LoggingService.debug("Chance check failed for command: $baseCommand (${chancePercentage}%)")
                return null
            }

            // Chance check passed, return the base command
            LoggingService.debug("Chance check passed for command: $baseCommand (${chancePercentage}%)")
            return baseCommand
        }

        // Not a chance-based command, return as is
        return command
    }

    /**
     * Determines if a command should execute based on a chance percentage.
     *
     * @param chancePercentage The percentage chance (0-100) of execution
     * @return true if command should execute, false otherwise
     */
    private fun shouldExecute(chancePercentage: Double): Boolean {
        // Generate a random number between 0 and 100
        val randomValue = ThreadLocalRandom.current().nextDouble(100.0)

        // Log the values for debugging
        LoggingService.debug("Chance roll: $randomValue vs threshold: $chancePercentage")

        // Return true if the random value is less than the chance percentage
        return randomValue < chancePercentage
    }
}
