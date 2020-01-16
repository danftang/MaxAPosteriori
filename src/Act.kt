data class Act<AGENT>(val requirements: Set<AGENT>, val consequences: Multiset<AGENT>, val rate: Double) {
    val additions: Multiset<AGENT>
        get() = consequences - requirements

    val deletions: Set<AGENT>
        get() = requirements - consequences.distinctSet

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


}