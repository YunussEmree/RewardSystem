package provanasservices.rewardsystem.util

import org.bukkit.entity.Player
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.service.PlaceholderService
import java.util.*
import kotlin.math.*

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
     * Simple method to test math evaluation directly without needing a Player object.
     * This is for testing purposes only.
     */
    fun testEvaluateExpression(expression: String): String {
        try {
            // Simplified version that skips placeholder processing
            println("Test evaluating expression: $expression")
            
            // Skip processing placeholders
            
            // Check if we're dealing with a simple numeric value
            val asDirectNumber = expression.toDoubleOrNull()
            if (asDirectNumber != null) {
                // The expression is already a simple number, just return it
                return asDirectNumber.toString()
            }
            
            // Validate the expression for security
            if (!isExpressionSafe(expression)) {
                println("Potentially unsafe math expression rejected: $expression")
                return "0"
            }
            
            // Evaluate the expression using safe parser
            val calculatedResult = try {
                println("Evaluating expression with parser: $expression")
                evaluateSafely(expression)
            } catch (e: Exception) {
                println("Math expression error: ${e.message} in expression: $expression")
                e.printStackTrace()
                0.0
            }
            
            println("Expression result: $calculatedResult")
            
            // Return the result, no rounding
            return calculatedResult.toString()
        } catch (e: Exception) {
            println("Exception in evaluateExpression: ${e.message}")
            e.printStackTrace()
            return "0"
        }
    }
    
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
        
        // Handle special case of max and min functions specifically as a pre-processing step
        val specialFunctionsPattern = "\\{math:(max|min)\\s*\\((.*?)\\)\\}%?".toRegex(RegexOption.DOT_MATCHES_ALL)
        var processedCommand = command
        
        // First pass - replace all math function expressions with simple placeholder values
        val mathFunctionMatches = specialFunctionsPattern.findAll(command).toList()
        if (mathFunctionMatches.isNotEmpty()) {
            LoggingService.info("Found ${mathFunctionMatches.size} special function expressions to pre-process")
            
            for ((index, match) in mathFunctionMatches.withIndex()) {
                try {
                    val fullMatch = match.value
                    val functionName = match.groupValues[1] // max or min
                    val argsText = match.groupValues[2] // arguments including any commas
                    
                    LoggingService.info("Pre-processing $functionName function #${index+1}: $fullMatch")
                    
                    // Check if this is a max/min function with a comma
                    if (argsText.contains(",")) {
                        // Split arguments at the first comma
                        val commaIndex = findOutermostComma(argsText)
                        if (commaIndex == -1) {
                            LoggingService.warning("Could not find comma to split arguments in: $argsText")
                            continue
                        }
                        
                        val arg1 = argsText.substring(0, commaIndex).trim()
                        val arg2 = argsText.substring(commaIndex + 1).trim()
                        
                        LoggingService.info("Function $functionName arguments: 1='$arg1', 2='$arg2'")
                        
                        // Process placeholders in arguments
                        var processed1 = PlaceholderService.setPlaceholders(player, arg1)
                        var processed2 = PlaceholderService.setPlaceholders(player, arg2)
                        
                        LoggingService.info("After placeholder processing: 1='$processed1', 2='$processed2'")
                        
                        // First, check if either argument needs further math evaluation
                        // For example, if it contains nested parentheses or operations
                        if (needsFurtherEvaluation(processed1)) {
                            LoggingService.info("Argument 1 needs further evaluation: $processed1")
                            try {
                                // Recursively evaluate this math expression
                                val evaluated = evaluateExpression(processed1, player, roundingMode)
                                processed1 = evaluated
                                LoggingService.info("Evaluated argument 1 to: $processed1")
                            } catch (e: Exception) {
                                LoggingService.warning("Failed to evaluate argument 1: ${e.message}")
                                processed1 = "0" // Default to 0 on failure
                            }
                        }
                        
                        if (needsFurtherEvaluation(processed2)) {
                            LoggingService.info("Argument 2 needs further evaluation: $processed2")
                            try {
                                // Recursively evaluate this math expression
                                val evaluated = evaluateExpression(processed2, player, roundingMode)
                                processed2 = evaluated
                                LoggingService.info("Evaluated argument 2 to: $processed2")
                            } catch (e: Exception) {
                                LoggingService.warning("Failed to evaluate argument 2: ${e.message}")
                                processed2 = "0" // Default to 0 on failure
                            }
                        }
                        
                        // Now convert to numbers
                        val num1 = processed1.toDoubleOrNull() ?: 0.0
                        val num2 = processed2.toDoubleOrNull() ?: 0.0
                        
                        // Apply the function
                        val result = when (functionName) {
                            "max" -> Math.max(num1, num2)
                            "min" -> Math.min(num1, num2)
                            else -> 0.0
                        }
                        
                        // Apply rounding
                        val roundedResult = applyRounding(result, roundingMode)
                        LoggingService.info("Function $functionName result: $roundedResult")
                        
                        // Replace the original function with its calculated value
                        // We need to use Regex.escapeReplacement to handle special characters in the replacement
                        processedCommand = processedCommand.replace(fullMatch, 
                                          if (fullMatch.endsWith("%")) "{math:$roundedResult}%" else "{math:$roundedResult}")
                    }
                } catch (e: Exception) {
                    LoggingService.severe("Error pre-processing function: $e")
                    LoggingService.severe(e.stackTraceToString())
                }
            }
        }
        
        // Now process the remaining math expressions with simple MATH_PATTERN matching
        val result = MATH_PATTERN.replace(processedCommand) { matchResult ->
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
     * Helper function to find the position of the outermost comma in a function argument string
     * This properly handles nested parentheses to find the comma that separates the top-level arguments
     */
    private fun findOutermostComma(text: String): Int {
        var parenLevel = 0
        
        for (i in text.indices) {
            when (text[i]) {
                '(' -> parenLevel++
                ')' -> parenLevel--
                ',' -> if (parenLevel == 0) return i  // Only count commas at the top level
            }
        }
        
        return -1  // No comma found at the top level
    }
    
    /**
     * Checks if a processed argument needs further math evaluation
     */
    private fun needsFurtherEvaluation(arg: String): Boolean {
        // Look for operation symbols or nested parentheses
        return arg.contains("+") || 
               arg.contains("-") || 
               arg.contains("*") || 
               arg.contains("/") || 
               arg.contains("%") ||
               arg.contains("^") ||
               (arg.contains("(") && arg.contains(")"))
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
            
            // Check if we're dealing with a simple numeric value after placeholder resolution
            val asDirectNumber = processedExpr.toDoubleOrNull()
            if (asDirectNumber != null) {
                // The expression resolved to a simple number, just return it with proper rounding
                val result = applyRounding(asDirectNumber, roundingMode)
                LoggingService.info("Expression is a direct number after placeholder processing: $result")
                return result
            }
            
            // Validate the expression for security
            if (!isExpressionSafe(processedExpr)) {
                LoggingService.warning("Potentially unsafe math expression rejected: $processedExpr")
                return "0"
            }
            
            // Evaluate the expression using safe parser
            val calculatedResult = try {
                LoggingService.info("Evaluating expression with parser: $processedExpr")
                evaluateSafely(processedExpr)
            } catch (e: Exception) {
                LoggingService.warning("Math expression error: ${e.message} in expression: $processedExpr")
                LoggingService.warning("Stack trace: ${e.stackTraceToString()}")
                0.0
            }
            
            LoggingService.info("Expression result: $calculatedResult")
            
            // Apply rounding based on config
            val finalResult = applyRounding(calculatedResult, roundingMode)
            LoggingService.info("Final result after rounding: $finalResult")
            
            return finalResult
        } catch (e: Exception) {
            LoggingService.severe("Exception in evaluateExpression: ${e.message}")
            LoggingService.severe("Stack trace: ${e.stackTraceToString()}")
            return "0"
        }
    }
    
    /**
     * Checks if an expression is safe to evaluate for test purposes.
     * Uses println instead of LoggingService for standalone testing.
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
                    println("Unauthorized function in expression: ${token.second}")
                    return false
                }
                if (token.first == TokenType.OPERATOR && !ALLOWED_OPERATORS.contains(token.second)) {
                    println("Unauthorized operator in expression: ${token.second}")
                    return false
                }
                if (token.first == TokenType.UNKNOWN) {
                    println("Unknown token in expression: ${token.second}")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            println("Error validating expression: ${e.message}")
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
        
        // First, process all placeholders within the expression
        // Start by replacing all placeholders with their actual values
        var processedExpression = expression
        
        // Find all % placeholders and replace them
        val percentPlaceholders = Regex("%([^%]+)%").findAll(expression)
        for (match in percentPlaceholders) {
            val placeholder = match.value
            val value = PlaceholderService.setPlaceholders(player, placeholder)
            
            // Only replace if we got a different value (meaning the placeholder was processed)
            if (value != placeholder) {
                LoggingService.info("Replacing placeholder '$placeholder' with value '$value'")
                processedExpression = processedExpression.replace(placeholder, value)
            } else {
                LoggingService.warning("Placeholder '$placeholder' was not processed - might be an unknown placeholder")
            }
        }
        
        // Find all bracket placeholders and replace them
        val bracketPlaceholders = Regex("\\{([^{}:]+)\\}").findAll(processedExpression)
        for (match in bracketPlaceholders) {
            val placeholder = match.value
            val value = PlaceholderService.setBracketPlaceholders(player, placeholder)
            
            // Only replace if we got a different value
            if (value != placeholder) {
                LoggingService.info("Replacing bracket placeholder '$placeholder' with value '$value'")
                processedExpression = processedExpression.replace(placeholder, value)
            }
        }
        
        // Debug log the processed expression
        LoggingService.info("Expression after placeholder processing: $processedExpression")
        
        // Handle numeric formatting if needed
        val finalExpression = processNumericPlaceholders(processedExpression, roundingMode)
        
        LoggingService.info("Final processed expression: $finalExpression")
        return finalExpression
    }
    
    /**
     * Process any numeric placeholders for proper formatting
     */
    private fun processNumericPlaceholders(expression: String, roundingMode: String): String {
        // This is a simple method to detect numeric values that might need formatting
        // For complex expressions, we'll leave them as-is for the evaluator to handle
        
        // If the expression is just a number, apply rounding
        val numericValue = expression.toDoubleOrNull()
        if (numericValue != null) {
            return applyRounding(numericValue, roundingMode)
        }
        
        return expression
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
        private val chars = sanitizeExpression(expression).toCharArray()
        
        /**
         * Sanitizes the expression to ensure it's parseable
         */
        private fun sanitizeExpression(expr: String): String {
            // Remove any extra whitespace
            var cleaned = expr.trim()
            
            // Log the initial cleaning step
            println("Sanitizing expression: '$expr'")
            
            // Handle max and min functions with nested parentheses, which are common sources of errors
            val maxMinPattern = "(max|min)\\s*\\((.+?)\\)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val matches = maxMinPattern.findAll(cleaned)
            
            // For each max/min function, ensure proper argument separation and balanced parentheses
            matches.forEach { match ->
                val fullMatch = match.value
                val funcName = match.groupValues[1]
                val args = match.groupValues[2]
                
                println("Sanitizing $funcName function with args: $args")
                
                // Check if this function contains nested parentheses that might cause parsing issues
                if (args.contains("(") && args.contains(")")) {
                    // Count open and close parentheses to check balance
                    val openCount = args.count { it == '(' }
                    val closeCount = args.count { it == ')' }
                    
                    if (openCount != closeCount) {
                        println("Unbalanced parentheses in $funcName function arguments: $args")
                        
                        // Try to fix by balancing parentheses
                        val fixed = if (openCount > closeCount) {
                            // Add missing closing parentheses
                            val missingClose = openCount - closeCount
                            "$funcName($args${"".padEnd(missingClose, ')')})"
                        } else {
                            // Add missing opening parentheses
                            val missingOpen = closeCount - openCount
                            "$funcName(${"".padEnd(missingOpen, '(')}$args)"
                        }
                        
                        println("Fixed function call: $fixed")
                        cleaned = cleaned.replace(fullMatch, fixed)
                    }
                    
                    // Also check for comma placement in two-argument functions
                    if (!args.contains(",")) {
                        println("Missing comma in $funcName function arguments: $args")
                        
                        // Try to insert a comma if there is an apparent location - in the middle
                        val insertPos = args.length / 2
                        val withComma = args.substring(0, insertPos) + "," + args.substring(insertPos)
                        val fixed = "$funcName($withComma)"
                        
                        println("Inserted comma in function call: $fixed")
                        cleaned = cleaned.replace(fullMatch, fixed)
                    }
                }
            }
            
            // Replace any unprocessed placeholders with 0
            // This is a safety mechanism to avoid parser errors
            cleaned = cleaned.replace(Regex("%[^%]+%"), "0")
            
            // Check for balanced parentheses across the entire expression
            val openCount = cleaned.count { it == '(' }
            val closeCount = cleaned.count { it == ')' }
            
            // Add missing closing parentheses if needed
            if (openCount > closeCount) {
                val missing = openCount - closeCount
                println("Expression missing $missing closing parentheses, adding them")
                cleaned = cleaned + ")".repeat(missing)
            } else if (closeCount > openCount) {
                // Add missing opening parentheses if needed
                val missing = closeCount - openCount
                println("Expression missing $missing opening parentheses, adding them at the beginning")
                cleaned = "(".repeat(missing) + cleaned
            }
            
            // Ensure balanced function calls
            // Check for common functions followed by open parenthesis without a matching close
            val funcPattern = "(max|min|abs|sqrt|pow|sin|cos|tan)\\s*\\(".toRegex()
            val funcMatches = funcPattern.findAll(cleaned)
            
            for (match in funcMatches) {
                val funcStart = match.range.first
                val openParenPos = match.range.last
                
                // Ensure a balanced parenthesis for this function
                var level = 1
                var pos = openParenPos + 1
                var foundClosing = false
                
                while (pos < cleaned.length) {
                    when (cleaned[pos]) {
                        '(' -> level++
                        ')' -> {
                            level--
                            if (level == 0) {
                                foundClosing = true
                                break
                            }
                        }
                    }
                    pos++
                }
                
                if (!foundClosing) {
                    println("Function call at position $funcStart missing closing parenthesis")
                    // Add closing parenthesis at the end
                    cleaned = cleaned + ")"
                    println("Added missing closing parenthesis at the end")
                }
            }
            
            // Insert * operator between number and parenthesis if needed (e.g., "2(3+4)" -> "2*(3+4)")
            cleaned = cleaned.replace(Regex("(\\d)\\s*\\("), "$1*(")
            
            // Log any placeholder replacements or other changes
            if (cleaned != expr.trim()) {
                println("Sanitized expression: '$expr' -> '$cleaned'")
            }
            
            // Return the cleaned expression
            println("Final sanitized expression: '$cleaned'")
            return cleaned
        }
        
        fun parse(): Double {
            try {
                println("Starting to parse expression: '${String(chars)}'")
                val result = parseExpression()
                
                // Specifically check if we've parsed the entire expression
                if (pos < chars.size) {
                    val remaining = String(chars.copyOfRange(pos, chars.size))
                    println("Parser didn't consume entire expression. Remaining: '$remaining'")
                    
                    // Attempt to continue parsing the remaining part
                    if (remaining.trim().isNotEmpty()) {
                        // Reset position and parse the entire expression
                        println("Restarting parsing from the beginning with full handling")
                        pos = 0
                        return parseFullExpression()
                    }
                }
                
                println("Parse result: $result")
                return result
            } catch (e: Exception) {
                println("Error parsing expression '${String(chars)}': ${e.message}")
                // Try parsing with full expression handling as fallback
                try {
                    pos = 0
                    return parseFullExpression()
                } catch (e2: Exception) {
                    println("Fallback parsing also failed: ${e2.message}")
                    throw e
                }
            }
        }
        
        /**
         * Parse the full expression, handling all operators at the appropriate precedence level
         */
        private fun parseFullExpression(): Double {
            println("Using full expression parser")
            
            // First try to handle addition/subtraction expressions
            return parseAddSubtract()
        }
        
        private fun parseAddSubtract(): Double {
            // First handle multiplication and division
            var result = parseMulDivide()
            
            // Continue parsing while we have + or - operators
            while (pos < chars.size) {
                skipWhitespace()
                if (pos >= chars.size) break
                
                when (chars[pos]) {
                    '+' -> {
                        pos++
                        val term = parseMulDivide()
                        println("Addition: $result + $term")
                        result += term
                    }
                    '-' -> {
                        pos++
                        val term = parseMulDivide()
                        println("Subtraction: $result - $term")
                        result -= term
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseMulDivide(): Double {
            // First handle exponentiation
            var result = parseExponent()
            
            // Continue parsing while we have * / or % operators
            while (pos < chars.size) {
                skipWhitespace()
                if (pos >= chars.size) break
                
                when (chars[pos]) {
                    '*' -> {
                        pos++
                        val factor = parseExponent()
                        println("Multiplication: $result * $factor")
                        result *= factor
                    }
                    '/' -> {
                        pos++
                        val factor = parseExponent()
                        if (factor == 0.0) {
                            println("Division by zero detected! Using 1 instead.")
                            result /= 1.0
                        } else {
                            println("Division: $result / $factor")
                            result /= factor
                        }
                    }
                    '%' -> {
                        pos++
                        val factor = parseExponent()
                        if (factor == 0.0) {
                            println("Modulo by zero detected! Using 1 instead.")
                            result %= 1.0
                        } else {
                            println("Modulo: $result % $factor")
                            result %= factor
                        }
                    }
                    else -> break
                }
            }
            
            return result
        }
        
        private fun parseExponent(): Double {
            // First handle primary factors (numbers, parentheses, functions)
            var result = parsePrimary()
            
            // Check for exponentiation
            skipWhitespace()
            if (pos < chars.size && chars[pos] == '^') {
                pos++
                val exponent = parsePrimary()
                println("Exponentiation: $result ^ $exponent")
                result = result.pow(exponent)
            }
            
            return result
        }
        
        private fun parsePrimary(): Double {
            skipWhitespace()
            
            // Handle functions
            if (pos < chars.size && chars[pos].isLetter()) {
                return parseFunction()
            }
            
            // Handle parentheses
            if (pos < chars.size && chars[pos] == '(') {
                pos++ // Skip '('
                println("Found opening parenthesis at position ${pos-1}")
                val result = parseFullExpression()
                skipWhitespace()
                
                if (pos < chars.size && chars[pos] == ')') {
                    pos++ // Skip ')'
                    println("Found closing parenthesis at position ${pos-1}, result: $result")
                    return result
                } else {
                    println("Missing closing parenthesis at position $pos")
                    println("Expression fragment: '${String(chars, Math.max(0, pos-10), Math.min(20, chars.size - Math.max(0, pos-10)))}...'")
                    
                    // In recovery mode - assume there's a closing parenthesis
                    println("Attempting to recover by assuming a closing parenthesis")
                    return result
                }
            }
            
            // Handle negative numbers
            if (pos < chars.size && chars[pos] == '-') {
                pos++
                println("Found negative sign at position ${pos-1}")
                val negatedValue = parsePrimary()
                println("Negated value: -$negatedValue")
                return -negatedValue
            }
            
            // Parse numbers
            return parseNumber()
        }
        
        private fun parseFunction(): Double {
            val funcStart = pos
            
            // Read the function name
            while (pos < chars.size && chars[pos].isLetter()) {
                pos++
            }
            
            val function = String(chars, funcStart, pos - funcStart).lowercase()
            println("Found function: $function")
            
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                println("Unknown function: $function")
                pos = funcStart // Revert position
                throw IllegalArgumentException("Unknown function: $function")
            }
            
            skipWhitespace()
            if (pos >= chars.size || chars[pos] != '(') {
                println("Missing opening parenthesis for function: $function")
                pos = funcStart // Revert position
                throw IllegalArgumentException("Missing opening parenthesis for function: $function")
            }
            
            pos++ // Skip '('
            skipWhitespace()
            
            // Parse arguments
            val arguments = mutableListOf<Double>()
            
            // Special case for empty argument list
            if (pos < chars.size && chars[pos] == ')') {
                pos++ // Skip ')'
                println("Function $function called with no arguments")
                
                // Apply function with no arguments
                return when (function) {
                    "min", "max" -> 0.0 // Default for min/max with no args
                    else -> throw IllegalArgumentException("Function $function requires arguments")
                }
            }
            
            // Parse first argument
            arguments.add(parseFullExpression())
            
            // Parse additional arguments if present
            while (pos < chars.size && chars[pos] == ',') {
                pos++ // Skip ','
                skipWhitespace()
                arguments.add(parseFullExpression())
            }
            
            skipWhitespace()
            if (pos >= chars.size || chars[pos] != ')') {
                println("Missing closing parenthesis for function: $function")
                // Try to continue anyway
            } else {
                pos++ // Skip ')'
            }
            
            // Apply the function with all parsed arguments
            val result = when (function) {
                "sin" -> if (arguments.isNotEmpty()) sin(Math.toRadians(arguments[0])) else 0.0
                "cos" -> if (arguments.isNotEmpty()) cos(Math.toRadians(arguments[0])) else 0.0
                "tan" -> if (arguments.isNotEmpty()) tan(Math.toRadians(arguments[0])) else 0.0
                "sqrt" -> if (arguments.isNotEmpty()) sqrt(arguments[0]) else 0.0
                "abs" -> if (arguments.isNotEmpty()) abs(arguments[0]) else 0.0
                "pow" -> if (arguments.size >= 2) arguments[0].pow(arguments[1]) else if (arguments.isNotEmpty()) arguments[0] else 0.0
                "min" -> if (arguments.isNotEmpty()) arguments.minOf { it } else 0.0
                "max" -> if (arguments.isNotEmpty()) arguments.maxOf { it } else 0.0
                else -> throw IllegalArgumentException("Unknown function: $function")
            }
            
            println("Function $function evaluated with ${arguments.size} arguments: $result")
            return result
        }
        
        private fun parseExpression(): Double {
            // Start by parsing a term
            var result = parseTerm()
            
            // Continue parsing as long as we have operators and haven't reached the end
            while (pos < chars.size) {
                when (chars[pos]) {
                    '+' -> {
                        pos++
                        val term = parseTerm()
                        println("Addition: $result + $term")
                        result += term
                    }
                    '-' -> {
                        pos++
                        val term = parseTerm()
                        println("Subtraction: $result - $term")
                        result -= term
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
                        val factor = parseFactor()
                        println("Multiplication: $result * $factor")
                        result *= factor
                    }
                    '/' -> {
                        pos++
                        val factor = parseFactor()
                        if (factor == 0.0) {
                            println("Division by zero detected! Using 1 instead.")
                            result /= 1.0
                        } else {
                            println("Division: $result / $factor")
                            result /= factor
                        }
                    }
                    '%' -> {
                        pos++
                        val factor = parseFactor()
                        if (factor == 0.0) {
                            println("Modulo by zero detected! Using 1 instead.")
                            result %= 1.0
                        } else {
                            println("Modulo: $result % $factor")
                            result %= factor
                        }
                    }
                    '^' -> {
                        pos++
                        val exponent = parseFactor()
                        println("Exponentiation: $result ^ $exponent")
                        result = result.pow(exponent)
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
                val function = String(chars, funcStart, pos - funcStart).lowercase()
                println("Found potential function: $function")
                
                if (ALLOWED_FUNCTIONS.contains(function)) {
                    skipWhitespace()
                    if (pos < chars.size && chars[pos] == '(') {
                        pos++ // Skip '('
                        println("Parsing arguments for function: $function")
                        
                        // Create a list to store all arguments
                        val arguments = mutableListOf<Double>()
                        
                        // Parse the first argument
                        skipWhitespace()
                        if (pos < chars.size && chars[pos] != ')') {
                            val arg = parseExpression()
                            arguments.add(arg)
                            println("Parsed argument: $arg")
                            
                            // Parse additional arguments if present
                            while (pos < chars.size && chars[pos] == ',') {
                                pos++ // Skip ','
                                skipWhitespace()
                                val nextArg = parseExpression()
                                arguments.add(nextArg)
                                println("Parsed additional argument: $nextArg")
                                skipWhitespace()
                            }
                        }
                        
                        // Check for closing parenthesis
                        if (pos < chars.size && chars[pos] == ')') {
                            pos++ // Skip ')'
                            
                            // Apply the function with all parsed arguments
                            val result = when (function) {
                                "sin" -> if (arguments.isNotEmpty()) sin(Math.toRadians(arguments[0])) else 0.0
                                "cos" -> if (arguments.isNotEmpty()) cos(Math.toRadians(arguments[0])) else 0.0
                                "tan" -> if (arguments.isNotEmpty()) tan(Math.toRadians(arguments[0])) else 0.0
                                "sqrt" -> if (arguments.isNotEmpty()) sqrt(arguments[0]) else 0.0
                                "abs" -> if (arguments.isNotEmpty()) abs(arguments[0]) else 0.0
                                "pow" -> if (arguments.size >= 2) arguments[0].pow(arguments[1]) else if (arguments.isNotEmpty()) arguments[0] else 0.0
                                "min" -> if (arguments.isNotEmpty()) arguments.minOf { it } else 0.0
                                "max" -> if (arguments.isNotEmpty()) arguments.maxOf { it } else 0.0
                                else -> throw IllegalArgumentException("Unknown function: $function")
                            }
                            
                            println("Function $function evaluated with ${arguments.size} arguments: $result")
                            return result
                        } else {
                            // Missing closing parenthesis
                            println("Missing closing parenthesis for function: $function")
                            
                            // Apply the function anyway as a fallback
                            val result = when (function) {
                                "sin" -> if (arguments.isNotEmpty()) sin(Math.toRadians(arguments[0])) else 0.0
                                "cos" -> if (arguments.isNotEmpty()) cos(Math.toRadians(arguments[0])) else 0.0
                                "tan" -> if (arguments.isNotEmpty()) tan(Math.toRadians(arguments[0])) else 0.0
                                "sqrt" -> if (arguments.isNotEmpty()) sqrt(arguments[0]) else 0.0
                                "abs" -> if (arguments.isNotEmpty()) abs(arguments[0]) else 0.0
                                "pow" -> if (arguments.size >= 2) arguments[0].pow(arguments[1]) else if (arguments.isNotEmpty()) arguments[0] else 0.0
                                "min" -> if (arguments.isNotEmpty()) arguments.minOf { it } else 0.0
                                "max" -> if (arguments.isNotEmpty()) arguments.maxOf { it } else 0.0
                                else -> throw IllegalArgumentException("Unknown function: $function")
                            }
                            
                            println("Function $function evaluation recovery with result: $result")
                            return result
                        }
                    }
                }
                
                // Not a function or invalid function syntax, revert position
                println("Not a valid function call, reverting position to $funcStart")
                pos = funcStart
            }
            
            // Handle parentheses
            if (pos < chars.size && chars[pos] == '(') {
                pos++ // Skip '('
                println("Found opening parenthesis at position ${pos-1}")
                val result = parseExpression()
                skipWhitespace()
                
                if (pos < chars.size && chars[pos] == ')') {
                    pos++ // Skip ')'
                    println("Found closing parenthesis at position ${pos-1}, result: $result")
                    return result
                } else {
                    println("Missing closing parenthesis at position $pos")
                    println("Expression fragment: '${String(chars, Math.max(0, pos-10), Math.min(20, chars.size - Math.max(0, pos-10)))}...'")
                    
                    // In recovery mode - assume there's a closing parenthesis
                    println("Attempting to recover by assuming a closing parenthesis")
                    return result
                }
            }
            
            // Handle negative numbers
            if (pos < chars.size && chars[pos] == '-') {
                pos++
                println("Found negative sign at position ${pos-1}")
                val negatedValue = parseFactor()
                println("Negated value: -$negatedValue")
                return -negatedValue
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
                        println("Invalid number format: multiple decimal points at position $pos")
                        throw IllegalArgumentException("Invalid number format: multiple decimal points")
                    }
                    hasDecimal = true
                }
                pos++
            }
            
            if (start == pos) {
                println("Expected number at position $pos but found '${if (pos < chars.size) chars[pos] else "end of input"}'")
                throw IllegalArgumentException("Expected number at position $pos")
            }
            
            val numStr = String(chars, start, pos - start)
            val result = numStr.toDouble()
            println("Parsed number: $result")
            return result
        }
        
        private fun skipWhitespace() {
            val startPos = pos
            while (pos < chars.size && chars[pos].isWhitespace()) {
                pos++
            }
            if (pos > startPos) {
                println("Skipped whitespace from position $startPos to $pos")
            }
        }
    }

    private class Evaluator {
        /**
         * This method evaluates a mathematical function based on its name and arguments
         */
        fun evaluateMathFunction(name: String, args: List<Double>): Double {
            return when (name.lowercase()) {
                "sin" -> if (args.isNotEmpty()) sin(args[0]) else 0.0
                "cos" -> if (args.isNotEmpty()) cos(args[0]) else 0.0
                "tan" -> if (args.isNotEmpty()) tan(args[0]) else 0.0
                "sqrt" -> if (args.isNotEmpty()) sqrt(args[0]) else 0.0
                "abs" -> if (args.isNotEmpty()) abs(args[0]) else 0.0
                "pow" -> if (args.size >= 2) args[0].pow(args[1]) else if (args.isNotEmpty()) args[0] else 0.0
                "min" -> evaluateMinFunction(args)
                "max" -> evaluateMaxFunction(args)
                else -> throw IllegalArgumentException("Unknown function: $name with args: $args")
            }
        }
        
        /**
         * Evaluates the max function with robust error handling
         * Supports multiple arguments and filters out invalid values
         */
        private fun evaluateMaxFunction(args: List<Double>): Double {
            println("Evaluating max function with args: $args")
            
            if (args.isEmpty()) {
                println("Max function called with no arguments, returning 0")
                return 0.0
            }
            
            // Filter out NaN values, which can occur from parsing errors
            val validArgs = args.filter { !it.isNaN() }
            
            if (validArgs.isEmpty()) {
                println("Max function has only NaN arguments, returning 0")
                return 0.0
            }
            
            // Handle special cases for infinity
            if (validArgs.any { it == Double.POSITIVE_INFINITY }) {
                println("Max function contains POSITIVE_INFINITY, returning POSITIVE_INFINITY")
                return Double.POSITIVE_INFINITY
            }
            
            // Handle the regular case
            val maxValue = validArgs.maxOrNull() ?: 0.0
            println("Max function result: $maxValue from valid arguments: $validArgs")
            return maxValue
        }
        
        /**
         * Evaluates the min function with robust error handling
         * Supports multiple arguments and filters out invalid values
         */
        private fun evaluateMinFunction(args: List<Double>): Double {
            println("Evaluating min function with args: $args")
            
            if (args.isEmpty()) {
                println("Min function called with no arguments, returning 0")
                return 0.0
            }
            
            // Filter out NaN values, which can occur from parsing errors
            val validArgs = args.filter { !it.isNaN() }
            
            if (validArgs.isEmpty()) {
                println("Min function has only NaN arguments, returning 0")
                return 0.0
            }
            
            // Handle special cases for infinity
            if (validArgs.any { it == Double.NEGATIVE_INFINITY }) {
                println("Min function contains NEGATIVE_INFINITY, returning NEGATIVE_INFINITY")
                return Double.NEGATIVE_INFINITY
            }
            
            // Handle the regular case
            val minValue = validArgs.minOrNull() ?: 0.0
            println("Min function result: $minValue from valid arguments: $validArgs")
            return minValue
        }
        
        /**
         * This method evaluates a binary operation
         */
        fun evaluateBinaryOperation(operator: String, left: Double, right: Double): Double {
            return when (operator) {
                "+" -> left + right
                "-" -> left - right
                "*" -> left * right
                "/" -> if (right == 0.0) {
                    println("Division by zero detected, returning Infinity")
                    Double.POSITIVE_INFINITY
                } else {
                    left / right
                }
                "%" -> if (right == 0.0) {
                    println("Modulo by zero detected, returning 0")
                    0.0
                } else {
                    left % right
                }
                "^" -> left.pow(right)
                else -> throw IllegalArgumentException("Unknown operator: $operator")
            }
        }
    }
} 