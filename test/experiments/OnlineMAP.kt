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
import java.io.Serializable

class OnlineMAP {
    @Test
    fun findAverageDivergence() {
        System.loadLibrary("jniortools")
        val pObserve = 0.75
        val nPredator = 20
        val nPrey = 30
        val WINDOW_LEN = 1
        val nSteps = 10
        val params = StandardParams

        val problem = setupPredPreyProblem(nPredator, nPrey, nSteps, params)

        val windows = problem
            .realTrajectory
            .generateObservations(pObserve)
            .windowed(WINDOW_LEN, WINDOW_LEN, true)

        for(i in 0 until windows.size) {
            val window = windows[i]
            println("Adding window $i of ${windows.size-1}")
            problem.solver.addObservations(window)
            problem.solver.minimalSolve()
            problem.solver.removeDeadAgents(5)
            File("problemOnline${i}.dump").writeObject(problem.solver)
        }

        println("log prob of real trajectory = ${problem.realTrajectory.logProb()}")
        println("log prob of MAP trajectory = ${problem.solver.trajectory.logProb()}")
    }


    @Test
    fun calculateDivergences() {
        val params = StandardParams
        val problem = File("problemOnline0.dump").readObject<PredPreyProblem>()
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

        val resultFile = File("resultsOnline.dat").writer()
        for(t in divergences.indices) {
            val field = "${t+1} ${divergences[t]} ${refDivergences[t]}\n"
            resultFile.write(field)
            print(field)
        }
        resultFile.close()
    }


    fun setupPredPreyProblem(
        nPredator: Int,
        nPrey: Int,
        nSteps: Int,
        params: Params
    ): PredPreyProblem {
        val myModel = PredPreyModel(params)
        val startState = PredPreyModel.randomState(nPredator, nPrey, params)
        val realTrajectory = myModel.sampleTimesteppingPath(startState, nSteps)
        return PredPreyProblem(realTrajectory, MAPOrbitSolver(myModel, startState))
    }


//    fun solve(problem: PredPreyProblem) {
//        val WINDOW_LEN = 1
//
//        val windows = problem.observedTrajectory
//            .drop(1)
//            .windowed(WINDOW_LEN, WINDOW_LEN, true) { window ->
//                window.map { obs -> obs.observation }
//            }
//
//
//        windows.forEachIndexed { i, window ->
//            println("Adding window $window")
//            problem.solver.addObservations(window)
//            problem.solver.minimalSolve()
//            problem.solver.removeDeadAgents(5)
//            problem.saveToFile("partialProblem${i}.dump")
//        }
//    }
//
//    fun solve(dumpFile: String) {
//        val problem = File(dumpFile).readObject<PredPreyProblem>()
//        solve(problem)
//    }
}