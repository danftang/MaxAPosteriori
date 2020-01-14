
class Prey : Agent {
    constructor(pos: Int) : super(pos)

    override fun copyAt(pos: Int) = Prey(pos)

    fun hamiltonian(h: Hamiltonian<Agent>, params: Params) {
        reproduce(h, params)
        diffuse(h, params.GRIDSIZE, params.preyDiffuse)
        die(h, params.preyDie)
    }

    fun reproduce(h: Hamiltonian<Agent>, params: Params) {
        h += action(params.preyReproduce/4.0, Prey(left(params.GRIDSIZE)), this)
        h += action(params.preyReproduce/4.0, Prey(right(params.GRIDSIZE)), this)
        h += action(params.preyReproduce/4.0, Prey(up(params.GRIDSIZE)), this)
        h += action(params.preyReproduce/4.0, Prey(down(params.GRIDSIZE)), this)
    }

    override fun toString() = "r($pos)"

    override fun hashCode() = pos*2

    override fun equals(other: Any?) = (other is Prey && pos == other.pos)
}