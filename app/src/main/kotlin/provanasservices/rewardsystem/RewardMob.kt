package provanasservices.rewardsystem

class RewardMob {
    var radius = -1
    var rewardMessages: List<String>? = null
    var name: String? = null
    var type: String? = null
    var enabledWorld: String? = null
    var enabledRegion: String? = null
    var allRewards: List<String>? = null
    var rewards = HashMap<Int, List<String>>()
    val chanceRewards = HashMap<Int, List<ChanceReward>>()
    var allChanceRewards = ArrayList<ChanceReward>()
    fun nameEquals(name: String): Boolean {
        return if (this.name == null) true else this.name == name
    }

    fun typeEquals(type: String): Boolean {
        return if (this.type == null) true else this.type == type
    }

    fun worldEquals(world: String): Boolean {
        return if (enabledWorld == null) true else enabledWorld == world
    }

    data class ChanceReward(val chance: Int, val commands: String)
}