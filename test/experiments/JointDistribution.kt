package experiments

import PredPreyModel
import StandardParams
import lib.HashMultiset
import lib.Multiset
import lib.statistics
import org.junit.Test
import java.io.FileWriter
import kotlin.math.roundToInt

class JointDistribution {
    @Test
    fun calcPriorProbs() {
        val nPredator = 40
        val nPrey = 60
        val params = StandardParams
        val nSamples = 10000
        val nSteps = 6

        val myModel = PredPreyModel(params)
        val startState = PredPreyModel.randomState(nPredator, nPrey, params)
        val probs = ArrayList<Double>(nSamples)
        for(i in 1..nSamples) {
            val trajectory = myModel.sampleTimesteppingPath(startState, nSteps)
            probs.add(trajectory.logProb())
        }
        println(probs.statistics())
        val range = (probs.min()?:0.0)..(probs.max()?:0.0)
        val delta = range.endInclusive - range.start
        val bins = HashMultiset<Double>()
        probs.mapTo(bins) {
            range.start + ((it - range.start)*100.0/delta).roundToInt()*delta/100.0
        }
        val file = FileWriter("data.dat")
        bins.counts.toSortedMap().forEach {
            println("${it.key} ${it.value}")
            file.write("${it.key} ${it.value}\n")
        }
        file.close()
    }
}