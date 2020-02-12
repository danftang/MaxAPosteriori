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
    val eventIndicators = HashMap<Event<AGENT>,MPVariable>()
    val footprintIndicators = HashMap<AGENT,MPVariable>()
    override val consequencesFootprint: Set<AGENT>
        get() = (potentialEvents.asSequence().flatMap { it.consequences.supportSet.asSequence() } + sources.asSequence()).toSet()
    val requirementsFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.requirements.asSequence() }.toSet()
    val absenceFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.absenceRequirements.asSequence() }.toSet()
    val allRequirementsFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.totalRequirements.asSequence() }.toSet()
    val committedState: Multiset<AGENT>
        get() = commitedEvents.flatMap { it.consequences }.toMultiset()


    constructor(hamiltonian: Hamiltonian<AGENT>, previousState: ModelState<AGENT>, observations: Multiset<AGENT>) {
        this.hamiltonian = hamiltonian
        this.previousState = previousState
        this.unsatisfiedObservations.addAll(observations)
    }

    fun addForwardEvents() {
         potentialEvents = hamiltonian.eventsPresenceSatisfiedBy(previousState.consequencesFootprint).toSet()
    }


    fun filterBackwardEvents(nextRequirementsFootprint: Set<AGENT>) {
        val activeActs = HashSet<Event<AGENT>>()
        val allRequirements = nextRequirementsFootprint.asSequence() + unsatisfiedObservations.asSequence()
        allRequirements.flatMapTo(activeActs) { agent ->
            hamiltonian.eventsWithConsequence(agent).asSequence()
        }
        potentialEvents = potentialEvents.intersect(activeActs)
    }


    fun setupPartialSolution(solver: MPSolver) {
        // setup vars
        eventIndicators.clear()
        potentialEvents.forEach {event ->
            eventIndicators[event] = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY, getNextVarId())
        }

        setupObservationConstraints(solver)
        val primaryConstraints = setupRequirementsConstraints(solver, -1.0) { setOf(it.primaryAgent) }
        previousState.addConsequencesToConstraints(primaryConstraints, 1.0)
        previousState.addSourcesToConstraints(primaryConstraints, -1.0, false)

//        setupRequirementsConstraints(solver, 100, -1) { it.secondaryRequirements }
        // TODO: Add secondary and absence constraints

        setupObjective(solver)
    }


    fun setupCompleteSolution(solver: MPSolver) {
        // setup vars
        println("Setting up complete solution")
        eventIndicators.clear()
        footprintIndicators.clear()
        potentialEvents.forEach {event ->
            eventIndicators[event] = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY, getNextVarId())
        }

        val footprintIndicatorGTConstraints = HashMap<AGENT,MPConstraint>()
        val footprintIndicatorLTConstraints = HashMap<AGENT,MPConstraint>()
        allRequirementsFootprint.forEach { agent ->
            val indicator = solver.makeIntVar(0.0,1.0,getNextVarId())
            val gtConstraint = solver.makeConstraint()
            gtConstraint.setCoefficient(indicator, 100.0)
            val ltConstraint = solver.makeConstraint()
            ltConstraint.setCoefficient(indicator, -1.0)
            footprintIndicators[agent] = indicator
            footprintIndicatorGTConstraints[agent] = gtConstraint
            footprintIndicatorLTConstraints[agent] = ltConstraint
        }
        previousState.addConsequencesToConstraints(footprintIndicatorGTConstraints, -1.0)
        previousState.addSourcesToConstraints(footprintIndicatorGTConstraints, 1.0, false)
        previousState.addConsequencesToConstraints(footprintIndicatorLTConstraints, 1.0)
        previousState.addSourcesToConstraints(footprintIndicatorLTConstraints, -1.0, false)

        setupObservationConstraints(solver)

        val primaryConstraints = setupRequirementsConstraints(solver, -1.0) { setOf(it.primaryAgent) }
        previousState.addConsequencesToConstraints(primaryConstraints, 1.0)
        previousState.addSourcesToConstraints(primaryConstraints, -1.0, true)

        val secondaryConstraints = setupRequirementsConstraints(solver, -1.0) { it.secondaryRequirements }
        secondaryConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(footprintIndicators[agent], 100.0)
            constraint.setBounds(0.0, Double.POSITIVE_INFINITY)
        }

        val absenceConstraints = setupRequirementsConstraints(solver, -1.0) { it.absenceRequirements }
        absenceConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(footprintIndicators[agent], -100.0)
            constraint.setBounds(-100.0, Double.POSITIVE_INFINITY)
        }

        setupObjective(solver)
    }


    fun applySolution() {
        val newCommitedEvents = ArrayList<Event<AGENT>>()
        eventIndicators.forEach { (event, mpVar) ->
            for(i in 1..mpVar.solutionValue().toInt()) newCommitedEvents.add(event)
        }
        // check secondary requirements are met
        newCommitedEvents.forEach {
            if(!previousState.sources.containsAll(it.secondaryRequirements))
                throw(IllegalStateException("Solution orbit does not meet secondary requirements for event $it"))
        }
        // check absence requirements are met
        newCommitedEvents.forEach {
            if(!previousState.sources.intersect(it.absenceRequirements).isEmpty())
                throw(IllegalStateException("Solution orbit does not meet absence requirements for event $it"))
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
                satisfyObservation.setCoefficient(eventIndicators[event], event.consequences.count(agent).toDouble())
            }
        }
    }


    // Sets up requirement constraints for this timestep such that for each required agent state
    // -previousMultiplier * sources_{t-1,agent} <=
    //      previousMultiplier * (sum_{event_{t-1}} event(consequences in agent)*i_event)
    //      + thisMultiplier * (sum_{event_t} agent \in requiredAgents(event)*i_event)
    fun setupRequirementsConstraints(solver: MPSolver, multiplier: Double, requiredAgents: (Event<AGENT>) -> Set<AGENT>): Map<AGENT, MPConstraint> {
        val requirementConstraints = HashMap<AGENT, MPConstraint>()
        eventIndicators.forEach { (event, mpvar) ->
            requiredAgents(event).forEach { agent ->
                val constraint = requirementConstraints.getOrPut(agent) { solver.makeConstraint() }
                constraint.setCoefficient(mpvar, multiplier)
            }
        }
        return requirementConstraints
    }


    fun setupObjective(solver: MPSolver) {
        val obj = solver.objective()
        eventIndicators.forEach { (event, mpVar) ->
            obj.setCoefficient(mpVar, ln(event.rate))
        }
    }


    // Adds the consequences of this timestep for the given agent state to the given constraint
    // so that
    // -multiplier * sources_agent <= multiplier*(sum_event event_{consequences in agent}) + ...
//    override fun addConsequencesToConstraint(constraint: MPConstraint, agent: AGENT, multiplier: Int, equality: Boolean) {
//        hamiltonian.eventsWithConsequence(agent).forEach {event ->
//            eventIndicators[event]?.also { mpVar ->
//                constraint.setCoefficient(mpVar, multiplier * event.consequences.count(agent).toDouble())
//            }
//        }
//        constraint.setBounds(
//            -multiplier * sources.count(agent).toDouble(),
//            if(equality) -multiplier * sources.count(agent).toDouble() else Double.POSITIVE_INFINITY
//        )
//    }


    override fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double) {
        constraints.forEach { (agent, constraint) ->
            hamiltonian.eventsWithConsequence(agent).forEach { event ->
                eventIndicators[event]?.also { mpVar ->
                    constraint.setCoefficient(mpVar, multiplier * event.consequences.count(agent).toDouble())
                }
            }
        }
    }

//    override fun addSourcesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double, equality: Boolean) {
//        constraints.forEach { (agent, constraint) ->
//            constraint.setBounds(
//                multiplier * sources.count(agent),
//                if(equality) multiplier * sources.count(agent) else Double.POSITIVE_INFINITY
//            )
//        }
//    }




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