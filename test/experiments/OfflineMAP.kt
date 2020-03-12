package experiments

import MAPOrbitSolver
import Params
import PredPreyModel
import PredPreyProblem
import StandardParams
import lib.comparisonPlot
import lib.divergence
import lib.readObject
import lib.writeObject
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class OfflineMAP {
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
            val problem = setupPredPreyProblem(pObserve, nPred, nPrey, nSteps, params)
            val time = measureTimeMillis {  problem.solver.completeSolve() }/1000.0
            totalTime += time
            println("time to solve ${time}s")
            if(!problem.solver.solutionIsCorrect(false)) throw(IllegalStateException("Solution is not correct!"))
            File("problem${nSteps}.${i}.dump").writeObject(problem)
        }
        println("Average solve time = ${totalTime/samples.count()}")
    }


    fun setupPredPreyProblem(
        pObserve: Double,
        nPredator: Int,
        nPrey: Int,
        nSteps: Int,
        params: Params
    ): PredPreyProblem {
        val myModel = PredPreyModel(params)
        val startState = PredPreyModel.randomState(nPredator, nPrey, params)
        val trajectory = myModel.sampleTimesteppingPath(startState, nSteps)
        val solver = MAPOrbitSolver(myModel, startState)
        solver.addObservations(trajectory.generateObservations(pObserve))
        return PredPreyProblem(trajectory, solver, params)
    }


    @Test
    fun loadDumpedSolutions() {
        val nSamples = 4
        val nSteps = 4
        val divergenceMeans = DoubleArray(nSteps) { 0.0 }
        val refMeans = DoubleArray(nSteps) { 0.0 }

        // generate observation path
        for(i in 1..nSamples) {
            println("Loading sample $i")
            val params = StandardParams
            val problem = File("problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()

            // compare states
            val unobservedPosterior = problem.solver.observations
                .zip(problem.solver.trajectory)
                .map {(observation, posterior) ->
                    posterior.consequences - observation
                }
            val divergences = problem.realTrajectory.toStateTrajectory()
                .zip(unobservedPosterior)
                .map { (real, predicted) ->
                    predicted.divergence(real, params.GRIDSIZE)
                }

            val refDivergences = problem.realTrajectory.toStateTrajectory()
                .map { realState ->
                    PredPreyModel.randomState(50,params).divergence(realState, params.GRIDSIZE)
                }

            for(i in 0 until nSteps) {
                divergenceMeans[i] += divergences[i]/nSamples
                refMeans[i] += refDivergences[i]/nSamples
            }

            println("log prob of real trajetory = ${problem.realTrajectory.logProb()}")
            println("log prob of MAP trajetory = ${problem.solver.trajectory.logProb()}")
        }
        println()
        val resultFile = File("results.dat").writer()
        for(t in 0 until nSteps) {
            val field = "$t ${divergenceMeans[t]} ${refMeans[t]}\n"
            resultFile.write(field)
            print(field)
        }
        resultFile.close()
    }

    @Test
    fun plotFinalStateComparison() {
        val nSteps = 4
        val problem = File("problem${nSteps}.1.dump").readObject<PredPreyProblem>()
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

}