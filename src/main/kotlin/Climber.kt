const val MAX_ACC = 6

data class Climber(
    val space: Space,
    val tent: Space?,
    val score: Int,
    val acc: Int,
) {
    override fun toString(): String = buildString {
        append("@${space.index}")
        if (tent != null) append("(${tent.index})")
        append(": score=$score, acc=$acc")
    }
}

interface ClimbersState {
    val hand: PackedCards
    val climbers: List<Climber>
}

data class ClimberMove(val space: Space, val tent: Space?, val score: Int, val accWithoutTent: Int) {
    lateinit var via: List<Space>
}

fun findClimberMoves(climber: Climber, cards: PackedCards, paths: BoardPaths): List<ClimberMove> {
    val moves = HashSet<ClimberMove>()
    fun addMove(space: Space, tent: Space?, score: Int, acc: Int, via: List<Space>) {
        val move = ClimberMove(space, tent, score, acc)
        if (moves.add(move)) move.via = via
    }
    forAllCardMoves(cards) { up, down, acc ->
        for ((b, list) in paths.paths[climber.space]!!) {
            for (cost in list) {
                val newAcc = (climber.acc + b.acc + acc).coerceAtMost(MAX_ACC)
                if (newAcc >= 0 && cost.up <= up && cost.down <= down) {
                    val newScore = maxOf(climber.score, cost.score)
                    addMove(b, climber.tent, newScore, newAcc, cost.via)
                    if (climber.tent == null && cost.up + b.ent <= up) {
                        addMove(b, b, newScore, newAcc, cost.via)
                    }
                }
            }
        }
    }
    return moves.toList()
}
