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
        val pObserve = 0.5
        val nPredator = 40
        val nPrey = 60
        val WINDOW_LEN = 1
        val nSteps = 7
        val params = StandardParams

        val problem = setupPredPreyProblem(pObserve, nPredator, nPrey, nSteps, params)
//        val problem = File("problemOnline0.dump").readObject<PredPreyProblem>()

        val windows = problem.observedTrajectory
            .drop(1)
            .windowed(WINDOW_LEN, WINDOW_LEN, true) { window ->
                window.map { obs -> obs.observation }
            }

        for(i in 0 until windows.size) {
            val window = windows[i]
            println("Adding window $window")
            problem.solver.addObservations(window)
            problem.solver.minimalSolve()
            problem.solver.removeDeadAgents(5)
            File("problemOnline${i}.dump").writeObject(problem)
        }
    }


    @Test
    fun calculateDivergences() {
        val params = StandardParams
        val problem = File("problemOnline0.dump").readObject<PredPreyProblem>()
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

        val resultFile = File("resultsOnline.dat").writer()
        for(t in divergences.indices) {
            val field = "${t+1} ${divergences[t]} ${refDivergences[t]}\n"
            resultFile.write(field)
            print(field)
        }
        resultFile.close()
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


    fun solve(problem: PredPreyProblem) {
        val WINDOW_LEN = 1

        val windows = problem.observedTrajectory
            .drop(1)
            .windowed(WINDOW_LEN, WINDOW_LEN, true) { window ->
                window.map { obs -> obs.observation }
            }


        windows.forEachIndexed { i, window ->
            println("Adding window $window")
            problem.solver.addObservations(window)
            problem.solver.minimalSolve()
            problem.solver.removeDeadAgents(5)
            problem.saveToFile("partialProblem${i}.dump")
        }
    }

    fun solve(dumpFile: String) {
        val problem = File(dumpFile).readObject<PredPreyProblem>()
        solve(problem)
    }
}