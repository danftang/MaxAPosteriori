open class Hamiltonian<AGENT>: ArrayList<Act<AGENT>>() {

    // map from agents to act indices that change the number of agents in that state
    fun toDeltaMap(): Map<AGENT, Set<Int>> {
        val deltaMap = HashMap<AGENT, HashSet<Int>>()
        this.forEachIndexed { i, act ->
            val deltaAgents =
                act.requirements.minus(act.consequences).union(act.consequences.minus(act.requirements))
            deltaAgents.forEach { deltaAgent ->
                deltaMap.getOrPut(deltaAgent,{HashSet()}).add(i)
            }
        }
        return deltaMap
    }

    // map from agents to acts that require that agent
    fun toRequirementMap(): Map<AGENT, Set<Int>> {
        val requirementsMap = HashMap<AGENT, HashSet<Int>>()
        this.forEachIndexed { i, act ->
            act.requirements.forEach { requirement ->
                requirementsMap.getOrPut(requirement,{HashSet()}).add(i)
            }
        }
        return requirementsMap
    }

    fun samplePathFromPrior(startState: Set<Agent>, time: Double) {

    }

}