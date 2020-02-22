import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.*
import java.io.Serializable
import kotlin.math.ln

class Timestep<AGENT>: UnknownModelState<AGENT>, Serializable {
//    override val sources = HashMultiset<AGENT>()
    val hamiltonian: Hamiltonian<AGENT>
    val previousState: UnknownModelState<AGENT>
    val observations = HashMultiset<AGENT>()
    var potentialEvents: Set<Event<AGENT>> = setOf()
    val committedEvents = ArrayList<Event<AGENT>>()
    val eventIndicators = HashMap<Event<AGENT>,MPVariable>()
    val endStateIndicators = HashMap<AGENT,MPVariable>()
    val endStateBinaryIndicators = HashMap<AGENT,MPVariable>()
    val M = 100.0 // Maximum multiplicity


    override val committedConsequences: Multiset<AGENT>
        get() = committedEvents.asSequence().flatMap { it.consequences.asSequence() }.toMultiset()

    override val potentialConsequencesFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.consequences.supportSet.asSequence() }.toSet()

    val potentialRequirementsFootprint: Set<AGENT>
        get() = potentialEvents.asSequence().flatMap { it.requirements.asSequence() }.toSet()

    val committedAbsenceFootprint: Set<AGENT>
        get() = committedEvents.asSequence().flatMap { it.absenceRequirements.asSequence() }.toSet()

    val hangingAgents: Multiset<AGENT>
        get() = previousState.committedConsequences - committedPrimaryRequirements

    val committedPrimaryRequirements: Multiset<AGENT>
        get() = committedEvents.asSequence().map { it.primaryAgent }.toMultiset()

//    val absenceFootprint: Set<AGENT>
//        get() = potentialEvents.asSequence().flatMap { it.absenceRequirements.asSequence() }.toSet()



    constructor(hamiltonian: Hamiltonian<AGENT>, previousState: UnknownModelState<AGENT>, observations: Multiset<AGENT>) {
        this.hamiltonian = hamiltonian
        this.previousState = previousState
        this.observations.addAll(observations)
    }

    fun addForwardEvents() {
        val potentialConsequences = previousState.potentialConsequencesFootprint
        val previousCommittedFootprint = previousState.committedConsequences.supportSet
        val potentialPrimaryAgents = potentialConsequences + hangingAgents.supportSet
        val potentialPresentAgents = potentialConsequences + previousCommittedFootprint

        potentialEvents = hamiltonian
            .eventsPresenceSatisfiedBy(potentialPresentAgents)
            .filter { potentialPrimaryAgents.contains(it.primaryAgent) }
            .filter { it.absenceRequirements.intersect(previousCommittedFootprint).isEmpty() }
            .toSet()
    }


    fun observationsAreSatisfied(): Boolean = committedEvents.isNotEmpty()


    fun filterBackwardEvents(nextRequirementsFootprint: Set<AGENT>) {
        val activeActs = HashSet<Event<AGENT>>()
        val allRequirements = if(observationsAreSatisfied())
            nextRequirementsFootprint.asSequence()
        else
            nextRequirementsFootprint.asSequence() + observations.asSequence()
        allRequirements.flatMapTo(activeActs) { agent ->
            hamiltonian.eventsWithConsequence(agent).asSequence()
        }
        potentialEvents = potentialEvents.intersect(activeActs)
    }


    fun setupProblem(solver: MPSolver, isComplete: Boolean) {
        // setup vars
        eventIndicators.clear()
        endStateIndicators.clear()
        endStateBinaryIndicators.clear()
        potentialEvents.forEach {event ->
            eventIndicators[event] = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY, getNextVarId())
        }

        setupObservationConstraints(solver)

        val primaryConstraints = setupRequirementsConstraints(solver, -1.0) { setOf(it.primaryAgent) }
        primaryConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(previousState.getEndStateVriable(solver, agent), 1.0)
            val primaryCount = committedPrimaryRequirements.count(agent).toDouble()
            if(isComplete)
                constraint.setBounds(primaryCount, primaryCount)
            else
                constraint.setBounds(primaryCount,Double.POSITIVE_INFINITY)
        }

        val secondaryConstraints = setupRequirementsConstraints(solver, -1.0) { it.secondaryRequirements }
        secondaryConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(previousState.getEndStateBinaryVriable(solver, agent), M)
            constraint.setBounds(0.0, Double.POSITIVE_INFINITY)
        }

        val absenceConstraints = setupRequirementsConstraints(solver, 1.0) { it.absenceRequirements }
        absenceConstraints.forEach { (agent, constraint) ->
            constraint.setCoefficient(previousState.getEndStateBinaryVriable(solver, agent), M)
            constraint.setBounds(0.0, if(committedAbsenceFootprint.contains(agent)) M-1.0 else M)
        }

        setupObjective(solver)
    }


//
//    fun setupCommittedAbsenceConstraints(solver: MPSolver) {
//        val absenceConstraints = committedAbsenceFootprint.associateWith { solver.makeConstraint(0.0, 0.0) }
//        previousState.addConsequencesToConstraints(absenceConstraints, 1.0)
//    }
//
//    fun setupFootprintIndicators(solver: MPSolver, consequenceCounters: Map<AGENT,MPVariable>): Map<AGENT,MPVariable> {
//        val footprintIndicators = HashMap<AGENT,MPVariable>()
//        consequenceCounters.forEach { (agent, counter)->
//            val indicator = solver.makeIntVar(0.0,1.0,getNextVarId())
//            val gtConstraint = solver.makeConstraint(0.0,Double.POSITIVE_INFINITY)
//            gtConstraint.setCoefficient(indicator, 100.0)
//            gtConstraint.setCoefficient(counter, -1.0)
//            val ltConstraint = solver.makeConstraint(0.0,Double.POSITIVE_INFINITY)
//            ltConstraint.setCoefficient(indicator, -1.0)
//            ltConstraint.setCoefficient(counter, 1.0)
//            footprintIndicators[agent] = indicator
//        }
//        return footprintIndicators
//    }

//    fun setupConsequenceCounters(solver: MPSolver): Map<AGENT,MPVariable> {
////        val committedAbsences = committedAbsenceFootprint
//        val allPotentialRequirements =
//            potentialEvents
//                .asSequence()
//                .flatMap { it.totalRequirements.asSequence() }
////                .filter { !committedAbsences.contains(it) }
//                .toSet()
//
//        val footprintCounters = HashMap<AGENT,MPVariable>()
//        val footprintConstraints = HashMap<AGENT,MPConstraint>()
//        allPotentialRequirements.forEach { agent ->
//            val indicator = solver.makeIntVar(0.0,Double.POSITIVE_INFINITY,getNextVarId())
//            val constraint = solver.makeConstraint()
//            constraint.setCoefficient(indicator, 1.0)
//            footprintCounters[agent] = indicator
//            footprintConstraints[agent] = constraint
//        }
//        previousState.addConsequencesToConstraints(footprintConstraints, -1.0)
//        previousState.addSourcesToConstraints(footprintConstraints, 1.0, true)
//        return footprintCounters
//    }



    fun applySolution() {
        val newCommitedEvents = ArrayList<Event<AGENT>>()
        eventIndicators.forEach { (event, mpVar) ->
            for(i in 1..mpVar.solutionValue().toInt()) newCommitedEvents.add(event)
        }
        eventIndicators.clear()
        endStateBinaryIndicators.clear()
        endStateIndicators.clear()
        committedEvents.addAll(newCommitedEvents)

        // check secondary requirements are met
        newCommitedEvents.forEach {
            if(!previousState.committedConsequences.containsAll(it.secondaryRequirements))
                throw(IllegalStateException("Solution orbit does not meet secondary requirements for event $it"))
        }

        // check absence requirements are met
        newCommitedEvents.forEach {
            if(!previousState.committedConsequences.intersect(it.absenceRequirements).isEmpty())
                throw(IllegalStateException("Solution orbit does not meet absence requirements for event $it"))
        }

        // check primary requirements are met
        if(!committedPrimaryRequirements.isSubsetOf(previousState.committedConsequences))
            throw(IllegalStateException("Solution orbit does not meet primary requirements"))

        // check observations are met
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


    // Creates a set of constraints such that for each member of requiredAgents for each potential event,
    // the following coefficients are added to the constraint
    // multiplier * (sum_{event_t} agent \in requiredAgents(event)*i_event)
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


//    override fun addConsequencesToConstraints(constraints: Map<AGENT, MPConstraint>, multiplier: Double) {
//        constraints.forEach { (agent, constraint) ->
//            hamiltonian.eventsWithConsequence(agent).forEach { event ->
//                eventIndicators[event]?.also { mpVar ->
//                    constraint.setCoefficient(mpVar, multiplier * event.consequences.count(agent).toDouble())
//                }
//            }
//        }
//    }

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
        committedEvents.clear()
        return true
    }


    companion object {
        var mpVarId = 0

        fun getNextVarId(): String = mpVarId++.toString()
    }

    override fun getEndStateVriable(solver: MPSolver, agent: AGENT): MPVariable {
        return endStateIndicators.getOrPut(agent) {
            val commitment = committedConsequences.count(agent).toDouble()
            val indicator = solver.makeIntVar(0.0, Double.POSITIVE_INFINITY,  getNextVarId())
            val constraint = solver.makeConstraint(commitment, commitment)
            constraint.setCoefficient(indicator, 1.0)
            hamiltonian.eventsWithConsequence(agent).forEach { event ->
                eventIndicators[event]?.also { eventIndicator ->
                    constraint.setCoefficient(eventIndicator, -event.consequences.count(agent).toDouble())
                }
            }
            indicator
        }
    }

    override fun getEndStateBinaryVriable(solver: MPSolver, agent: AGENT): MPVariable {
        return endStateBinaryIndicators.getOrPut(agent) {
            val psi_agent = getEndStateVriable(solver, agent)
            val binaryIndicator = solver.makeIntVar(0.0, 1.0, getNextVarId())
            val gtConstraint = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY)
            gtConstraint.setCoefficient(binaryIndicator, M)
            gtConstraint.setCoefficient(psi_agent, -1.0)
            val ltConstraint = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY)
            ltConstraint.setCoefficient(binaryIndicator, -1.0)
            ltConstraint.setCoefficient(psi_agent, 1.0)
            binaryIndicator
        }
    }


}