package provanasservices.rewardsystem

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
        println("Math Expression Tester")
        println("======================")
        println()

        // Test basic expressions
        println("BASIC ARITHMETIC")
        println("----------------")
        testGroup(listOf(
            "5 + 3",
            "10 - 4", 
            "3 * 4",
            "20 / 5",
            "2 ^ 3"
        ))
        
        // Test parenthesized expressions
        println("\nPARENTHESIZED EXPRESSIONS")
        println("-------------------------")
        testGroup(listOf(
            "(5 + 3) * 2",
            "10 - (4 + 1)",
            "3 * (4 + 2)",
            "20 / (5 + 5)",
            "(2 + 3) ^ 2"
        ))
        
        // Test complex expressions
        println("\nCOMPLEX EXPRESSIONS")
        println("-------------------")
        testGroup(listOf(
            "5 + 3 * 2",
            "10 - 4 + 1",
            "3 * 4 / 2",
            "20 / 5 + 3",
            "2 ^ 3 + 4"
        ))
        
        // Test specifically the expressions that were causing issues
        println("\nPROBLEM EXPRESSIONS")
        println("-------------------")
        testGroup(listOf(
            "20 + (25 / 10)",  // Was truncating after "20"
            "90 - (25 * 10)",  // Was truncating after "90"
            "max(10, 90 - (25 * 10))",
            "max(10, 5)",
            "min(10, 5)"
        ))
        
        // Test multi-argument functions
        println("\nMULTI-ARGUMENT FUNCTIONS")
        println("------------------------")
        testGroup(listOf(
            "max(1, 2, 3, 4, 5)",
            "min(5, 4, 3, 2, 1)",
            "max(10, max(5, 15))",
            "min(5, min(3, 8))"
        ))
        
        println("\nTesting complete!")
    }
    
    /**
     * Tests a group of expressions and prints results
     */
    private fun testGroup(expressions: List<String>) {
        expressions.forEach { expr ->
            try {
                val result = MathEvaluator.testEvaluateExpression(expr)
                println("  $expr = $result")
            } catch (e: Exception) {
                println("  $expr = ERROR: ${e.message}")
            }
        }
    }
} 