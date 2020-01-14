import com.google.ortools.linearsolver.MPSolver
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.random.Random

class Model: Hamiltonian<Agent> {

    constructor(params: Params) : super() {
        for(i in 0..params.GRIDSIZESQ) {
            Predator(i).hamiltonian(this, params)
            Prey(i).hamiltonian(this, params)
        }
    }


    fun MAP(startState: Set<Agent>, observedState: Map<Agent,Int>, time: Double, nFactors: Int, nActs: Int): List<Act<Agent>> {
        val solver = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
        val termsPerFactor = this.size
        val X = Array(nFactors) {
            solver.makeBoolVarArray(termsPerFactor)
        }

        // only terms that commute in a factor
        for (firstTerm in 0 until termsPerFactor) {
            for (otherTerm in firstTerm until termsPerFactor) {
                if (!this[firstTerm].commutesWith(this[otherTerm])) {
                    for (factor in 0 until nFactors) {
                        val doesntCommute = solver.makeConstraint(0.0, 1.0)
                        doesntCommute.setCoefficient(X[factor][firstTerm], 1.0)
                        doesntCommute.setCoefficient(X[factor][otherTerm], 1.0)
                    }
                }
            }
        }


        // observations must be satisfied
        for ((agent, nObserved) in observedState) {
            val nTotal = if(startState.contains(agent)) 0.0 else 1.0
            val satisfyObservation = solver.makeConstraint(nTotal, nTotal) // exactly one
            for (term in 0 until termsPerFactor) {
                val d = this[term].delta(agent)
                if (d != 0) {
                    for (factor in 0 until nFactors) {
                        satisfyObservation.setCoefficient(X[factor][term], d * 1.0)
                    }
                }
            }
        }

        // for every factor requirements must be satisfied for every state
        val states = getAllStates()
        for(state in states) {
            for(factor in 0 until nFactors) {
                val satisfyRequirement = solver.makeConstraint(
                    if(startState.contains(state)) -1.0 else 0.0,
                    Double.POSITIVE_INFINITY
                )
                for(term in 0 until termsPerFactor) {
                    if(this[term].requirements.contains(state))
                        satisfyRequirement.setCoefficient(X[factor][term], -1.0)
                    val d = this[term].delta(state)
                    if (d != 0) {
                        for(satFactor in factor+1 until nFactors) {
                            satisfyRequirement.setCoefficient(X[satFactor][term], d * 1.0)
                        }
                    }
                }
            }
        }

        // set total number of acts
        val totalNumberConstraint = solver.makeConstraint(nActs*1.0, nActs*1.0)
        for(term in 0 until termsPerFactor) {
            for(factor in 0 until nFactors) {
                totalNumberConstraint.setCoefficient(X[factor][term], 1.0)
            }
        }


        // set objective function
        val obj = solver.objective()
        for(term in 0 until termsPerFactor) {
            for(factor in 0 until nFactors) {
                obj.setCoefficient(X[factor][term], ln(this[term].rate))
            }
        }
        obj.setMaximization()

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


    fun getAllStates(): Set<Agent> {
        val agents = HashSet<Agent>()
        this.forEach { act ->
            agents.addAll(act.consequences)
            agents.addAll(act.requirements)
        }
        return agents
    }

}