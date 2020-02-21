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
    fun findAverageDivergence() {
        val nSamples = 16
        val nSteps = 6
        val pObserve = 0.5
        val nPred = 40
        val nPrey = 60
        System.loadLibrary("jniortools")

        for(i in 10..nSamples) {
            println("Starting sample $i")
            val params = StandardParams
            val problem = setupPredPreyProblem(pObserve, nPred, nPrey, nSteps, params)
            problem.observedTrajectory.drop(1).forEach { problem.solver.addObservation(it.observation) }
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
        val observations = myModel.generateObservations(startState, nSteps, pObserve)
        return PredPreyProblem(observations, MAPOrbitSolver(myModel, startState))
    }


    @Test
    fun loadDumpedSolutions() {
        val nSamples = 16
        val nSteps = 6
        val divergenceMeans = DoubleArray(nSteps) { 0.0 }
        val refMeans = DoubleArray(nSteps) { 0.0 }

        // generate observation path
        for(i in 1..nSamples) {
            println("Loading sample $i")
            val params = StandardParams
            val problem = File("problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()

            // compare states
            val unobservedPosterior = problem.observedTrajectory
                .drop(1)
                .map { it.observation }
                .zip(problem.solver.trajectory)
                .map {(observation, posterior) ->
                    posterior - observation
                }
            val divergences = problem.observedTrajectory
                .drop(1)
                .map { it.realState }
                .zip(unobservedPosterior)
                .map { (real, predicted) ->
                    predicted.divergence(real, params.GRIDSIZE)
                }

            val refDivergences = problem.observedTrajectory
                .drop(1)
                .map { obs ->
                    PredPreyModel.randomState(50,params).divergence(obs.realState, params.GRIDSIZE)
                }

            for(i in 0 until nSteps) {
                divergenceMeans[i] += divergences[i]/nSamples
                refMeans[i] += refDivergences[i]/nSamples
            }
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