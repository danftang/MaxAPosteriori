import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.*
import kotlin.math.ln
import kotlin.random.Random

class Model: Hamiltonian<Agent> {
    var X: Array<Array<MPVariable>>
    val requirementIndex: Map<Agent, Set<Int>>
    val primaryRequirementIndex: Map<Agent, Set<Int>>
    val deltaIndex: Map<Agent, Set<Int>>
    val allStates: Set<Agent>
        get() = requirementIndex.keys

    data class Observation(val realState: Multiset<Agent>, val observation: Multiset<Agent>)

    constructor(params: Params) : super() {
        for(i in 0..params.GRIDSIZESQ) {
            Predator(i).hamiltonian(this, params)
            Prey(i).hamiltonian(this, params)
        }
        X = emptyArray()
        requirementIndex = this.toRequirementMap()
        deltaIndex = this.toDeltaMap()
        primaryRequirementIndex = this.toPrimaryRequirementMap()
    }


    fun randomState(nAgents: Int): MutableMultiset<Agent> {
        val state = HashMultiset<Agent>()
        while(state.size < nAgents) {
            state.add(allStates.random())
        }
        return state
    }

    fun sampleContinuousTimePath(startState: Multiset<Agent>, time: Double): List<Pair<Double,Multiset<Agent>>> {
        val distribution = MutableCategorical<Act<Agent>>()
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
        val distribution = MutableCategorical<Act<Agent>>()

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
                val choices = MutableCategorical<Act<Agent>>()
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


    fun MutableCategorical<Act<Agent>>.updateRates(agent: Agent, state: Multiset<Agent>) {
        requirementIndex[agent]?.forEach { actIndex ->
            val act = this@Model[actIndex]
            val rate = act.rateFor(state)
            if(rate == 0.0)
                this.remove(act)
            else
                this[act] = rate
        }
    }

    fun MAP(startState: Multiset<Agent>, observations: List<Multiset<Agent>>): List<List<Act<Agent>>> {
        val solver = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
        val termsPerFactor = this.size
        val nFactors = observations.size - 1
        X = Array(nFactors) {
            solver.makeBoolVarArray(termsPerFactor)
        }

//        addOneTermPerFactorConstraint(solver)
//        addFactorCommutationConstraint(solver)
        addSatisfyObservationConstraint(solver, observations, startState)
        addSatisfySecondaryRequirementsConstraint(solver, startState)
        addSatisfyPrimaryRequirementsConstraint(solver, startState)
//        addTotalActsConstraint(solver, nActs)

        // set objective function
        val obj = solver.objective()
        for(term in 0 until termsPerFactor) {
            val weight = ln(this[term].rate)
            for(factor in 0 until nFactors) {
                obj.setCoefficient(X[factor][term], weight)
            }
        }
        obj.setMaximization()

        // Do solve
        println("solving for ${solver.numVariables()} variables and ${solver.numConstraints()} constraints")
        val solveState = solver.solve()
        println("solveState = $solveState")

//        val orbit = ArrayList<Act<Agent>>()
//        for(factor in nFactors-1 downTo 0) {
//            for(term in 0 until termsPerFactor) {
//                if(X[factor][term].solutionValue() == 1.0) orbit.add(this[term])
//            }
//        }
        val orbit = ArrayList<ArrayList<Act<Agent>>>(observations.size)
        for(factor in 0 until nFactors) {
            val step = ArrayList<Act<Agent>>()
            for(term in 0 until termsPerFactor) {
                if(X[factor][term].solutionValue() == 1.0) step.add(this[term])
            }
            orbit.add(step)
        }

        return orbit
    }


    private fun addOneTermPerFactorConstraint(solver: MPSolver) {
        println("adding one term per factor constraint")
        for(factor in X) {
            val oneTermPerFactor = solver.makeConstraint(1.0, 1.0)
            for(term in factor) {
                oneTermPerFactor.setCoefficient(term, 1.0)
            }
        }
    }


    // all terms in a factor must mutually commute
    private fun addFactorCommutationConstraint(solver: MPSolver) {
        val termsPerFactor = X[0].size
        for (firstTerm in 0 until termsPerFactor) {
            for (otherTerm in firstTerm until termsPerFactor) {
                if (!this[firstTerm].commutesWith(this[otherTerm])) {
                    for (factor in 0 until X.size) {
                        val doesntCommute = solver.makeConstraint(0.0, 1.0)
                        doesntCommute.setCoefficient(X[factor][firstTerm], 1.0)
                        doesntCommute.setCoefficient(X[factor][otherTerm], 1.0)
                    }
                }
            }
        }
    }

    private fun addSatisfyObservationConstraint(solver: MPSolver, observations: List<Multiset<Agent>>, startState: Multiset<Agent>) {
        println("adding observation constraint")
        for(observedFactor in 1 until observations.size) {
            for ((agent, nObserved) in observations[observedFactor].counts) {
                val nTotal = (nObserved - startState.count(agent)).toDouble()
                val satisfyObservation = solver.makeConstraint(nTotal, Double.POSITIVE_INFINITY)
                for (term in deltaIndex.getOrDefault(agent, emptySet())) {
                    val d = this[term].delta(agent)
                    for (factor in observedFactor-1 downTo 0) {
                        satisfyObservation.setCoefficient(X[factor][term], d * 1.0)
                    }
                }
            }
        }
    }

    // all requirements in each factor must be satisfied by consequences of factors to the right
    private fun addSatisfySecondaryRequirementsConstraint(solver: MPSolver, startState: Multiset<Agent>) {
        println("adding requirement satisfaction constraint")
        val MAX_OVERLAP = 100.0
        val states = requirementIndex.keys
        for(state in states) {
            for(factor in X.indices) {
                val satisfyRequirement = solver.makeConstraint(
                    -MAX_OVERLAP*startState.count(state).toDouble(),
                    Double.POSITIVE_INFINITY
                )
                for(term in requirementIndex.getOrDefault(state, emptySet())) {
                    if(state != this[term].primaryAgent) satisfyRequirement.setCoefficient(X[factor][term], -1.0)
                }
                for(term in deltaIndex.getOrDefault(state, emptySet())) {
                    val d = this[term].delta(state)
                    for(satFactor in factor-1 downTo 0) {
                        satisfyRequirement.setCoefficient(X[satFactor][term], d * MAX_OVERLAP)
                    }
                }
            }
        }
    }

    private fun addSatisfyPrimaryRequirementsConstraint(solver: MPSolver, startState: Multiset<Agent>) {
        println("adding primary requirement satisfaction constraint")
        val states = requirementIndex.keys
        for(state in states) {
            for(factor in X.indices) {
                val satisfyRequirement = solver.makeConstraint(
                    -startState.count(state).toDouble(),
                    -startState.count(state).toDouble()
                )
                for(term in primaryRequirementIndex.getOrDefault(state, emptySet())) {
                    satisfyRequirement.setCoefficient(X[factor][term], -1.0)
                }
                for(term in deltaIndex.getOrDefault(state, emptySet())) {
                    val d = this[term].delta(state)
                    for(satFactor in factor-1 downTo 0) {
                        satisfyRequirement.setCoefficient(X[satFactor][term], d * 1.0)
                    }
                }
            }
        }
    }


    // total number of acts is exactly nActs
    private fun addTotalActsConstraint(solver: MPSolver, nActs: Int) {
        println("adding total acts constraint")
        val totalNumberConstraint = solver.makeConstraint(nActs*1.0, nActs*1.0)
        for(term in X[0].indices) {
            for(factor in X.indices) {
                totalNumberConstraint.setCoefficient(X[factor][term], 1.0)
            }
        }
    }
}