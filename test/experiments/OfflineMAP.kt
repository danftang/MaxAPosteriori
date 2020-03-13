package experiments

import MAPOrbitSolver
import Params
import PredPreyModel
import PredPreyProblem
import StandardParams
import lib.*
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class OfflineMAP {
    val directory = "experiments/pObs0.66"

    @Test
    fun doOfflineSolve() {
        val samples = 1..16
        val nSteps = 7
        val pObserve = 2.0/3.0
        val nPred = 40
        val nPrey = 60
        System.loadLibrary("jniortools")

        var totalTime = 0.0
        for(i in samples) {
            println("Starting sample $i")
            val params = StandardParams
            val problem = PredPreyProblem(nPred, nPrey, nSteps, params)
            val observations = problem.realTrajectory.generateObservations(pObserve)
            val time = measureTimeMillis {  problem.offlineSolve(observations) }/1000.0
            totalTime += time
            println("time to solve ${time}s")
            if(!problem.solver.solutionIsCorrect(false)) throw(IllegalStateException("Solution is not correct!"))
            File("problem${nSteps}.${i}.dump").writeObject(problem)
        }
        println("Average solve time = ${totalTime/samples.count()}")
    }


    @Test
    fun calcDivergences() {
        val samples = 1..16
        val nSteps = 7
        val divergenceMeans = DoubleArray(nSteps) { 0.0 }
        val refMeans = DoubleArray(nSteps) { 0.0 }
        val nSamples = samples.count()

        // generate observation path
        for(i in samples) {
            println("Loading sample $i")
            val params = StandardParams
            val problem = File("${directory}/problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()

            val (divergences, refDivergences) = problem.calcDivergences()

            for(i in 0 until nSteps) {
                divergenceMeans[i] += divergences[i]/nSamples
                refMeans[i] += refDivergences[i]/nSamples
            }
        }
//        val resultFile = File("divergences.dat").writer()
        for(t in 0 until nSteps) {
            val field = "$t ${divergenceMeans[t]} ${refMeans[t]}\n"
//            resultFile.write(field)
            print(field)
        }
//        resultFile.close()
        gnuplot {
            val plotdata = heredoc((0 until nSteps).asSequence().map {
                Triple(it+1.0, divergenceMeans[it], refMeans[it])
            }, -1)
            invoke("""
                set boxwidth 0.3
                set style fill solid
                plot [][0:7] $plotdata using 1:2 with boxes title "Average distance of MAP"
                replot $plotdata using ($1+0.3):3 with boxes title "Average distance of random point"
            """)
        }
    }

    @Test
    fun plotFinalStateComparison() {
        val nSteps = 7
        val problem = File("experiments/pObs0.66/problem${nSteps}.1.dump").readObject<PredPreyProblem>()
        val realFinalState = problem.realTrajectory.last().consequences
        val mapFinalState = problem.solver.trajectory.last().consequences
        realFinalState.comparisonPlot(mapFinalState, problem.params.GRIDSIZE)

        // compare states
        val unobservedPosterior = problem.solver.observations
            .zip(problem.solver.trajectory)
            .map {(observation, posterior) ->
                posterior.consequences - observation
            }

        val unobservedRealState = problem.solver.observations
            .zip(problem.realTrajectory)
            .map {(observation, realEvents) ->
                realEvents.consequences - observation
            }

        unobservedRealState.last().comparisonPlot(unobservedPosterior.last(), problem.params.GRIDSIZE)
    }

    @Test
    fun calcLogProbs() {
        val nSamples = 16
        val nSteps = 7
        val directory = "experiments/pObs0.66"

        // generate observation path
        for(i in 1..nSamples) {
            val problem = File("${directory}/problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()
            val realLogProb = problem.realTrajectory.logProb()
            val mapLogProb = problem.solver.trajectory.logProb()
            println("$realLogProb $mapLogProb ${mapLogProb-realLogProb}")
        }
    }

}