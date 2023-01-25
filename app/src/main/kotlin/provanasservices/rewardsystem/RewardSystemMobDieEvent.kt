package provanasservices.rewardsystem

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RewardSystemMobDieEvent(val damageMap: HashMap<String, Double>, val mobIndex: Int) : Event() {
    companion object{
        val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }



    override fun getHandlers(): HandlerList {
        return RewardSystemMobDieEvent.handlers
    }
}