package provanasservices.rewardsystem.Database

import java.util.*

open class DbHelper {
    open fun connect() {
        println("Connecting to database...")
    }

    open fun exportCooldowns(map: Map<UUID, Long>, rewardMobId: String) {
        println("Exporting cooldowns to database...")
    }

    open fun importCooldowns(cooldowns: MutableMap<UUID, Long>, rewardMobId: String, currentTimeLong: Long) {
        println("Importing cooldowns from database...")
    }
}