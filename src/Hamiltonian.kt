open class Hamiltonian<AGENT>: ArrayList<Event<AGENT>>() {
    val requirementIndex: Map<AGENT, Set<Int>>          by lazy { indexBy { it.requirements } }
    val primaryRequirementIndex: Map<AGENT, Set<Int>>   by lazy { indexBy { setOf(it.primaryAgent) } }
    val consequenceIndex: Map<AGENT, Set<Int>>          by lazy { indexBy { it.consequences } }
    val secondaryRequirementIdnex: Map<AGENT, Set<Int>> by lazy { indexBy { it.requirements.minus(it.primaryAgent) } }
    val deltaIndex: Map<AGENT, Set<Int>>                by lazy { indexBy {
            it.requirements.minus(it.consequences).union(it.consequences.minus(it.requirements))
        }
    }
    val allStates: Set<AGENT>
        get() = requirementIndex.keys

    // TODO: Change the indices to point directly at events
    fun eventsWithRequirement(agent: AGENT) = requirementIndex[agent]?.map { this[it] }?:emptyList()
    fun eventsWithPrimaryRequirement(agent: AGENT) = primaryRequirementIndex[agent]?.map { this[it] }?:emptyList()
    fun eventsWithSecondaryRequirement(agent: AGENT) = secondaryRequirementIdnex[agent]?.map { this[it] }?:emptyList()
    fun eventsWithConsequence(agent: AGENT) = consequenceIndex[agent]?.map { this[it] }?:emptyList()
    fun eventsThatChange(agent: AGENT) = deltaIndex[agent]?.map {this[it]}?:emptyList()


    fun eventsSatisfiedBy(footprint: Set<AGENT>): List<Event<AGENT>> {
        return footprint.flatMap { agent ->
            eventsWithPrimaryRequirement(agent)
                .filter { event -> footprint.containsAll(event.requirements) }

        }
    }


    fun <K>indexBy(keySetSelector: (Event<AGENT>) -> Set<K>): Map<K,Set<Int>> {
        val index = HashMap<K, MutableSet<Int>>()
        this.forEachIndexed { i, event ->
            keySetSelector(event).forEach { key ->
                index.getOrPut(key,{HashSet()}).add(i)
            }
        }
        return index
    }

    fun samplePathFromPrior(startState: Set<Agent>, time: Double) {

    }

}