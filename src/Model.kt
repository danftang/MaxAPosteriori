import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import lib.*
import kotlin.math.ln
import kotlin.random.Random

class Model: Hamiltonian<Agent> {
    var X: Array<Array<MPVariable>>
    val requirementIndex: Map<Agent, Set<Int>>
    val deltaIndex: Map<Agent, Set<Int>>

    constructor(params: Params) : super() {
        for(i in 0..params.GRIDSIZESQ) {
            Predator(i).hamiltonian(this, params)
            Prey(i).hamiltonian(this, params)
        }
        X = emptyArray()
        requirementIndex = this.toRequirementMap()
        deltaIndex = this.toDeltaMap()
    }


    fun randomState(nAgents: Int): MutableMultiset<Agent> {
        val state = HashMultiset<Agent>()
        val states = requirementIndex.keys
        while(state.size < nAgents) {
            state.add(states.random())
        }
        return state
    }


    fun samplePath(startState: Multiset<Agent>) {
        var state = startState
        val distribution = MutableCategorical<Act<Agent>>()
        // initialise distribution
        startState.forEach { agent ->
            requirementIndex.get(agent)?.forEach { actIndex ->
                val act = this[actIndex]
                if(!distribution.containsKey(act)) {
                    val rate = act.rateFor(state)
                    if(rate != 0.0) distribution[act] = rate
                }
            }
        }

        // execute an event
        var time = 0.0
        val nextAct = distribution.sample()
        val totalRate = distribution.sum()
        time += Random.nextExponential(totalRate)
        state = nextAct.actOn(state)
        nextAct.additions.distinctSet.forEach { agent ->
            requirementIndex.get(agent)?.forEach { actIndex ->
                val act = this[actIndex]
                val rate = act.rateFor(state)
                if(rate != 0.0) distribution[act] = rate
            }

        }
        nextAct.deletions.forEach { agent ->
            requirementIndex.get(agent)?.forEach { actIndex ->
                val act = this[actIndex]
                val rate = act.rateFor(state)
                if(rate == 0.0)
                    distribution.remove(act)
                else
                    distribution[act] = rate
            }
        }
        //TODO: ######## FINISH THIS ##########
    }



    fun MAP(startState: Set<Agent>, observedState: Map<Agent,Int>, time: Double, nFactors: Int, nActs: Int): List<Act<Agent>> {
        val solver = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
        val termsPerFactor = this.size
        X = Array(nFactors) {
            solver.makeBoolVarArray(termsPerFactor)
        }

        addOneTermPerFactorConstraint(solver)
//        addFactorCommutationConstraint(solver)
        addSatisfyObservationConstraint(solver, observedState, startState)
        addSatisfyRequirementsConstraint(solver, startState)
        addTotalActsConstraint(solver, nActs)

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

        val orbit = ArrayList<Act<Agent>>()
        for(factor in nFactors-1 downTo 0) {
            for(term in 0 until termsPerFactor) {
                if(X[factor][term].solutionValue() == 1.0) orbit.add(this[term])
            }
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

    private fun addSatisfyObservationConstraint(solver: MPSolver, observedState: Map<Agent,Int>, startState: Set<Agent>) {
        println("adding observation constraint")
        for ((agent, nObserved) in observedState) {
            val nTotal = nObserved - if(startState.contains(agent)) 1.0 else 0.0
            val satisfyObservation = solver.makeConstraint(nTotal, nTotal)
            for (term in deltaIndex.getOrDefault(agent, emptySet())) {
                val d = this[term].delta(agent)
                for (factor in X.indices) {
                    satisfyObservation.setCoefficient(X[factor][term], d * 1.0)
                }
            }
        }
    }

    // all requirements in each factor must be satisfied by consequences of factors to the right
    private fun addSatisfyRequirementsConstraint(solver: MPSolver, startState: Set<Agent>) {
        println("adding requirement satisfaction constraint")
        val states = requirementIndex.keys
        for(state in states) {
            for(factor in X.indices) {
                val satisfyRequirement = solver.makeConstraint(
                    if(startState.contains(state)) -1.0 else 0.0,
                    Double.POSITIVE_INFINITY
                )
                for(term in requirementIndex.getOrDefault(state, emptySet())) {
                    satisfyRequirement.setCoefficient(X[factor][term], -1.0)
                }
                for(term in deltaIndex.getOrDefault(state, emptySet())) {
                    val d = this[term].delta(state)
                    for(satFactor in factor+1 until X.size) {
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