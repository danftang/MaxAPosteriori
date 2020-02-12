import org.junit.Test

class TestMAPOrbitSolver {
    @Test
    fun basicFunctionalityTest() {
        System.loadLibrary("jniortools")

        // generate observation path
        val myModel = PredPreyModel(StandardParams)
        val startState = myModel.randomState(50)
        val observations = myModel.generateObservations(startState, 6, 0.5)
        println("Real orbit")
        observations.forEach {println(it.realState)}
        println("Observations")
        observations.forEach {println(it.observation)}

        val mySolver = MAPOrbitSolver(myModel, startState)
        observations.drop(1).forEach { mySolver.addObservation(it.observation) }
        mySolver.completeSolve()

        println("MAP orbit is")
        mySolver.timesteps.forEach { println(it.commitedEvents) }

        println("history is")
        println(startState)
        mySolver.timesteps.forEach { println(it.committedState) }
    }
}