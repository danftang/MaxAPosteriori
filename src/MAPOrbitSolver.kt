import com.google.ortools.linearsolver.MPSolver
import lib.Multiset
import java.lang.IllegalStateException
import java.util.*

class MAPOrbitSolver<AGENT> {
    val timesteps = ArrayDeque<Timestep<AGENT>>()
    val startState: ModelState<AGENT>
    val hamiltonian: Hamiltonian<AGENT>
//    var solver: MPSolver

    constructor(hamiltonian: Hamiltonian<AGENT>, startState: Multiset<AGENT>) {
        this.hamiltonian = hamiltonian
        this.startState = StartState(startState)
    }


    fun addObservation(observation: Multiset<AGENT>) {
        val lastState = if(timesteps.isEmpty()) startState else timesteps.peekLast()
        timesteps.add(Timestep(hamiltonian, lastState, observation))
    }


    fun addObservations(observations: Iterable<Multiset<AGENT>>) {
        observations.forEach { addObservation(it) }
    }


    fun solve() {
        do {
            val solver = MPSolver("HamiltonianSolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
            calculatePotentialEvents()

            timesteps.forEach { it.setupProblem(solver) }
            solver.objective().setMaximization()
            println("solving for ${solver.numVariables()} variables and ${solver.numConstraints()} constraints")
            val solveState = solver.solve()
            println("solveState = $solveState")

            if (solveState == MPSolver.ResultStatus.INFEASIBLE) {
                println("COULDN'T SOLVE. ROLLING BACK...")
                val timestepIt = timesteps.descendingIterator()
                var doneRollback = false
                while(timestepIt.hasNext() && !doneRollback) {
                    doneRollback = timestepIt.next().rollback()
                }
                if(!doneRollback) throw(IllegalStateException("Unsolvable state"))
            }

        } while(solveState == MPSolver.ResultStatus.INFEASIBLE)

        timesteps.forEach {
            it.applySolution()
        }
    }


    private fun calculatePotentialEvents() {
        timesteps.forEach { it.addForwardEvents() }
        var forwardRequirements: Set<AGENT> = emptySet()
        timesteps.descendingIterator().forEach { timestep ->
            timestep.filterBackwardEvents(forwardRequirements)
            forwardRequirements = timestep.requirementsFootprint
        }

    }
}