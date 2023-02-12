import kotlin.random.Random

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

class State(
    val c1: Climber,
    val c2: Climber,
    val hand: PackedCards,
    val deck: PackedCards
) {
    fun compositeScore() = (c1.score + c2.score) * 100 + (c1.acc + c2.acc)

    override fun equals(other: Any?): Boolean = other is State &&
            (c1 == other.c1 && c2 == other.c2 || c1 == other.c2 && c2 == other.c1) &&
            hand == other.hand && deck == other.deck

    override fun hashCode(): Int = ((c1.hashCode() + c2.hashCode()) * 31 + hand.hashCode()) * 31 + deck.hashCode()
    override fun toString(): String = "$c1 $c2 hand=${hand.packedCardsToString()} deck=${deck.packedCardsToString()}"

}

const val DAYS = 4
const val USE_CARDS = 3
const val MAX_ACC = 6

val stateValues = Array(DAYS) { HashMap<State, Int>() }

data class MoveTrace(
    val cc1: PackedCards,
    val cc2: PackedCards,
    val state: State
)

fun solveStateValue(day: Int, state: State, trace: MutableList<MoveTrace>?): Int {
    if (day == DAYS) return state.compositeScore()
    if (trace == null) stateValues[day][state]?.let { return it }
    val nHand = state.hand.countCards()
    if (nHand < HAND_SIZE && state.deck != 0L || nHand == 0) {
        val deck = if (state.deck == 0L) allCards else state.deck
        val sel = Random(day).sampleCardsShuffle(HAND_SIZE - nHand, deck)
        return solveStateValue(day, State(state.c1, state.c2, state.hand + sel, deck - sel), trace)
    }
    var best = if (trace == null) 0 else stateValues[day][state]!!
    var done = false
    chooseCards(USE_CARDS, state.hand) loop1@{ useCards ->
        if (done) return@loop1
        for (nc1 in 0..USE_CARDS) chooseCards(nc1, useCards) loop2@{ cc1 ->
            if (done) return@loop2
            val cc2 = useCards - cc1
            val (ms1, a1d) = findClimberMoves(state.c1, cc1)
            val (ms2, a2d) = findClimberMoves(state.c2, cc2)
            for ((s1, t1n, sc1) in ms1) for ((s2, t2n, sc2) in ms2) {
                val t1 = state.c1.tent ?: t1n
                val t2 = state.c2.tent ?: t2n
                var a1 = state.c1.acc + a1d + s1.acc
                var a2 = state.c2.acc + a2d + s2.acc
                if (s1 == t1 || s1 == t2) a1++
                if (s2 == t1 || s2 == t2) a2++
                if (a1 >= 1 && a2 >= 1) {
                    val c1 = Climber(s1, t1, maxOf(state.c1.score, sc1), a1.coerceAtMost(MAX_ACC))
                    val c2 = Climber(s2, t2, maxOf(state.c2.score, sc2), a2.coerceAtMost(MAX_ACC))
                    val next = State(c1, c2, state.hand - useCards, state.deck)
                    val win = solveStateValue(day + 1, next, null)
                    if (trace == null) {
                        best = maxOf(best, win)
                    } else {
                        if (win == best) {
                            trace.add(MoveTrace(cc1, cc2, next))
                            solveStateValue(day + 1, next, trace)
                            done = true
                            return@loop2
                        }
                    }
                }
            }
        }
    }
    stateValues[day][state] = best
    return best
}

data class ClimberMove(val space: Space, val tent: Space?, val score: Int)

val climberMoves = Array(100) { HashMap<Space, List<ClimberMove>>() }

fun findClimberMoves(space0: Space, up0: Int, down0: Int): List<ClimberMove> {
    require(up0 in 0..9 && down0 in 0..9 && up0 <= down0)
    val rem0 = up0 * 10 + down0
    climberMoves[rem0][space0]?.let { return it }
    val moves = ArrayList<ClimberMove>()
    val done = HashSet<ClimberMove>()
    val queue = HashMap<ClimberMove, Int>()
    fun add(move: ClimberMove, rem: Int) {
        if (move in done) return
        val prev = queue[move]
        if (prev != null && prev >= rem) return
        if (prev == null) moves.add(move)
        queue[move] = rem
    }
    fun canMove(cost: Int, rem: Int): Boolean = rem / 10 >= cost / 10 && rem % 10 >= cost % 10
    add(ClimberMove(space0, null, space0.score), rem0)
    while (queue.isNotEmpty()) {
        val rem = queue.values.max()
        if (rem == 0) break
        val cur = queue.entries.find { it.value == rem }!!.key
        queue.remove(cur)
        done += cur
        val (space, tent, score) = cur
        for (next in space.moves) {
            check(next.row != space.row)
            val upCost = next.ent * 11
            val cost = if (next.row < space.row) upCost else next.ent
            if (!canMove(cost, rem)) continue
            add(ClimberMove(next, tent, maxOf(score, next.score)), rem - cost)
            if (tent == null && canMove(cost + upCost, rem)) {
                add(ClimberMove(next, next, maxOf(score, next.score)), rem - cost - upCost)
            }
        }
    }
    climberMoves[rem0][space0] = moves
    return moves
}

data class ClimberMovesForCards(val spaces: List<ClimberMove>, val acc: Int)

fun findClimberMoves(climber: Climber, cards: PackedCards): ClimberMovesForCards {
    var acc = 0
    var up = 0
    var down = 0
    for (i in 0 until nCardTypes) {
        val m = cards[i]
        if (m == 0) continue
        when (val card = allCardTypes[i]) {
            is Card.Move -> {
                up += m * card.up
                down += m * card.down
            }
            is Card.Acc -> acc += m * card.value
        }
    }
    var spaces = findClimberMoves(climber.space, up, down)
    if (climber.tent != null) spaces = spaces.filter { it.tent == null }
    return ClimberMovesForCards(spaces, acc)
}


fun main() {
    val board = easyBoard

//    val c0 = Climber(board.spaces.single { it.index == 14 }, null, 2, 6)
//    val (moves, acc) = findClimberMoves(
//        c0,
//        packedCard(allCardTypes.indexOf(Card.Move(1))) +
//            packedCard(allCardTypes.indexOf(Card.Move(2))) +
//            packedCard(allCardTypes.indexOf(Card.Move(1, 3)))
//    )
//    board.printState(State(c0, c0, 0L, 0L))
//    moves.forEach { println("${it.space} score=${it.score}") }

    println("--- Board ---")
    val c0 = Climber(board.start, null, 1, 1)
    val s0 = State(c0, c0, 0L, 0L)
    board.printState(s0)
    val score = solveStateValue(0, s0, null)
    println("Found solution with score $score")
    println("=============")
    val trace = ArrayList<MoveTrace>()
    solveStateValue(0, s0, trace)
    for (t in trace) {
        board.printState(t.state, t.cc1, t.cc2)
        println("-------------")
    }
}