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

class OfflineMAP {
    @Test
    fun doOfflineSolve() {
        val nSamples = 4
        val nSteps = 4
        val pObserve = 0.5
        val nPred = 40
        val nPrey = 60
        System.loadLibrary("jniortools")

        for(i in 1..nSamples) {
            println("Starting sample $i")
            val params = StandardParams
            val problem = setupPredPreyProblem(pObserve, nPred, nPrey, nSteps, params)
            problem.solver.completeSolve()
            File("problem${nSteps}.${i}.dump").writeObject(problem)
        }
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
        return PredPreyProblem(trajectory, solver)
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

}