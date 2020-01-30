import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.HashMultiset
import lib.Multiset
import lib.toMultiset
import kotlin.math.ln

class Timestep<AGENT>: ModelState<AGENT> {
    val hamiltonian: Hamiltonian<AGENT>
    val previousState: ModelState<AGENT>
    val unsatisfiedObservations = HashMultiset<AGENT>()
    val satisfiedObservations = HashMultiset<AGENT>()
    var potentialEvents: Set<Event<AGENT>> = setOf()
    val commitedEvents = ArrayList<Event<AGENT>>()
    override val sources = HashMultiset<AGENT>()
    override val indicators = HashMap<Event<AGENT>,MPVariable>()
    override val consequencesFootprint: Set<AGENT>
        get() = (potentialEvents.asSequence().flatMap { it.consequences.distinctSet.asSequence() } + sources.asSequence()).toSet()
    val requirementsFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.requirements.asSequence() }.toSet()
    val committedState: Multiset<AGENT>
        get() = commitedEvents.flatMap { it.consequences }.toMultiset()


    constructor(hamiltonian: Hamiltonian<AGENT>, previousState: ModelState<AGENT>, observations: Multiset<AGENT>) {
        this.hamiltonian = hamiltonian
        this.previousState = previousState
        this.unsatisfiedObservations.addAll(observations)
    }

    fun addForwardEvents() {
         potentialEvents = hamiltonian.eventsSatisfiedBy(previousState.consequencesFootprint).toSet()
    }


    fun filterBackwardEvents(nextRequirementsFootprint: Set<AGENT>) {
        val activeActs = HashSet<Event<AGENT>>()
        val allRequirements = nextRequirementsFootprint.asSequence() + unsatisfiedObservations.asSequence()
        allRequirements.flatMapTo(activeActs) { agent ->
            hamiltonian.eventsWithConsequence(agent).asSequence()
        }
        potentialEvents = potentialEvents.intersect(activeActs)
    }


    fun setupProblem(solver: MPSolver) {
        // setup vars
        indicators.clear()
        potentialEvents.forEach {event ->
            indicators[event] = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY, getNextVarId())
        }

        setupObservationConstraints(solver)
        setupRequirementsConstraints(solver, 1) { listOf(it.primaryAgent) }
        setupRequirementsConstraints(solver, 100) { it.secondaryRequirements }
        setupObjective(solver)
    }


    fun applySolution() {
        val newCommitedEvents = ArrayList<Event<AGENT>>()
        indicators.forEach {(event, mpVar) ->
            for(i in 1..mpVar.solutionValue().toInt()) newCommitedEvents.add(event)
        }
        // check secondary requirements are met
        newCommitedEvents.forEach {
            if(!previousState.sources.containsAll(it.secondaryRequirements))
                throw(IllegalStateException("Solution orbit does not meet secondary requirements for event $it"))
        }
        newCommitedEvents.forEach { event ->
            val primaryAgentRemoved = previousState.sources.remove(event.primaryAgent)
            if(!primaryAgentRemoved) throw(IllegalStateException("Solution orbit does not meet primary requirements for event $event"))
            sources.addAll(event.consequences)
        }
        // check observations are met
        if(!unsatisfiedObservations.isSubsetOf(sources)) throw(IllegalStateException("Solution orbit does not satisfy observations."))
        satisfiedObservations.addAll(unsatisfiedObservations)
        unsatisfiedObservations.clear()
        commitedEvents.addAll(newCommitedEvents)
    }



    fun setupObservationConstraints(solver: MPSolver) {
        for ((agent, nObserved) in unsatisfiedObservations.counts) {
            val satisfyObservation = solver.makeConstraint(nObserved.toDouble(), Double.POSITIVE_INFINITY)
            val relevantEvents = hamiltonian.eventsWithConsequence(agent).intersect(potentialEvents)
            relevantEvents.forEach { event ->
                satisfyObservation.setCoefficient(indicators[event], event.consequences.count(agent).toDouble())
            }
        }
    }


    fun setupRequirementsConstraints(solver: MPSolver, multiplier: Int, requiredAgents: (Event<AGENT>) -> Collection<AGENT>) {
        val requirementConstraints = HashMap<AGENT, MPConstraint>()
        indicators.forEach {(event, mpvar) ->
            requiredAgents(event).forEach { agent ->
                val constraint = requirementConstraints.getOrPut(agent) {
                    val c = solver.makeConstraint()
                    previousState.addConsequencesToConstraint(c, agent, multiplier)
                    c
                }
                constraint.setCoefficient(mpvar, -1.0)
            }
        }
    }

    fun setupObjective(solver: MPSolver) {
        val obj = solver.objective()
        indicators.forEach {(event, mpVar) ->
            obj.setCoefficient(mpVar, ln(event.rate))
        }
    }


    override fun addConsequencesToConstraint(constraint: MPConstraint, agent: AGENT, multiplier: Int) {
        hamiltonian.eventsWithConsequence(agent).forEach {event ->
            indicators[event]?.also { mpVar ->
                constraint.setCoefficient(mpVar, multiplier * event.consequences.count(agent).toDouble())
            }
        }
        constraint.setBounds(-sources.count(agent).toDouble(), Double.POSITIVE_INFINITY)
    }


    fun rollback(): Boolean {
        if(sources.isEmpty()) return false
        sources.clear()
        previousState.sources.addAll(commitedEvents.map { it.primaryAgent })
        unsatisfiedObservations.addAll(satisfiedObservations)
        satisfiedObservations.clear()
        commitedEvents.clear()
        return true
    }


    companion object {
        var mpVarId = 0

        fun getNextVarId(): String = mpVarId++.toString()
    }


}