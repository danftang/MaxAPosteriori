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
        for(t in 1..nSteps) {
            val lastState = state
            lastState.forEach {agent ->
                val choices = MutableCategorical<Event<Agent>>()
                primaryRequirementIndex[agent]?.forEach { act ->
                    choices[act] = act.rateFor(lastState)
                }
                if(choices.size == 0) println("no choices for agent $agent from ${primaryRequirementIndex[agent]}")
                val nextAct = choices.sample()
                state = nextAct.actOn(state)
            }
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
        requirementIndex[agent]?.forEach { act ->
            val rate = act.rateFor(state)
            if(rate == 0.0)
                this.remove(act)
            else
                this[act] = rate
        }
    }
}