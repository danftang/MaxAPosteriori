import lib.comparisonPlot
import lib.emptyMultiset
import org.junit.Test

class PlottingTests {
    @Test
    fun comparisonPlotTest() {
        val state1 = PredPreyModel.randomState(50,StandardParams)
        val state2 = PredPreyModel.randomState(50,StandardParams)
        state1.comparisonPlot(state2, StandardParams.GRIDSIZE)
    }
}