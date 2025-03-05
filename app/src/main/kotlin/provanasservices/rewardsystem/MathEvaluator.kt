package provanasservices.rewardsystem

import javax.script.ScriptEngineManager
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.ceil
import org.bukkit.entity.Player
import me.clip.placeholderapi.PlaceholderAPI
import provanasservices.rewardsystem.Main.Companion.PLACEHOLDERAPI_ENABLED

/**
 * Helper class for evaluating mathematical expressions.
 * Processes mathematical expressions and placeholders in commands.
 */
class MathEvaluator {
    companion object {
        // Regex to identify math expressions
        private val MATH_PATTERN = "\\{math:(.*?)\\}".toRegex()
        
        // JavaScript engine
        private val scriptEngine = ScriptEngineManager().getEngineByName("JavaScript")
        
        /**
         * Processes all mathematical expressions in the given command.
         * Example: "give %player% diamond {math:2*%island.level%+5}"
         * 
         * @param command Command to process
         * @param player Player for placeholder values
         * @param roundingMode Rounding mode: "none", "floor", "ceil", "round"
         * @return Processed command
         */
        fun processCommand(command: String, player: Player?, roundingMode: String = "floor"): String {
            if (!command.contains("{math:")) return command
            
            var result = command
            val matches = MATH_PATTERN.findAll(result)
            
            for (match in matches) {
                val fullMatch = match.value
                val expression = match.groupValues[1]
                
                // Evaluate the expression
                val evaluatedValue = evaluateExpression(expression, player, roundingMode)
                
                // Replace in command
                result = result.replace(fullMatch, evaluatedValue)
            }
            
            return result
        }
        
        /**
         * Evaluates a mathematical expression.
         * 
         * @param expression Mathematical expression
         * @param player Player for placeholder values
         * @param roundingMode Rounding mode
         * @return Evaluated result
         */
        private fun evaluateExpression(expression: String, player: Player?, roundingMode: String): String {
            var processedExpression = expression
            
            if (player != null && PLACEHOLDERAPI_ENABLED) {
                val placeholderPattern = "%([^%]+)%".toRegex()
                val placeholders = placeholderPattern.findAll(expression)
                
                for (placeholder in placeholders) {
                    val fullPlaceholder = placeholder.value
                    val placeholderValue = PlaceholderAPI.setPlaceholders(player, fullPlaceholder)
                    
                    val numericValue = placeholderValue.replace("[^0-9.-]".toRegex(), "")
                    val safeValue = if (numericValue.isEmpty()) "0" else numericValue
                    
                    processedExpression = processedExpression.replace(fullPlaceholder, safeValue)
                }
            }
            
            try {
                val result = scriptEngine.eval(processedExpression) as? Number ?: 0.0
                
                val roundedValue = when (roundingMode.lowercase()) {
                    "floor" -> floor(result.toDouble())
                    "ceil" -> ceil(result.toDouble())
                    "round" -> round(result.toDouble())
                    else -> result.toDouble()
                }
                
                return if (roundedValue == roundedValue.toLong().toDouble()) {
                    roundedValue.toLong().toString()
                } else {
                    roundedValue.toString()
                }
            } catch (e: Exception) {
                Main.getInstance().logger.warning("Math evaluation error: ${e.message} for expression: $expression")
                return "0"
            }
        }
    }
} 