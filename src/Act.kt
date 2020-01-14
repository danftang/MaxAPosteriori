data class Act<AGENT>(val requirements: Set<AGENT>, val consequences: Set<AGENT>, val rate: Double) {
    fun commutesWith(other: Act<AGENT>): Boolean {
        if(
            this.consequences.union(other.requirements).isEmpty() &&
            other.consequences.union(this.requirements).isEmpty()
        ) return true
        return false
    }

    fun delta(agent: AGENT): Int {
        return (if(consequences.contains(agent)) 1 else 0) - (if(requirements.contains(agent)) 1 else 0)
    }
}