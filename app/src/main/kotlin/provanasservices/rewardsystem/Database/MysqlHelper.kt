package provanasservices.rewardsystem.Database

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
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
 * MySQL implementation of the database helper.
 * Handles database operations for MySQL databases.
 */
class MysqlHelper(private val plugin: Main) : DbHelper {
    private var connection: Connection? = null
    private lateinit var dbUrl: String
    private lateinit var username: String
    private lateinit var password: String

    init {
        connect()
    }

    /**
     * Connects to the MySQL database using configuration values.
     */
    override fun connect() {
        try {
            val configFile = File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) {
                plugin.saveResource("config.yml", false)
            }
            val config = YamlConfiguration.loadConfiguration(configFile)
            dbUrl = config.getString("Database.url", "jdbc:mysql://localhost:3306/minecraft").toString()
            username = config.getString("Database.username", "root").toString()
            password = config.getString("Database.password", "").toString()

            // Build connection URL
            val url = "jdbc:mysql://$dbUrl?useSSL=false"

            // Establish connection
            Class.forName("com.mysql.jdbc.Driver")
            connection = DriverManager.getConnection(url, username, password)

            // Create tables if they don't exist
            createTables()
            
            LoggingService.info("Connected to MySQL database at $dbUrl")
        } catch (e: Exception) {
            LoggingService.severe("Failed to connect to MySQL database: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Disconnects from the MySQL database and releases resources.
     */
    override fun disconnect() {
        try {
            if (connection != null && !connection!!.isClosed) {
                connection!!.close()
                LoggingService.info("Disconnected from MySQL database")
            }
        } catch (e: SQLException) {
            LoggingService.severe("Error closing MySQL connection: ${e.message}")
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
                "player_name VARCHAR(36) NOT NULL, " +
                "reward_id VARCHAR(36) NOT NULL, " +
                "expiry BIGINT NOT NULL, " +
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
            // Clear existing cooldowns for this reward ID
            var clearSql = "DELETE FROM cooldowns WHERE reward_id = ?"
            preparedStatement = connection?.prepareStatement(clearSql)
            preparedStatement?.setString(1, rewardId)
            preparedStatement?.executeUpdate()
            preparedStatement?.close()
            
            // Skip if no cooldowns to export
            if (cooldownsMap.isEmpty()) {
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
            
            LoggingService.debug("Exported ${cooldownsMap.size} cooldowns for reward ID: $rewardId")
        } catch (e: SQLException) {
            LoggingService.severe("Error exporting cooldowns: ${e.message}")
            e.printStackTrace()
        } finally {
            preparedStatement?.close()
        }
    }
}