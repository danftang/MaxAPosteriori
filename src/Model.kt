import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.*
import kotlin.math.ln
import kotlin.random.Random

class Model: Hamiltonian<Agent> {
    val solver: MPSolver
    var X: Array<Map<Int,MPVariable>> // integer variables associated with every act in every timestep

    data class Observation(val realState: Multiset<Agent>, val observation: Multiset<Agent>)

    constructor(params: Params) : super() {
        for(i in 0 until params.GRIDSIZESQ) {
            Predator(i).hamiltonian(this, params)
            Prey(i).hamiltonian(this, params)
        }
        X = emptyArray()
        solver = MPSolver("HamiltonianSolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
    }


    fun randomState(nAgents: Int): MutableMultiset<Agent> {
        val state = HashMultiset<Agent>()
        while(state.size < nAgents) {
            state.add(allStates.random())
        }
        return state
    }

    fun sampleContinuousTimePath(startState: Multiset<Agent>, time: Double): List<Pair<Double,Multiset<Agent>>> {
        val distribution = MutableCategorical<Event<Agent>>()
        // initialise distribution
        startState.forEach { distribution.updateRates(it, startState) }

        // initialise path
        val path = ArrayList<Pair<Double,Multiset<Agent>>>((time*distribution.sum()).toInt())
        path.add(Pair(0.0,startState))

        // generate events
        var t = Random.nextExponential(distribution.sum())
        var state = startState
        while(t < time) {
            val nextAct = distribution.sample()
            state = nextAct.actOn(state)
            path.add(Pair(t,state))
            nextAct.modifiedAgents().forEach { distribution.updateRates(it, state) }
            t += Random.nextExponential(distribution.sum())
        }
        return path
    }


    // timestepping
    fun sampleTimesteppingPath(startState: Multiset<Agent>, nSteps: Int): List<Multiset<Agent>> {

        // initialise path
        val path = ArrayList<Multiset<Agent>>(nSteps)
        path.add(startState)

        // generate events
        var state = startState
//        val orbit = ArrayList<ArrayList<Act<Agent>>>()
        for(t in 1..nSteps) {
            val lastState = state
//            val acts = ArrayList<Act<Agent>>()
            lastState.forEach {agent ->
                val choices = MutableCategorical<Event<Agent>>()
                primaryRequirementIndex[agent]?.forEach {actIndex ->
                    choices[this[actIndex]] = this[actIndex].rateFor(lastState)
                }
//                println("Choices are ${choices}")
                if(choices.size == 0) println("no choices for agent $agent from ${primaryRequirementIndex[agent]}")
                val nextAct = choices.sample()
//                acts.add(nextAct)
                state = nextAct.actOn(state)
            }
//            println("acts = $acts")
//            println("state = $state")
            path.add(state)
        }
        return path
    }


    fun generateObservations(startState: Multiset<Agent>, nSteps: Int, pObserve: Double): List<Observation> {
        val observations = ArrayList<Observation>(nSteps)

        sampleTimesteppingPath(startState, nSteps).forEach { realState ->
            val observedState = HashMultiset<Agent>()
            realState.forEach { agent ->
                if(Random.nextDouble() < pObserve) {
                    observedState.add(agent)
                }
            }
            observations.add(Observation(realState, observedState))
        }
        return(observations)
    }


    fun MutableCategorical<Event<Agent>>.updateRates(agent: Agent, state: Multiset<Agent>) {
        requirementIndex[agent]?.forEach { actIndex ->
            val act = this@Model[actIndex]
            val rate = act.rateFor(state)
            if(rate == 0.0)
                this.remove(act)
            else
                this[act] = rate
        }
    }

    fun MAP(startState: Multiset<Agent>, observations: List<Multiset<Agent>>): List<List<Event<Agent>>> {
        val termsPerFactor = this.size
        val nFactors = observations.size - 1

        val activeActs = getActiveActs(startState, observations)
        X = Array(nFactors) { factor ->
            activeActs[factor].associateWith { actIndex ->
                solver.makeIntVar(0.0,Double.POSITIVE_INFINITY,"${factor}:${actIndex}")
//                solver.makeBoolVar("${factor}:${actIndex}")
            }
        }

//        printFactors()

        addSatisfyObservationConstraint(observations)
        addSatisfySecondaryRequirementsConstraint(startState)
        addSatisfyPrimaryRequirementsConstraint(startState)

        // set objective function
        val obj = solver.objective()
        for(factor in 0 until nFactors) {
            for((actIndex, indicator) in X[factor]) {
                val weight = ln(this[actIndex].rate)
                obj.setCoefficient(indicator, weight)
            }
        }
        obj.setMaximization()

        // Do solve

        println("solving for ${solver.numVariables()} variables and ${solver.numConstraints()} constraints")
        val solveState = solver.solve()
        println("solveState = $solveState")

        val orbit = ArrayList<ArrayList<Event<Agent>>>(observations.size)
        for(factor in 0 until nFactors) {
            val step = ArrayList<Event<Agent>>()
            for((actIndex, indicator) in X[factor]) {
                for(i in 1..indicator.solutionValue().toInt()) step.add(this[actIndex])
            }
            orbit.add(step)
        }

        return orbit
    }


    fun getActiveActs(startState: Multiset<Agent>, observations: List<Multiset<Agent>>): List<Set<Int>> {
        val footprint = ArrayList<Set<Agent>>()
        val activeActs = ArrayList<Set<Int>>()

        // forward sweep
        footprint.add(startState.distinctSet)
        for(factor in 1 until observations.size-1) {
            val acts = getForwardActs(footprint.last())
            activeActs.add(acts)
            val forwardFootprint = HashSet<Agent>()
            acts.flatMapTo(forwardFootprint) { actIndex -> this[actIndex].consequences.distinctSet }
            footprint.add(forwardFootprint)
        }
        activeActs.add(getForwardActs(footprint.last()))
        footprint.add(observations[observations.lastIndex].distinctSet)

        // backward sweep
        for(factor in activeActs.lastIndex downTo 0) {
            val acts = getBackwardActs(footprint[factor+1])
            activeActs[factor] = activeActs[factor].intersect(acts)
            val backwardFootprint = HashSet<Agent>()
            acts.flatMapTo(backwardFootprint) { actIndex -> this[actIndex].requirements }
            footprint[factor] = footprint[factor].intersect(backwardFootprint.union(observations[factor].distinctSet))
        }
        return activeActs
    }


    fun getForwardActs(state: Set<Agent>): Set<Int> {
        val activeActs = HashSet<Int>()
        state.flatMapTo(activeActs) { agent ->
            primaryRequirementIndex[agent]
                ?.filter { actIndex -> state.containsAll(this[actIndex].requirements) }
                ?:emptySet()
        }
        return activeActs
    }


    fun getBackwardActs(state: Set<Agent>): Set<Int> {
        val activeActs = HashSet<Int>()
        state.flatMapTo(activeActs) { agent ->
            consequenceIndex.getOrDefault(agent, emptySet())
        }
        return activeActs
    }


//    private fun addOneTermPerFactorConstraint(solver: MPSolver) {
//        println("adding one term per factor constraint")
//        for(factor in X) {
//            val oneTermPerFactor = solver.makeConstraint(1.0, 1.0)
//            for(term in factor) {
//                oneTermPerFactor.setCoefficient(term, 1.0)
//            }
//        }
//    }


    // all terms in a factor must mutually commute
//    private fun addFactorCommutationConstraint(solver: MPSolver) {
//        val termsPerFactor = X[0].size
//        for (firstTerm in 0 until termsPerFactor) {
//            for (otherTerm in firstTerm until termsPerFactor) {
//                if (!this[firstTerm].commutesWith(this[otherTerm])) {
//                    for (factor in 0 until X.size) {
//                        val doesntCommute = solver.makeConstraint(0.0, 1.0)
//                        doesntCommute.setCoefficient(X[factor][firstTerm], 1.0)
//                        doesntCommute.setCoefficient(X[factor][otherTerm], 1.0)
//                    }
//                }
//            }
//        }
//    }


    private fun addSatisfyObservationConstraint(observations: List<Multiset<Agent>>) {
        for(observedFactor in 1 until observations.size) {
            for ((agent, nObserved) in observations[observedFactor].counts) {
                val satisfyObservation = solver.makeConstraint(nObserved.toDouble(), Double.POSITIVE_INFINITY)
//                println("adding observation constraint $observedFactor : $agent = $nObserved")
                for(term in consequenceIndex.getOrDefault(agent, emptySet())) {
                    X[observedFactor-1][term]?.also { indicator ->
                        satisfyObservation.setCoefficient(indicator,
                            this[term].consequences.count(agent).toDouble()
                        )
                    }
                }
            }
        }
    }

    // for each term, x, in factor t, the secondary requrements of x must be satisfied by the
    // consequences of factors t-1.
    // This is equivalent to requiring that the sum of all secondary requiremets of factor t
    // are satisfied by MAX_OVERLAP * the consequences of factor t-1.
    // The sum of secondary requirements of factor 0 must be satisfied by MAX_OVERAP*the start state
    private fun addSatisfySecondaryRequirementsConstraint(startState: Multiset<Agent>) {
        addSatisfyRequirementsConstraint(startState, 100) { it.secondaryRequirements }
//        println("adding requirement satisfaction constraint")
//        val MAX_OVERLAP = 100.0
//        val states = requirementIndex.keys
//
//        for(factor in X.indices) {
//            val secondaryFootprint = HashSet<Agent>()
//            X[factor].keys.flatMapTo(secondaryFootprint) { actIndex -> this[actIndex].secondaryRequirements }
//            for(secondaryAgent in secondaryFootprint) {
//                val secondaryRequirement: MPConstraint
//                if(factor == 0) {
//                    secondaryRequirement = solver.makeConstraint(
//                        -MAX_OVERLAP*startState.count(secondaryAgent).toDouble(),
//                        Double.POSITIVE_INFINITY
//                    )
//                } else {
//                    secondaryRequirement = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY)
//                    for(term in consequenceIndex.getOrDefault(secondaryAgent, emptySet())) {
//                        X[factor-1][term]?.also { indicator ->
//                            secondaryRequirement.setCoefficient(indicator, MAX_OVERLAP*this[term].consequences.count(secondaryAgent).toDouble())
//                        }
//                    }
//                }
//                for(term in primaryRequirementIndex.getOrDefault(secondaryAgent, emptySet())) {
//                    X[factor][term]?.also { indicator -> secondaryRequirement.setCoefficient(indicator, -1.0) }
//                }
//            }
//        }
    }

    // the sum of all primary requirements in factor t must be satisfied by the consequences
    // of factor t-1.
    // the sum of primary requirements of factor 0 must be satisfied by the start state
    private fun addSatisfyPrimaryRequirementsConstraint(startState: Multiset<Agent>) {
        addSatisfyRequirementsConstraint(startState, 1) { setOf(it.primaryAgent) }
//        println("adding primary requirement satisfaction constraint")
//        val states = requirementIndex.keys
//        for(factor in X.indices) {
//            val primaryFootprint = HashSet<Agent>()
//            X[factor].keys.mapTo(primaryFootprint) { actIndex -> this[actIndex].primaryAgent }
//            for(primaryAgent in primaryFootprint) {
//                val primaryRequirement: MPConstraint
//                if(factor == 0) {
//                    primaryRequirement = solver.makeConstraint(-startState.count(primaryAgent).toDouble(), Double.POSITIVE_INFINITY)
//                } else {
//                    primaryRequirement = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY)
//                    for(term in consequenceIndex.getOrDefault(primaryAgent, emptySet())) {
//                        X[factor-1][term]?.also { indicator ->
//                            primaryRequirement.setCoefficient(indicator, this[term].consequences.count(primaryAgent).toDouble())
//                        }
//                    }
//                }
//                for(term in primaryRequirementIndex.getOrDefault(primaryAgent, emptySet())) {
//                    X[factor][term]?.also { indicator -> primaryRequirement.setCoefficient(indicator, -1.0) }
//                }
//            }
//        }
    }


    private fun addSatisfyRequirementsConstraint(startState: Multiset<Agent>, maxOverlap: Int, requirements: (Event<Agent>) -> Set<Agent>) {
        for(factor in X.indices) {
            val requirementFootprint = HashSet<Agent>()
            X[factor].keys.flatMapTo(requirementFootprint) { actIndex -> requirements(this[actIndex]) }
            for(requiredAgent in requirementFootprint) {
//                println("adding requirement satisfaction constraint $factor : $requiredAgent")
                val fulfillRequirement = solver.makeConstraint()
                fulfillRequirement.setUb(Double.POSITIVE_INFINITY)
                if(factor == 0) {
                    fulfillRequirement.setLb(-maxOverlap*startState.count(requiredAgent).toDouble())
                } else {
                    fulfillRequirement.setLb(0.0)
                    for(term in consequenceIndex.getOrDefault(requiredAgent, emptySet())) {
                        X[factor-1][term]?.also { indicator ->
                            val consequenceCount = this[term].consequences.count(requiredAgent)
                            if(consequenceCount != 0)
                                fulfillRequirement.setCoefficient(indicator, maxOverlap*consequenceCount.toDouble())
                        }
                    }
                }
                for((actIndex, indicator) in X[factor]) {
                    if(requirements(this[actIndex]).contains(requiredAgent)) {
                        fulfillRequirement.setCoefficient(indicator, -1.0)
                    }
                }
            }
        }
    }


    // total number of acts is exactly nActs
//    private fun addTotalActsConstraint(solver: MPSolver, nActs: Int) {
//        println("adding total acts constraint")
//        val totalNumberConstraint = solver.makeConstraint(nActs*1.0, nActs*1.0)
//        for(term in X[0].indices) {
//            for(factor in X.indices) {
//                totalNumberConstraint.setCoefficient(X[factor][term], 1.0)
//            }
//        }
//    }

    private fun getActsWithSecondaryRequirement(agent: Agent): Set<Int> {
        val secondaryReq = HashSet<Int>()
        requirementIndex[agent]?.filterTo(secondaryReq) { actIndex -> this[actIndex].primaryAgent != agent }
        return secondaryReq
    }

    fun printFactors() {
        println("Factors are:")
        X.forEach {  factor ->
            println(factor.keys.map { this[it] })
        }
        println()
    }
}