import lib.HashMultiset
import lib.Multiset
import lib.MutableMultiset
import lib.toMutableMultiset

data class Act<AGENT>(val requirements: Set<AGENT>, val consequences: Multiset<AGENT>, val rate: Double) {
    val additions: MutableMultiset<AGENT>
        get() = consequences - requirements

    val deletions: Set<AGENT>
        get() = requirements - consequences.counts.keys

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
        return requirements.fold(rate) {a, agent -> a*state.count(agent)}
    }

    fun actOn(state: Multiset<AGENT>): MutableMultiset<AGENT> {
        val result = state.toMutableMultiset()
        requirements.forEach {
            if(!result.remove(it)) return HashMultiset()
        }
        consequences.counts.forEach {
            result.add(it.key, it.value)
        }
        return result
    }

}