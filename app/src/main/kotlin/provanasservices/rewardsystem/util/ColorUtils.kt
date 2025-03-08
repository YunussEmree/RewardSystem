package provanasservices.rewardsystem.util

import net.md_5.bungee.api.ChatColor
import java.util.regex.Pattern

/**
 * Utility class for handling color code processing and formatting.
 * Provides methods to translate and format color codes in strings.
 */
object ColorUtils {
    /** Pattern to match hex color codes in {#RRGGBB} format */
    private val HEX_PATTERN_1 = Pattern.compile("\\{(#[0-9A-Fa-f]{6})\\}")
    
    /** Pattern to match hex color codes in &#RRGGBB format */
    private val HEX_PATTERN_2 = Pattern.compile("&#[0-9A-Fa-f]{6}")
    
    /**
     * Translates all color codes in a string to their colored representation.
     * Supports both standard color codes (&a, &b, etc.) and hex colors in two formats:
     * - {#RRGGBB} format
     * - &#RRGGBB format
     * 
     * @param text The text containing color codes
     * @return Text with processed color codes
     */
    fun translateColors(text: String?): String {
        if (text == null) return ""
        
        // First convert {#RRGGBB} format to &#RRGGBB format
        var result = text.replace(HEX_PATTERN_1.toRegex(), "&$1")
        
        // Then process all &#RRGGBB hex codes
        if (HEX_PATTERN_2.toRegex().containsMatchIn(result)) {
            for (match in "&(#[0-9A-Fa-f]{6})".toRegex().findAll(result)) {
                result = result.replaceFirst(
                    match.value.toRegex(), 
                    ChatColor.of(match.value.substring(1)).toString()
                )
            }
        }
        
        // Finally process standard color codes (&a, &b, etc.)
        return ChatColor.translateAlternateColorCodes('&', result)
    }
} 