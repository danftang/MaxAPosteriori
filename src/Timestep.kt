import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.HashMultiset
import lib.Multiset
import lib.toMultiset
import java.io.Serializable
import kotlin.math.ln

class Timestep<AGENT>: UnknownModelState<AGENT>, Serializable {
    override val sources = HashMultiset<AGENT>()
    val hamiltonian: Hamiltonian<AGENT>
    val previousState: UnknownModelState<AGENT>
    val observations = HashMultiset<AGENT>()
    var potentialEvents: Set<Event<AGENT>> = setOf()
    val committedEvents = ArrayList<Event<AGENT>>()
    val eventIndicators = HashMap<Event<AGENT>,MPVariable>()

    // TODO: Agents should be able to use committed consequences to fulfil their secondary requirements!
    // ***************************************************************
    override val consequencesFootprint: Set<AGENT>
        get() = (potentialEvents.asSequence().flatMap { it.consequences.supportSet.asSequence() } + sources.asSequence()).toSet()
    val requirementsFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.requirements.asSequence() }.toSet()
    val committedState: Multiset<AGENT>
        get() = committedEvents.flatMap { it.consequences }.toMultiset()
    val committedAbsenceFootprint: Set<AGENT>
        get() = committedEvents.asSequence().flatMap { it.absenceRequirements.asSequence() }.toSet()

//    val committedPrimaryRequirements: Set<AGENT>
//        get() = committedEvents.asSequence().map { it.primaryAgent }.toSet()

//    val absenceFootprint: Set<AGENT>
//        get() = potentialEvents.asSequence().flatMap { it.absenceRequirements.asSequence() }.toSet()



    constructor(hamiltonian: Hamiltonian<AGENT>, previousState: UnknownModelState<AGENT>, observations: Multiset<AGENT>) {
        this.hamiltonian = hamiltonian
        this.previousState = previousState
        this.observations.addAll(observations)
    }

    override fun hasIndicators() = eventIndicators.isNotEmpty()

    fun addForwardEvents() {
         potentialEvents = hamiltonian.eventsPresenceSatisfiedBy(previousState.consequencesFootprint).toSet()
    }


    fun observationsAreSatisfied(): Boolean = committedEvents.isNotEmpty()


    fun filterBackwardEvents(nextRequirementsFootprint: Set<AGENT>) {
        val activeActs = HashSet<Event<AGENT>>()
        val allRequirements = nextRequirementsFootprint.asSequence() + observations.asSequence()
        allRequirements.flatMapTo(activeActs) { agent ->
            hamiltonian.eventsWithConsequence(agent).asSequence()
        }
        potentialEvents = potentialEvents.intersect(activeActs)
    }


    fun setupProblem(solver: MPSolver, isComplete: Boolean) {
        // setup vars
        eventIndicators.clear()
        potentialEvents.forEach {event ->
            eventIndicators[event] = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY, getNextVarId())
        }


        setupObservationConstraints(solver)

        if(previousState.hasIndicators()) setupCommittedAbsenceConstraints(solver)
        val consequenceCounters = setupConsequenceCounters(solver)
        val footprintIndicators = setupFootprintIndicators(solver, consequenceCounters)

        val primaryConstraints = setupRequirementsConstraints(solver, -1.0) { setOf(it.primaryAgent) }
        primaryConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(consequenceCounters[agent], 1.0)
            if(isComplete) constraint.setBounds(0.0,0.0) else constraint.setBounds(0.0,Double.POSITIVE_INFINITY)
        }

//        val footprintIndicators = setupFootprintIndicators(solver)
//        setupObservationConstraints(solver)
//        val primaryConstraints = setupRequirementsConstraints(solver, -1.0) { setOf(it.primaryAgent) }
//        previousState.addConsequencesToConstraints(primaryConstraints, 1.0)
//        previousState.addSourcesToConstraints(primaryConstraints, -1.0, isComplete)


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


    // Sets up indicator variables for the agents that are present
    // in the consequences of the previous timestep
//    fun setupFootprintIndicators(solver: MPSolver): Map<AGENT,MPVariable> {
//        val committedAbsences = committedAbsenceFootprint
//        val allRequirements =
//            potentialEvents
//                .asSequence()
//                .flatMap { it.totalRequirements.asSequence() }
//                .filter { !committedAbsences.contains(it) }
//                .toSet()
//
//        val absenceConstraints = committedAbsences.associateWith { solver.makeConstraint(0.0, 0.0) }
//        previousState.addConsequencesToConstraints(absenceConstraints, 1.0)
//
//        val footprintIndicators = HashMap<AGENT,MPVariable>()
//        val footprintIndicatorGTConstraints = HashMap<AGENT,MPConstraint>()
//        val footprintIndicatorLTConstraints = HashMap<AGENT,MPConstraint>()
//        allRequirements.forEach { agent ->
//            val indicator = solver.makeIntVar(0.0,1.0,getNextVarId())
//            val gtConstraint = solver.makeConstraint()
//            gtConstraint.setCoefficient(indicator, 100.0)
//            val ltConstraint = solver.makeConstraint()
//            ltConstraint.setCoefficient(indicator, -1.0)
//            footprintIndicators[agent] = indicator
//            footprintIndicatorGTConstraints[agent] = gtConstraint
//            footprintIndicatorLTConstraints[agent] = ltConstraint
//        }
//        previousState.addConsequencesToConstraints(footprintIndicatorGTConstraints, -1.0)
//        previousState.addSourcesToConstraints(footprintIndicatorGTConstraints, 1.0, false)
//        previousState.addConsequencesToConstraints(footprintIndicatorLTConstraints, 1.0)
//        previousState.addSourcesToConstraints(footprintIndicatorLTConstraints, -1.0, false)
//        return footprintIndicators
//    }


    fun setupCommittedAbsenceConstraints(solver: MPSolver) {
        val absenceConstraints = committedAbsenceFootprint.associateWith { solver.makeConstraint(0.0, 0.0) }
        previousState.addConsequencesToConstraints(absenceConstraints, 1.0)
    }

    fun setupFootprintIndicators(solver: MPSolver, consequenceCounters: Map<AGENT,MPVariable>): Map<AGENT,MPVariable> {
        val footprintIndicators = HashMap<AGENT,MPVariable>()
        consequenceCounters.forEach { (agent, counter)->
            val indicator = solver.makeIntVar(0.0,1.0,getNextVarId())
            val gtConstraint = solver.makeConstraint(0.0,Double.POSITIVE_INFINITY)
            gtConstraint.setCoefficient(indicator, 100.0)
            gtConstraint.setCoefficient(counter, -1.0)
            val ltConstraint = solver.makeConstraint(0.0,Double.POSITIVE_INFINITY)
            ltConstraint.setCoefficient(indicator, -1.0)
            ltConstraint.setCoefficient(counter, 1.0)
            footprintIndicators[agent] = indicator
        }
        return footprintIndicators
    }

    fun setupConsequenceCounters(solver: MPSolver): Map<AGENT,MPVariable> {
        val committedAbsences = committedAbsenceFootprint
        val allRequirements =
            potentialEvents
                .asSequence()
                .flatMap { it.totalRequirements.asSequence() }
                .filter { !committedAbsences.contains(it) }
                .toSet()

        val footprintCounters = HashMap<AGENT,MPVariable>()
        val footprintConstraints = HashMap<AGENT,MPConstraint>()
        allRequirements.forEach { agent ->
            val indicator = solver.makeIntVar(0.0,Double.POSITIVE_INFINITY,getNextVarId())
            val constraint = solver.makeConstraint()
            constraint.setCoefficient(indicator, 1.0)
            footprintCounters[agent] = indicator
            footprintConstraints[agent] = constraint
        }
        previousState.addConsequencesToConstraints(footprintConstraints, -1.0)
        previousState.addSourcesToConstraints(footprintConstraints, 1.0, true)
        return footprintCounters
    }



    fun applySolution() {
        val newCommitedEvents = ArrayList<Event<AGENT>>()
        eventIndicators.forEach { (event, mpVar) ->
            for(i in 1..mpVar.solutionValue().toInt()) newCommitedEvents.add(event)
        }
        eventIndicators.clear()
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
//        if(!observations.isSubsetOf(sources)) throw(IllegalStateException("Solution orbit does not satisfy observations."))
        committedEvents.addAll(newCommitedEvents)
        val committedConsequences = committedEvents.flatMap { it.consequences }.toMultiset()
        if(!observations.isSubsetOf(committedConsequences)) throw(IllegalStateException("Solution orbit does not satisfy observations."))
    }



    fun setupObservationConstraints(solver: MPSolver) {
        if(observationsAreSatisfied()) return
        for ((agent, nObserved) in observations.counts) {
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
        obj.setMaximization()
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
        if(committedEvents.isEmpty()) return false
        sources.clear()
        previousState.sources.addAll(committedEvents.map { it.primaryAgent })
        committedEvents.clear()
        return true
    }


    companion object {
        var mpVarId = 0

        fun getNextVarId(): String = mpVarId++.toString()
    }


}