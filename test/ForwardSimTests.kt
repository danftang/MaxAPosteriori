import lib.HashMultiset
import lib.hashMultisetOf
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt

class ForwardSimTests {
    @Test
    fun showForwardSimTest() {
        val nSteps = 200
        val myModel = PredPreyModel(StandardParams)
        val startState = PredPreyModel.randomState(40, 60, StandardParams)
        val trajectory = myModel.sampleTimesteppingPath(startState, nSteps).toStateTrajectory()

        PredPreyModel.plotTrajectory(trajectory, StandardParams.GRIDSIZE)
    }
}