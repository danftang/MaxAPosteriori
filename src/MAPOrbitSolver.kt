import com.google.ortools.linearsolver.MPSolver
import lib.Multiset
import lib.emptyMultiset
import java.io.*
import java.lang.IllegalStateException
import java.util.*

class MAPOrbitSolver<AGENT>: Serializable {
    val timesteps = ArrayDeque<Timestep<AGENT>>()
    val startState: UnknownModelState<AGENT>
    val hamiltonian: Hamiltonian<AGENT>
//    var solver: MPSolver

    val trajectory: List<Multiset<AGENT>>
        get() = timesteps.map { it.committedState }

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


    fun completeSolve() {
        println("Starting complete solve")
        val solver = MPSolver("HamiltonianSolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
        timesteps.forEach { it.addForwardEvents() }

        timesteps.forEach { it.setupProblem(solver, true) }
        println("solving for ${solver.numVariables()} variables and ${solver.numConstraints()} constraints")
        val solveState = solver.solve()
        println("solveState = $solveState")

        timesteps.forEach {
            it.applySolution()
        }

    }

    fun minimalSolve() {
        do {
            val solver = MPSolver("HamiltonianSolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
            calculateForwardBackwardPotentialEvents()

            timesteps.forEach { it.setupProblem(solver, false) }
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

            timesteps.forEach {
                it.applySolution()
            }

        } while(solveState == MPSolver.ResultStatus.INFEASIBLE)

    }


    fun removeDeadAgents(maxStepsUnseen: Int) {
        timesteps.descendingIterator().asSequence().drop(maxStepsUnseen-1).forEach { timestep ->
            timestep.previousState.sources.forEach { agent ->
                timestep.committedEvents.add(Event(setOf(agent), emptySet(), emptyMultiset(), 1.0, agent))
            }
            timestep.previousState.sources.clear()
        }
    }


    private fun calculateForwardBackwardPotentialEvents() {
        timesteps.forEach { it.addForwardEvents() }
        var forwardRequirements: Set<AGENT> = emptySet()
        timesteps.descendingIterator().forEach { timestep ->
            timestep.filterBackwardEvents(forwardRequirements)
            forwardRequirements = timestep.requirementsFootprint
        }
    }

}