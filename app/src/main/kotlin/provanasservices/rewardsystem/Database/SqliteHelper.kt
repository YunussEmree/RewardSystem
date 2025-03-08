package provanasservices.rewardsystem.Database

import org.bukkit.Bukkit
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.service.LoggingService
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

/**
 * SQLite implementation of the database helper.
 * Handles database operations for SQLite databases.
 */
class SqliteHelper(private val plugin: Main) : DbHelper {
    private var connection: Connection? = null

    /**
     * Connects to the SQLite database.
     * Creates the database file if it doesn't exist.
     */
    override fun connect() {
        try {
            // Create the database directory if it doesn't exist
            val databaseFolder = File(plugin.dataFolder, "database")
            if (!databaseFolder.exists()) {
                databaseFolder.mkdirs()
            }

            // Create database file path
            val dbFile = File(databaseFolder, "cooldowns.db")
            
            // Connect to database
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.absolutePath)

            // Create tables if they don't exist
            createTables()
            
            LoggingService.info("Connected to SQLite database at ${dbFile.absolutePath}")
        } catch (e: Exception) {
            LoggingService.severe("Failed to connect to SQLite database: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Disconnects from the SQLite database and releases resources.
     */
    override fun disconnect() {
        try {
            if (connection != null && !connection!!.isClosed) {
                connection!!.close()
                LoggingService.info("Disconnected from SQLite database")
            }
        } catch (e: SQLException) {
            LoggingService.severe("Error closing SQLite connection: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Creates the necessary tables if they don't exist.
     */
    private fun createTables() {
        val statement = connection?.createStatement()
        
        try {
            // Create cooldowns table
            statement?.executeUpdate(
                "CREATE TABLE IF NOT EXISTS cooldowns (" +
                "player_name TEXT NOT NULL, " +
                "reward_id TEXT NOT NULL, " +
                "expiry INTEGER NOT NULL, " +
                "PRIMARY KEY (player_name, reward_id))"
            )
        } catch (e: SQLException) {
            LoggingService.severe("Failed to create tables: ${e.message}")
            e.printStackTrace()
        } finally {
            statement?.close()
        }
    }

    /**
     * Imports cooldowns from the database into the cooldown map.
     * Only imports cooldowns that haven't expired yet.
     *
     * @param cooldownsMap The map to populate with cooldown data
     * @param rewardId The ID of the reward
     * @param currentTime The current time in milliseconds
     */
    override fun importCooldowns(cooldownsMap: MutableMap<UUID, Long>, rewardId: String, currentTime: Long) {
        var preparedStatement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        
        try {
            // Delete expired cooldowns first
            val cleanupSql = "DELETE FROM cooldowns WHERE expiry < ?"
            preparedStatement = connection?.prepareStatement(cleanupSql)
            preparedStatement?.setLong(1, currentTime)
            preparedStatement?.executeUpdate()
            preparedStatement?.close()

            // Prepare query to get all cooldowns for a specific reward ID
            val sql = "SELECT player_name, expiry FROM cooldowns WHERE reward_id = ? AND expiry > ?"
            preparedStatement = connection?.prepareStatement(sql)
            preparedStatement?.setString(1, rewardId)
            preparedStatement?.setLong(2, currentTime)
            
            // Execute query and process results
            resultSet = preparedStatement?.executeQuery()
            
            while (resultSet?.next() == true) {
                val playerName = resultSet.getString("player_name")
                val expiry = resultSet.getLong("expiry")
                
                try {
                    // Convert player name to UUID
                    val playerUUID = UUID.fromString(playerName)
                    
                    // Add to cooldown map
                    cooldownsMap[playerUUID] = expiry
                } catch (e: IllegalArgumentException) {
                    // Skip invalid UUIDs
                    LoggingService.warning("Invalid UUID in database: $playerName")
                }
            }
            
            LoggingService.debug("Imported ${cooldownsMap.size} cooldowns for reward ID: $rewardId")
        } catch (e: SQLException) {
            LoggingService.severe("Error importing cooldowns: ${e.message}")
            e.printStackTrace()
        } finally {
            resultSet?.close()
            preparedStatement?.close()
        }
    }

    /**
     * Exports cooldowns from the cooldown map to the database.
     *
     * @param cooldownsMap The map containing cooldown data
     * @param rewardId The ID of the reward
     */
    override fun exportCooldowns(cooldownsMap: MutableMap<UUID, Long>, rewardId: String) {
        var preparedStatement: PreparedStatement? = null
        
        try {
            // Begin transaction for better performance
            connection?.autoCommit = false
            
            // Clear existing cooldowns for this reward ID
            var clearSql = "DELETE FROM cooldowns WHERE reward_id = ?"
            preparedStatement = connection?.prepareStatement(clearSql)
            preparedStatement?.setString(1, rewardId)
            preparedStatement?.executeUpdate()
            preparedStatement?.close()
            
            // Skip if no cooldowns to export
            if (cooldownsMap.isEmpty()) {
                connection?.commit()
                return
            }
            
            // Insert new cooldowns
            val insertSql = "INSERT INTO cooldowns (player_name, reward_id, expiry) VALUES (?, ?, ?)"
            preparedStatement = connection?.prepareStatement(insertSql)
            
            for ((playerUUID, expiry) in cooldownsMap) {
                preparedStatement?.setString(1, playerUUID.toString())
                preparedStatement?.setString(2, rewardId)
                preparedStatement?.setLong(3, expiry)
                preparedStatement?.addBatch()
            }
            
            // Execute batch insert
            preparedStatement?.executeBatch()
            connection?.commit()
            
            LoggingService.debug("Exported ${cooldownsMap.size} cooldowns for reward ID: $rewardId")
        } catch (e: SQLException) {
            LoggingService.severe("Error exporting cooldowns: ${e.message}")
            e.printStackTrace()
            
            // Rollback transaction on error
            try {
                connection?.rollback()
            } catch (rollbackEx: SQLException) {
                LoggingService.severe("Error rolling back transaction: ${rollbackEx.message}")
            }
        } finally {
            try {
                connection?.autoCommit = true
            } catch (e: SQLException) {
                LoggingService.severe("Error restoring auto-commit mode: ${e.message}")
            }
            preparedStatement?.close()
        }
    }
}