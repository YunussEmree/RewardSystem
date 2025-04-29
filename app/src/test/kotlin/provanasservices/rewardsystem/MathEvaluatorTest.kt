package provanasservices.rewardsystem

import org.bukkit.entity.Player
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.service.PlaceholderService
import provanasservices.rewardsystem.util.MathEvaluator
import kotlin.math.abs

/**
 * Tests for the MathEvaluator class
 * This mocks the required components to test the math evaluation functionality
 */
class MathEvaluatorTest {

    private lateinit var mockPlayer: Player

    @Before
    fun setup() {
        // Mock the player
        mockPlayer = mock(Player::class.java)
        
        // Setup the PlaceholderService mock to return input values
        // This will allow our tests to run without needing the actual PlaceholderAPI
        val originalPlaceholderService = PlaceholderService
        
        // Create a test implementation that returns the same string or handles specific cases
        object : PlaceholderService {
            override fun setPlaceholders(player: Player, text: String): String {
                return when {
                    text.contains("%player_level%") -> text.replace("%player_level%", "25")
                    text.contains("%server_online%") -> "1"
                    else -> text
                }
            }

            override fun setBracketPlaceholders(player: Player, text: String): String {
                return text
            }
        }
    }

    /**
     * Test basic arithmetic operations
     */
    @Test
    fun testBasicArithmetic() {
        val expressions = mapOf(
            "5 + 3" to 8.0,
            "10 - 4" to 6.0,
            "3 * 4" to 12.0,
            "20 / 5" to 4.0,
            "2 ^ 3" to 8.0
        )
        
        expressions.forEach { (expr, expected) ->
            val result = MathEvaluator.evaluateExpression(expr, mockPlayer).toDoubleOrNull() ?: 0.0
            assertEquals("Expression $expr should evaluate to $expected", expected, result, 0.001)
        }
    }
    
    /**
     * Test parenthesized expressions
     */
    @Test
    fun testParenthesizedExpressions() {
        val expressions = mapOf(
            "(5 + 3) * 2" to 16.0,
            "10 - (4 + 1)" to 5.0,
            "3 * (4 + 2)" to 18.0,
            "20 / (5 + 5)" to 2.0,
            "(2 + 3) ^ 2" to 25.0
        )
        
        expressions.forEach { (expr, expected) ->
            val result = MathEvaluator.evaluateExpression(expr, mockPlayer).toDoubleOrNull() ?: 0.0
            assertEquals("Expression $expr should evaluate to $expected", expected, result, 0.001)
        }
    }
    
    /**
     * Test expressions with multiple operations
     */
    @Test
    fun testComplexExpressions() {
        val expressions = mapOf(
            "5 + 3 * 2" to 11.0,
            "10 - 4 + 1" to 7.0,
            "3 * 4 / 2" to 6.0,
            "20 / 5 + 3" to 7.0,
            "2 ^ 3 + 4" to 12.0
        )
        
        expressions.forEach { (expr, expected) ->
            val result = MathEvaluator.evaluateExpression(expr, mockPlayer).toDoubleOrNull() ?: 0.0
            assertEquals("Expression $expr should evaluate to $expected", expected, result, 0.001)
        }
    }
    
    /**
     * Test specifically the expressions that were causing issues
     */
    @Test
    fun testProblemExpressions() {
        val expressions = mapOf(
            "20 + (25 / 10)" to 22.5,
            "90 - (25 * 10)" to -160.0,
            "max(10, 90 - (25 * 10))" to 10.0,
            "max(10, 5)" to 10.0,
            "min(10, 5)" to 5.0
        )
        
        expressions.forEach { (expr, expected) ->
            val result = MathEvaluator.evaluateExpression(expr, mockPlayer).toDoubleOrNull() ?: 0.0
            assertEquals("Expression $expr should evaluate to $expected", expected, result, 0.001)
        }
    }
    
    /**
     * Test math functions with multiple arguments and special cases
     */
    @Test
    fun testMultiArgFunctions() {
        val expressions = mapOf(
            "max(1, 2, 3, 4, 5)" to 5.0,
            "min(5, 4, 3, 2, 1)" to 1.0,
            "max(10, max(5, 15))" to 15.0,
            "min(5, min(3, 8))" to 3.0
        )
        
        expressions.forEach { (expr, expected) ->
            val result = MathEvaluator.evaluateExpression(expr, mockPlayer).toDoubleOrNull() ?: 0.0
            assertEquals("Expression $expr should evaluate to $expected", expected, result, 0.001)
        }
    }
} 