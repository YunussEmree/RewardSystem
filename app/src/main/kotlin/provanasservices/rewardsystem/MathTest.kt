package provanasservices.rewardsystem

import provanasservices.rewardsystem.util.LoggingUtility
import provanasservices.rewardsystem.util.MathEvaluator

/**
 * Simple test class for directly testing math expressions.
 * This should be run outside of Minecraft for testing purposes only.
 */
object MathTest {

    /**
     * Main method for direct testing
     */
    @JvmStatic
    fun main(args: Array<String>) {
        // Initialize logging in standalone mode with debug enabled
        LoggingUtility.initialize(false, true)
        
        LoggingUtility.info("Math Expression Tester")
        LoggingUtility.info("======================")
        
        // Initialize MathEvaluator
        MathEvaluator.initialize()

        // Test basic expressions
        LoggingUtility.info("BASIC ARITHMETIC")
        LoggingUtility.info("----------------")
        testGroup(listOf(
            "5 + 3",
            "10 - 4", 
            "3 * 4",
            "20 / 5",
            "2 ^ 3"
        ))
        
        // Test parenthesized expressions
        LoggingUtility.info("\nPARENTHESIZED EXPRESSIONS")
        LoggingUtility.info("-------------------------")
        testGroup(listOf(
            "(5 + 3) * 2",
            "10 - (4 + 1)",
            "3 * (4 + 2)",
            "20 / (5 + 5)",
            "(2 + 3) ^ 2"
        ))
        
        // Test complex expressions
        LoggingUtility.info("\nCOMPLEX EXPRESSIONS")
        LoggingUtility.info("-------------------")
        testGroup(listOf(
            "5 + 3 * 2",
            "10 - 4 + 1",
            "3 * 4 / 2",
            "20 / 5 + 3",
            "2 ^ 3 + 4"
        ))
        
        // Test specifically the expressions that were causing issues
        LoggingUtility.info("\nPROBLEM EXPRESSIONS")
        LoggingUtility.info("-------------------")
        testGroup(listOf(
            "20 + (25 / 10)",  // Was truncating after "20"
            "90 - (25 * 10)",  // Was truncating after "90"
            "max(10, 90 - (25 * 10))",
            "max(10, 5)",
            "min(10, 5)"
        ))
        
        // Test multi-argument functions
        LoggingUtility.info("\nMULTI-ARGUMENT FUNCTIONS")
        LoggingUtility.info("------------------------")
        testGroup(listOf(
            "max(1, 2, 3, 4, 5)",
            "min(5, 4, 3, 2, 1)",
            "max(10, max(5, 15))",
            "min(5, min(3, 8))"
        ))
        
        LoggingUtility.info("\nTesting complete!")
    }
    
    /**
     * Tests a group of expressions and prints results
     */
    private fun testGroup(expressions: List<String>) {
        expressions.forEach { expr ->
            try {
                val result = MathEvaluator.testEvaluateExpression(expr)
                LoggingUtility.info("  $expr = $result")
            } catch (e: Exception) {
                LoggingUtility.error("  $expr = ERROR: ${e.message}")
            }
        }
    }
} 