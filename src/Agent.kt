import lib.toMutableMultiset
import java.io.Serializable

abstract class Agent(val pos: Int): Serializable {
    abstract fun copyAt(id: Int): Agent


    fun diffuse(h: Hamiltonian<Agent>, size: Int, rate: Double) {
        h += action(rate/4.0, copyAt(right(size)))
        h += action(rate/4.0, copyAt(left(size)))
        h += action(rate/4.0, copyAt(up(size)))
        h += action(rate/4.0, copyAt(down(size)))
    }


    fun die(h: Hamiltonian<Agent>, rate: Double) {
        h += action(rate)
    }

    fun right(size: Int) = pos - pos.rem(size) + (pos+1).rem(size)
    fun left(size: Int) = pos - pos.rem(size) + (pos+size-1).rem(size)
    fun up(size: Int) = (pos + size).rem(size*size)
    fun down(size: Int) = (pos + size*size - size).rem(size*size)


    fun action(rate: Double, vararg addedAgents: Agent) : Act<Agent> {
        return Act(setOf(this), addedAgents.toMutableMultiset(), rate)
    }


    fun interaction(rate: Double, otherAgent: Agent, vararg addedAgents: Agent) : Act<Agent> {
        return Act(setOf(this, otherAgent), addedAgents.toMutableMultiset(), rate)
    }


    fun translate(transVector: Int, gridSize: Int): Agent {
        val newX = (pos + transVector + gridSize).rem(gridSize)
        val newY = (transVector.div(gridSize) + pos.div(gridSize) + gridSize).rem(gridSize)
        return copyAt(newY*gridSize + newX)
    }
}