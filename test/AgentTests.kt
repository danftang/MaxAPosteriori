import org.junit.Test

class AgentTests {
    @Test
    fun movementTest() {
        val params = StandardParams
        for(id in 0 until params.GRIDSIZESQ) {
            val agent = Predator(id)
            assert(agent.left(params.GRIDSIZE) == agent.left(params.GRIDSIZE).coerceIn(0 until params.GRIDSIZESQ))
            assert(agent.right(params.GRIDSIZE) == agent.right(params.GRIDSIZE).coerceIn(0 until params.GRIDSIZESQ))
            assert(agent.up(params.GRIDSIZE) == agent.up(params.GRIDSIZE).coerceIn(0 until params.GRIDSIZESQ))
            assert(agent.down(params.GRIDSIZE) == agent.down(params.GRIDSIZE).coerceIn(0 until params.GRIDSIZESQ))

        }
    }
}