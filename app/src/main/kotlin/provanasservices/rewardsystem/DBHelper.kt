package provanasservices.rewardsystem

import org.bukkit.Bukkit
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Time
import java.util.UUID

public class DBHelper(val plugin: Main) {
    val path: String

    init {
        val dbFile = File(plugin.dataFolder, "database.db")
        if (!dbFile.exists()) {
            dbFile.parentFile.mkdirs()
            dbFile.createNewFile()
        }
        path = dbFile.absolutePath
    }

    private val DB_URL = "jdbc:sqlite:$path" // Path to your SQLite DB
    private lateinit var connection: Connection
    fun connect() {
        try {
            // Create a connection to the database
            connection = DriverManager.getConnection(DB_URL).also {
                println("Connection to SQLite has been established.")
            }
            val statement = connection.createStatement()
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cooldowns (player VARCHAR(36), reward_mob VARCHAR(255), cooldown INTEGER, PRIMARY KEY (player, reward_mob))")
        } catch (e: SQLException) {
            println(e.message)
            null
        }
    }

    fun exportCooldowns(map: Map<UUID, Long>, rewardMobId: String) {
        var preparedStatement: PreparedStatement? = null
        try {
            connection.autoCommit = false // Ensure auto-commit is disabled
            preparedStatement =
                connection.prepareStatement("INSERT OR REPLACE INTO cooldowns (player, cooldown, reward_mob) VALUES (?, ?, ?)")
            for ((player, cooldown) in map) {
                preparedStatement.setString(1, player.toString())
                preparedStatement.setLong(2, cooldown)
                preparedStatement.setString(3, rewardMobId)
                preparedStatement.addBatch()
            }
            var a: IntArray? = preparedStatement.executeBatch()
            connection.commit() // Explicitly commit the transaction
        } catch (e: SQLException) {
            println("Error executing batch: ${e.message}")
            connection.rollback() // Rollback in case of error
        } finally {
            try {
                preparedStatement?.close() // Ensure preparedStatement is closed
            } catch (e: SQLException) {
                println("Error closing PreparedStatement: ${e.message}")
            }
        }
    }

    fun importCooldowns(cooldowns: MutableMap<UUID, Long>, rewardMobId: String, currentTimeLong: Long) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val statement = connection.createStatement()

            val preparedStatement = connection.prepareStatement("DELETE FROM cooldowns WHERE cooldown < ?")
            preparedStatement.setLong(1, currentTimeLong)
            preparedStatement.executeUpdate()
            preparedStatement.close()

            val resultSet = statement.executeQuery("SELECT * FROM cooldowns WHERE reward_mob = '$rewardMobId'")
            while (resultSet.next()) {
                cooldowns[UUID.fromString(resultSet.getString("player"))] = resultSet.getLong("cooldown")
            }

            statement.close()
        })
    }

}