package provanasservices.rewardsystem.util

import kotlin.math.floor
import kotlin.math.round
import kotlin.math.ceil
import org.bukkit.entity.Player
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.service.PlaceholderService
import java.util.*

/**
 * Utility class for evaluating mathematical expressions in commands.
 * Handles expressions in the format {math:...} and supports placeholders.
 */
object MathEvaluator {
    // Pattern to match math expressions in the format {math:...}
    private val MATH_PATTERN = "\\{math:(.*?)\\}".toRegex()
    
    // Safe operators and functions
    private val ALLOWED_OPERATORS = setOf("+", "-", "*", "/", "%", "(", ")", "^")
    private val ALLOWED_FUNCTIONS = setOf("min", "max", "abs", "sqrt", "pow", "sin", "cos", "tan")
    
    /**
     * Processes a command string and evaluates any math expressions in the format {math:...}
     * 
     * @param command The command string to process
     * @param player The player context for placeholders
     * @param roundingMode The rounding mode to use ("floor", "ceil", "round", or "none")
     * @return The processed command string with math expressions evaluated
     */
    fun processCommand(command: String, player: Player, roundingMode: String): String {
        // If no math expressions present, return the original command
        if (!command.contains("{math:")) return command
        
        LoggingService.info("Processing command with math expressions: $command")
        
        // Find and process each math expression
        val result = command.replace(MATH_PATTERN) { matchResult ->
            val expression = matchResult.groupValues[1]
            try {
                LoggingService.info("Found math expression: {math:$expression}")
                val evaluated = evaluateExpression(expression, player, roundingMode)
                LoggingService.info("Evaluated result: $evaluated")
                evaluated
            } catch (e: Exception) {
                // If evaluation fails, return 0
                LoggingService.warning("Math evaluation failed: ${e.message} for expression: $expression")
                "0"
            }
        }
        
        LoggingService.info("Final command after math processing: $result")
        return result
    }
    
    /**
     * Evaluates a mathematical expression with placeholder support.
     * 
     * @param expression The expression to evaluate
     * @param player The player context for placeholders
     * @param roundingMode The rounding mode to use
     * @return The result as a String
     */
    fun evaluateExpression(expression: String, player: Player, roundingMode: String = "none"): String {
        try {
            LoggingService.info("Evaluating math expression: $expression")
            
            // Process placeholders in the expression
            val processedExpr = processMathPlaceholders(expression, player, roundingMode)
            LoggingService.info("After placeholder processing: $processedExpr")
            
            // Validate the expression for security
            if (!isExpressionSafe(processedExpr)) {
                LoggingService.warning("Potentially unsafe math expression rejected: $processedExpr")
                return "0"
            }
            
            // Evaluate the expression using safe parser
            val calculatedResult = try {
                evaluateSafely(processedExpr)
            } catch (e: Exception) {
                LoggingService.warning("Math expression error: ${e.message} in expression: $processedExpr")
                0.0
            }
            
            LoggingService.info("Expression result: $calculatedResult")
            
            // Apply rounding based on config
            val finalResult = applyRounding(calculatedResult, roundingMode)
            LoggingService.info("Final result after rounding: $finalResult")
            
            return finalResult
        } catch (e: Exception) {
            LoggingService.severe("Exception in evaluateExpression: ${e.message}")
            e.printStackTrace()
            return "0"
        }
    }
    
    /**
     * Checks if an expression is safe to evaluate.
     * Only allows basic math operations and approved functions.
     *
     * @param expression The expression to check
     * @return true if the expression is safe, false otherwise
     */
    private fun isExpressionSafe(expression: String): Boolean {
        try {
            // Normalize the expression by removing spaces
            val normalizedExpr = expression.replace(" ", "")
            
            // Tokenize the expression
            val tokens = tokenizeExpression(normalizedExpr)
            
            // Check each token for safety
            for (token in tokens) {
                if (token.first == TokenType.FUNCTION && !ALLOWED_FUNCTIONS.contains(token.second.lowercase())) {
                    LoggingService.warning("Unauthorized function in expression: ${token.second}")
                    return false
                }
                if (token.first == TokenType.OPERATOR && !ALLOWED_OPERATORS.contains(token.second)) {
                    LoggingService.warning("Unauthorized operator in expression: ${token.second}")
                    return false
                }
                if (token.first == TokenType.UNKNOWN) {
                    LoggingService.warning("Unknown token in expression: ${token.second}")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            LoggingService.warning("Error validating expression: ${e.message}")
            return false
        }
    }
    
    /**
     * Token types for expression validation
     */
    private enum class TokenType {
        NUMBER, OPERATOR, FUNCTION, UNKNOWN
    }
    
    /**
     * Tokenizes a mathematical expression into manageable parts for validation.
     *
     * @param expr The expression to tokenize
     * @return List of token type and value pairs
     */
    private fun tokenizeExpression(expr: String): List<Pair<TokenType, String>> {
        val tokens = mutableListOf<Pair<TokenType, String>>()
        var i = 0
        
        while (i < expr.length) {
            val c = expr[i]
            
            // Check for numbers
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    i++
                }
                tokens.add(Pair(TokenType.NUMBER, expr.substring(start, i)))
                continue
            }
            
            // Check for operators
            if (ALLOWED_OPERATORS.contains(c.toString())) {
                tokens.add(Pair(TokenType.OPERATOR, c.toString()))
                i++
                continue
            }
            
            // Check for functions
            if (c.isLetter()) {
                val start = i
                while (i < expr.length && expr[i].isLetter()) {
                    i++
                }
                val funcName = expr.substring(start, i)
                tokens.add(Pair(TokenType.FUNCTION, funcName))
                continue
            }
            
            // Skip commas (used in function arguments)
            if (c == ',') {
                i++
                continue
            }
            
            // Unknown token
            tokens.add(Pair(TokenType.UNKNOWN, c.toString()))
            i++
        }
        
        return tokens
    }
    
    /**
     * Safely evaluates a mathematical expression using a recursive descent parser.
     *
     * @param expression The expression to evaluate
     * @return The result of the evaluation
     */
    private fun evaluateSafely(expression: String): Double {
        return ExpressionParser(expression).parse()
    }
    
    /**
     * Processes placeholders in a mathematical expression.
     * 
     * @param expression The expression containing placeholders
     * @param player The player context for placeholder resolution
     * @return The expression with placeholders replaced by their values
     */
    private fun processMathPlaceholders(expression: String, player: Player, roundingMode: String = "none"): String {
        LoggingService.info("Processing math placeholders in expression: $expression")
        
        val processedCommand = StringBuilder(expression)
        
        // First process PlaceholderAPI bracket placeholders
        val bracketRegex = Regex("\\{([^{}]+)\\}")
        processPlaceholderMatches(bracketRegex, processedCommand, player, roundingMode)
        
        // Then process standard placeholders
        val percentRegex = Regex("%([^%]+)%")
        processPlaceholderMatches(percentRegex, processedCommand, player, roundingMode)
        
        // State after placeholders processed
        LoggingService.info("Final processed expression: $processedCommand")
        return processedCommand.toString()
    }
    
    private fun processPlaceholderMatches(regex: Regex, stringBuilder: StringBuilder, player: Player, roundingMode: String) {
        var offset = 0
        regex.findAll(stringBuilder.toString()).forEach { matchResult ->
            val originalMatch = matchResult.value
            val placeholder = matchResult.groupValues[1]
            
            // If the placeholder didn't change or isn't a numeric value, use 0
            val processedValue = if (Main.PLACEHOLDERAPI_ENABLED) {
                PlaceholderService.setPlaceholders(player, originalMatch)
            } else {
                originalMatch
            }
            
            if (processedValue != originalMatch) {
                try {
                    val numericValue = processedValue.toDouble()
                    val roundedValue = applyRounding(numericValue, roundingMode)
                    
                    // State after placeholders processed
                    stringBuilder.replace(
                        matchResult.range.first + offset,
                        matchResult.range.last + 1 + offset,
                        roundedValue
                    )
                    offset += roundedValue.length - originalMatch.length
                } catch (e: NumberFormatException) {
                    // Keep the original if it's not a number
                    // No replacement needed
                }
            }
        }
    }
    
    /**
     * Applies the specified rounding mode to a number.
     * 
     * @param value The value to round
     * @param roundingMode The rounding mode ("floor", "ceil", "round", or "none")
     * @return The rounded value as a string
     */
    private fun applyRounding(value: Double, roundingMode: String): String {
        return when (roundingMode.lowercase()) {
            "floor" -> floor(value).toInt().toString()
            "ceil" -> ceil(value).toInt().toString()
            "round" -> round(value).toInt().toString()
            else -> value.toString()
        }
    }
    
    /**
     * Simple recursive descent parser for mathematical expressions.
     * Only handles basic arithmetic operations and a few math functions.
     */
    private class ExpressionParser(private val expression: String) {
        private var pos = 0
        private val chars = expression.toCharArray()
        
        fun parse(): Double {
            val result = parseExpression()
            if (pos < chars.size) {
                throw IllegalArgumentException("Unexpected character: ${chars[pos]}")
            }
            return result
        }
        
        private fun parseExpression(): Double {
            var result = parseTerm()
            
            while (pos < chars.size) {
                when (chars[pos]) {
                    '+' -> {
                        pos++
                        result += parseTerm()
                    }
                    '-' -> {
                        pos++
                        result -= parseTerm()
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseTerm(): Double {
            var result = parseFactor()
            
            while (pos < chars.size) {
                when (chars[pos]) {
                    '*' -> {
                        pos++
                        result *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) {
                            throw ArithmeticException("Division by zero")
                        }
                        result /= divisor
                    }
                    '%' -> {
                        pos++
                        result %= parseFactor()
                    }
                    '^' -> {
                        pos++
                        result = Math.pow(result, parseFactor())
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseFactor(): Double {
            skipWhitespace()
            
            // Check for functions
            val funcStart = pos
            while (pos < chars.size && chars[pos].isLetter()) {
                pos++
            }
            
            if (pos > funcStart) {
                val function = expression.substring(funcStart, pos).lowercase()
                if (ALLOWED_FUNCTIONS.contains(function)) {
                    skipWhitespace()
                    if (pos < chars.size && chars[pos] == '(') {
                        pos++ // Skip '('
                        val arg = parseExpression()
                        skipWhitespace()
                        
                        // For functions with two arguments
                        var arg2: Double? = null
                        if (pos < chars.size && chars[pos] == ',') {
                            pos++ // Skip ','
                            skipWhitespace()
                            arg2 = parseExpression()
                            skipWhitespace()
                        }
                        
                        if (pos < chars.size && chars[pos] == ')') {
                            pos++ // Skip ')'
                            
                            return when (function) {
                                "sin" -> Math.sin(Math.toRadians(arg))
                                "cos" -> Math.cos(Math.toRadians(arg))
                                "tan" -> Math.tan(Math.toRadians(arg))
                                "sqrt" -> Math.sqrt(arg)
                                "abs" -> Math.abs(arg)
                                "pow" -> if (arg2 != null) Math.pow(arg, arg2) else arg
                                "min" -> if (arg2 != null) Math.min(arg, arg2) else arg
                                "max" -> if (arg2 != null) Math.max(arg, arg2) else arg
                                else -> throw IllegalArgumentException("Unknown function: $function")
                            }
                        } else {
                            throw IllegalArgumentException("Missing closing parenthesis for function: $function")
                        }
                    }
                }
                
                // Not a function or invalid function syntax, revert position
                pos = funcStart
            }
            
            // Handle parentheses
            if (pos < chars.size && chars[pos] == '(') {
                pos++ // Skip '('
                val result = parseExpression()
                skipWhitespace()
                
                if (pos < chars.size && chars[pos] == ')') {
                    pos++ // Skip ')'
                    return result
                } else {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
            }
            
            // Handle negative numbers
            if (pos < chars.size && chars[pos] == '-') {
                pos++
                return -parseFactor()
            }
            
            // Parse numbers
            return parseNumber()
        }
        
        private fun parseNumber(): Double {
            skipWhitespace()
            
            val start = pos
            var hasDecimal = false
            
            while (pos < chars.size && (chars[pos].isDigit() || chars[pos] == '.')) {
                if (chars[pos] == '.') {
                    if (hasDecimal) {
                        throw IllegalArgumentException("Invalid number format: multiple decimal points")
                    }
                    hasDecimal = true
                }
                pos++
            }
            
            if (start == pos) {
                throw IllegalArgumentException("Expected number at position $pos")
            }
            
            return expression.substring(start, pos).toDouble()
        }
        
        private fun skipWhitespace() {
            while (pos < chars.size && chars[pos].isWhitespace()) {
                pos++
            }
        }
    }
} 