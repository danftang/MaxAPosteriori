fun main() {
    System.loadLibrary("jniortools")

    val INITIAL_WINDOW_LEN = 2
    val WINDOW_LEN = 2
    val TOTAL_STEPS = 15
    val myModel = PredPreyModel(StandardParams)
    val startState = myModel.randomState(50)
    val observations = myModel.generateObservations(startState, TOTAL_STEPS, 0.5)
    val windows = observations.drop(1).windowed(WINDOW_LEN, WINDOW_LEN, true) {window ->
        window.map { obs -> obs.observation }
    }
    println("Real orbit")
    observations.forEach {println(it.realState)}
    println("Observations")
    observations.forEach {println(it.observation)}



    val mySolver = MAPOrbitSolver(myModel, startState)

    windows.forEach {window ->
        println("Adding window $window")
        mySolver.addObservations(window)
        mySolver.solve()

        println("MAP orbit is")
        mySolver.timesteps.forEach { println(it.commitedEvents) }
        println("history is")
        println(startState)
        mySolver.timesteps.forEach { println(it.committedState) }
        println("sources are")
        println(mySolver.startState.sources)
        mySolver.timesteps.forEach { println(it.sources) }

        removeDeadAgents(mySolver, myModel, 8)
    }
}

fun removeDeadAgents(solver: MAPOrbitSolver<Agent>, model: PredPreyModel, maxStepsUnseen: Int) {
    solver.timesteps.descendingIterator().asSequence().drop(maxStepsUnseen-1).forEach { timestep ->
        timestep.previousState.sources.forEach { agent ->
            model.deathEvents[agent]?.also {
                timestep.commitedEvents.add(it)
            }
        }
        timestep.previousState.sources.clear()
    }
}
