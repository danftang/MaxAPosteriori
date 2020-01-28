import lib.HashMultiset
import lib.Multiset
import lib.MutableMultiset
import lib.toMutableMultiset
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

data class Act<AGENT>(val requirements: Set<AGENT>, val consequences: Multiset<AGENT>, val rate: Double, val primaryAgent: AGENT) {
    val additions: MutableMultiset<AGENT>
        get() = consequences - requirements
    val secondaryRequirements: Set<AGENT>
        get() = requirements.minus(primaryAgent)

//    val deletions: Set<AGENT>
//        get() = requirements - consequences.counts.keys

    // TODO: hmmm...
    fun commutesWith(other: Act<AGENT>): Boolean {
        if(
            this.consequences.intersect(other.requirements).isEmpty() &&
            other.consequences.intersect(this.requirements).isEmpty()
        ) return true
        return false
    }

    fun delta(agent: AGENT): Int {
        return consequences.count(agent) - (if(requirements.contains(agent)) 1 else 0)
    }

    fun rateFor(state: Multiset<AGENT>): Double {
        return requirements.fold(rate) {r, agent -> r*state.count(agent)}
    }

    fun actOn(state: Multiset<AGENT>): MutableMultiset<AGENT> {
        val result = state.toMutableMultiset()
        if(!result.contains(primaryAgent)) throw(IllegalArgumentException("Applying act to state without primary agent"))
        deltas().forEach {entry ->
            if(entry.value > 0) {
                result.add(entry.key, entry.value)
            } else {
                if(!result.remove(entry.key, -entry.value))
                    throw(IllegalArgumentException("Applying act to state without enough agents"))
            }
        }
        return result
    }

    fun modifiedAgents(): Set<AGENT> {
        return additions.distinctSet.union(setOf(primaryAgent))
    }

    fun deltas(): Map<AGENT,Int> {
        val d = HashMap<AGENT,Int>()
        requirements.forEach {
            d[it] = -1
        }
        consequences.counts.forEach {
            d.compute(it.key) { agent, oldCount ->
                val newCount = (oldCount?:0) + it.value
                if(newCount == 0) null else newCount
            }
        }
        return d
    }

    override fun toString(): String {
        return "${requirements} -> ${consequences}"
    }
}