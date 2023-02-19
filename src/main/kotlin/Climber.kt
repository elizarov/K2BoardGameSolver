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

data class ClimberMove(val space: Space, val tent: Space?, val score: Int, val accWithoutTent: Int)

fun findClimberMoves(climber: Climber, cards: PackedCards, paths: BoardPaths): List<ClimberMove> {
    val moves = HashSet<ClimberMove>()
    forAllCardMoves(cards) { up, down, acc ->
        for ((b, list) in paths.paths[climber.space]!!) {
            for (cost in list) {
                val newAcc = climber.acc + b.acc + acc
                if (newAcc >= 0 && cost.up <= up && cost.down <= down) {
                    val newScore = maxOf(climber.score, cost.score)
                    moves.add(ClimberMove(b, climber.tent, newScore, newAcc))
                    if (climber.tent == null && cost.up + b.ent <= up) {
                        moves.add(ClimberMove(b, b, newScore, newAcc))
                    }
                }
            }
        }
    }
    return moves.toList()
}
