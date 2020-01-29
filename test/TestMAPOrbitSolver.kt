import lib.HashMultiset
import lib.Multiset
import org.junit.Test

class TestMAPOrbitSolver {
    @Test
    fun basicFunctionalityTest() {
        System.loadLibrary("jniortools")

        // generate observation path
        val myModel = Model(TenByTenParams)
        val startState = myModel.randomState(100)
        val observations = myModel.generateObservations(startState, 4, 0.5)
        println("Real orbit")
        observations.forEach {println(it.realState)}
        println("Observations")
        observations.forEach {println(it.observation)}

        val mySolver = MAPOrbitSolver(myModel, startState)
        observations.drop(1).forEach { mySolver.addObservation(it.observation) }
        mySolver.solve()

        println("MAP orbit is")
        mySolver.timesteps.forEach { println(it.commitedEvents) }

        println("history is")
        println(startState)
        mySolver.timesteps.forEach { println(it.committedState) }
    }
}