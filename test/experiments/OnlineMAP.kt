package experiments

import MAPOrbitSolver
import PredPreyModel
import PredPreyProblem
import StandardParams
import com.google.ortools.linearsolver.MPSolver
import lib.divergence
import lib.readObject
import lib.writeObject
import org.junit.Test
import java.io.File
import java.lang.IllegalStateException
import kotlin.system.measureTimeMillis

class OnlineMAP {
    val directory = "experiments/pObs0.66"

    @Test
    fun doOnlineSolve() {
        System.loadLibrary("jniortools")
        val pObserve = 0.5
        val nPredator = 40
        val nPrey = 60
        val WINDOW_LEN = 1
        val DEATH_INTERVAL = 5
        val nSteps = 10
        val params = StandardParams

        val problem = PredPreyProblem(nPredator, nPrey, nSteps, params)
        val observations = problem.realTrajectory.generateObservations(pObserve)

        problem.onlineSolve(observations, WINDOW_LEN, DEATH_INTERVAL)
        if(!problem.solver.solutionIsCorrect(false)) throw(IllegalStateException("Complete solution is not correct"))
        File("problemOnline${nSteps}.dump").writeObject(problem)

        println("log prob of real trajectory = ${problem.realTrajectory.logProb()}")
        println("log prob of MAP trajectory = ${problem.solver.trajectory.logProb()}")
    }


    @Test
    fun solveFromOfflineProblem() {
        System.loadLibrary("jniortools")
        val samples = 1..16
        val nSteps = 7
        val WINDOW_LEN = 1
        val DEAD_INTERVAL = 10
        var totalTime = 0.0

        // generate observation path
        for(i in samples) {
            val offlineProblem = File("${directory}/problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()
//            val onlineProblem = PredPreyProblem(
//                offlineProblem.realTrajectory,
//                MAPOrbitSolver(offlineProblem.solver.hamiltonian, offlineProblem.solver.startState.committedConsequences),
//                offlineProblem.params
//            )
            val onlineProblem = PredPreyProblem(offlineProblem.realTrajectory, offlineProblem.params)
            val observations = offlineProblem.solver.observations

            val time = measureTimeMillis {
                onlineProblem.onlineSolve(observations, WINDOW_LEN, DEAD_INTERVAL)
            }

            totalTime += time
            println("Time to solution = $time")
            if (!onlineProblem.solver.solutionIsCorrect(false)) throw(IllegalStateException("Complete solution is not correct"))
            File("${directory}/problemOnline${nSteps}.${i}.dump").writeObject(onlineProblem)
        }
        println("Average solve time = ${totalTime/samples.count()}")
    }


    @Test
    fun calculateDivergences() {
        val samples = 1..16
        val nSteps = 7
        val offlineDivergenceMeans = DoubleArray(nSteps) { 0.0 }
        val onlineDivergenceMeans = DoubleArray(nSteps) { 0.0 }
        val refMeans = DoubleArray(nSteps) { 0.0 }
        val nSamples = samples.count()

        // generate observation path
        for(i in samples) {
            println("Loading sample $i")
            val offlineProblem = File("${directory}/problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()
            val onlineProblem = File("${directory}/problemOnline${nSteps}.${i}.dump").readObject<PredPreyProblem>()

            val (offlineDivergences, refDivergences) = offlineProblem.calcDivergences()
            val (onlineDivergences, _) = onlineProblem.calcDivergences()

            for(i in 0 until nSteps) {
                offlineDivergenceMeans[i] += offlineDivergences[i]/nSamples
                onlineDivergenceMeans[i] += onlineDivergences[i]/nSamples
                refMeans[i] += refDivergences[i]/nSamples
            }
        }
        val resultFile = File("divergences.dat").writer()
        for(t in 0 until nSteps) {
            val field = "${t+1} ${offlineDivergenceMeans[t]} ${onlineDivergenceMeans[t]} ${refMeans[t]}\n"
            resultFile.write(field)
            print(field)
        }
        resultFile.close()
    }


    @Test
    fun calcLogProbs() {
        val samples = 1..16
        val nSteps = 7
        val directory = "experiments/pObs0.66"
        var mapLogRatio = 0.0
        var onlineLogRatio = 0.0

        // generate observation path
        for(i in samples) {
            println("loading sample $i")
            val offlineProblem = File("${directory}/problem${nSteps}.${i}.dump").readObject<PredPreyProblem>()
            val onlineProblem = File("${directory}/problemOnline${nSteps}.${i}.dump").readObject<PredPreyProblem>()
            val realLogProb = offlineProblem.realTrajectory.logProb()
            val mapLogProb = offlineProblem.solver.trajectory.logProb()
            val onlineLogProb = onlineProblem.solver.trajectory.logProb()
            mapLogRatio += mapLogProb - realLogProb
            onlineLogRatio += onlineLogProb - realLogProb
        }
        println("${mapLogRatio/samples.count()} ${onlineLogRatio/samples.count()}")
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