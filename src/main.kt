import com.google.ortools.linearsolver.MPSolver
import lib.HashMultiset
import lib.Multiset

fun main() {
    System.loadLibrary("jniortools")

    // generate observation path
    val myModel = Model(StandardParams)
    val startState = myModel.randomState(60)
    val observations = myModel.generateObservations(startState, 4, 0.5)
    println("Real orbit")
    observations.forEach {println(it.realState)}
    println("Observations")
    observations.forEach {println(it.observation)}

    val mapOrbit = myModel.MAP(startState, observations.map {it.observation})
    println("Map orbit")
    mapOrbit.forEach { println(it) }

    // re-construct state
    val history = ArrayList<Multiset<Agent>>()
    history.add(startState)
    var state = startState
    mapOrbit.forEach { acts ->
        val lastState = state
        acts.forEach { act ->
            if(act.rateFor(lastState) == 0.0) throw(IllegalStateException("Impossible orbit!"))
            state = act.actOn(state)
        }
        history.add(state)
    }
    println("history is")
    history.forEach { println(it) }

    // ensure MAP history fits observation
    for(frame in observations.indices) {
        if(!observations[frame].observation.isSubsetOf(history[frame]))
            throw(IllegalStateException("MAP history does not match observations"))
    }

}

fun testSolve() {
    val solver = MPSolver("MySolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING)
    val x = solver.makeIntVar(0.0, 100.0, "x")
    val y = solver.makeIntVar(0.0, 1000.0, "y")

    val c0 = solver.makeConstraint(-1000.0, 17.5, "c0")
    c0.setCoefficient(x, 1.0)
    c0.setCoefficient(y, 7.0)

    val c1 = solver.makeConstraint(-1000.0, 3.5, "c1")
    c1.setCoefficient(x, 1.0)
    c1.setCoefficient(y, 0.0)

    val obj = solver.objective()
    obj.setCoefficient(x, 1.0)
    obj.setCoefficient(y, 10.0)
    obj.setMaximization()

    val solveState = solver.solve()

    println("solveState = $solveState")

    println("x = ${x.solutionValue()}")
    println("y = ${y.solutionValue()}")
    println("objective = ${obj.value()}")
}
