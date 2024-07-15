package provanasservices.rewardsystem.Database

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import provanasservices.rewardsystem.Main
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.UUID

class MysqlHelper(private val plugin: Main) : DbHelper() {
    private var connection: Connection? = null
    private lateinit var dbUrl: String
    private lateinit var username: String
    private lateinit var password: String

    init {
        connect()
    }

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


            Class.forName("com.mysql.jdbc.Driver")
            connection = DriverManager.getConnection(dbUrl, username, password)
            println("Connection to MySQL has been established.")

            val statement = connection!!.createStatement()
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cooldowns (player VARCHAR(36), reward_mob VARCHAR(255), cooldown BIGINT, PRIMARY KEY (player, reward_mob))")
        } catch (e: ClassNotFoundException) {
            println("MySQL JDBC Driver not found.")
        } catch (e: SQLException) {
            println("Connection to MySQL failed: ${e.message}")
        }
    }

    override fun exportCooldowns(map: Map<UUID, Long>, rewardMobId: String) {
        var preparedStatement: PreparedStatement? = null
        try {
            connection?.autoCommit = false
            preparedStatement = connection?.prepareStatement("INSERT INTO cooldowns (player, cooldown, reward_mob) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE cooldown = VALUES(cooldown)")
            for ((player, cooldown) in map) {
                preparedStatement?.setString(1, player.toString())
                preparedStatement?.setLong(2, cooldown)
                preparedStatement?.setString(3, rewardMobId)
                preparedStatement?.addBatch()
            }
            preparedStatement?.executeBatch()
            connection?.commit()
        } catch (e: SQLException) {
            println("Error executing MySQL batch: ${e.message}")
            try {
                connection?.rollback()
            } catch (ex: SQLException) {
                println("Error rolling back MySQL transaction: ${ex.message}")
            }
        } finally {
            try {
                preparedStatement?.close()
            } catch (e: SQLException) {
                println("Error closing MySQL PreparedStatement: ${e.message}")
            }
        }
    }

    override fun importCooldowns(cooldowns: MutableMap<UUID, Long>, rewardMobId: String, currentTimeLong: Long) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val preparedStatement = connection?.prepareStatement("DELETE FROM cooldowns WHERE cooldown < ?")
                preparedStatement?.setLong(1, currentTimeLong)
                preparedStatement?.executeUpdate()
                preparedStatement?.close()

                val statement = connection?.createStatement()
                val resultSet = statement?.executeQuery("SELECT * FROM cooldowns WHERE reward_mob = '$rewardMobId'")
                while (resultSet?.next() == true) {
                    cooldowns[UUID.fromString(resultSet.getString("player"))] = resultSet.getLong("cooldown")
                }
                statement?.close()
            } catch (e: SQLException) {
                println("Error importing MySQL cooldowns: ${e.message}")
            }
        })
    }
}