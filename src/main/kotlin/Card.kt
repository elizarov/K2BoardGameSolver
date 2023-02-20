import kotlin.random.Random

const val HAND_SIZE = 6
const val USE_CARDS = 3

val allCardsMap: Map<Card, Int> = mapOf(
    Card.Move(1) to 5,
    Card.Move(2) to 3,
    Card.Move(3) to 2,
    Card.Move(1, 3) to 1,
    Card.Move(1, 2) to 1,
    Card.Move(2, 3) to 1,
    Card.Acc(0) to 1,
    Card.Acc(1) to 2,
    Card.Acc(2) to 1,
    Card.Acc(3) to 1,
)

sealed class Card {
    data class Move(val up: Int, val down: Int = up) : Card() {
        override fun toString(): String =
            if (up == down) "M$up" else "M$up$down"
    }
    data class Acc(val value: Int) : Card() {
        override fun toString(): String = "A$value"
    }
}

typealias PackedCards = Long

val nCardTypes: Int = allCardsMap.size
val allCardTypes: Array<Card> = allCardsMap.keys.toTypedArray()
val allCardCounts: IntArray = allCardsMap.values.toIntArray()
val allCards: PackedCards = allCardCounts.packCards()

val maxDownMoveForUp = IntArray(9) { -1 }.also { a ->
    chooseCards(USE_CARDS, allCards) { cards ->
        forAllCardMoves(cards) { up, down, _ ->
            a[up] = maxOf(a[up], down)
        }
    }
}


fun IntArray.packCards(): PackedCards {
    var res = 0L
    for (i in 0 until nCardTypes) res = res or (this[i].toLong() shl (3 * i))
    return res
}

operator fun PackedCards.get(index: Int): Int = ((this shr (3 * index)) and 7L).toInt()

fun packedCard(index: Int): PackedCards = 1L shl (3 * index)

fun PackedCards.countCards(): Int {
    var cnt = 0
    for (i in 0 until nCardTypes) cnt += this[i]
    return cnt
}

fun PackedCards.packedCardsToString() = buildString {
    for (i in 0 until nCardTypes) {
        repeat(this@packedCardsToString[i]) { append(allCardTypes[i]) }
    }
}

fun Random.sampleCardsShuffle(k: Int, origin: PackedCards): PackedCards {
    val count = origin.countCards()
    require(count > 0 && k <= count)
    if (k == count) return origin
    var sel = 0L
    var rem = origin
    val sum = IntArray(nCardTypes)
    repeat(k) {
        sum[0] = rem[0]
        for (j in 1 until nCardTypes) sum[j] = sum[j - 1] + rem[j]
        val choice = nextInt(sum[nCardTypes - 1]) + 1
        val i = sum.indexOfFirst { it >= choice }
        sel += packedCard(i)
        rem -= packedCard(i)
    }
    return sel
}

inline fun chooseCards(k0: Int, origin: PackedCards, action: (PackedCards) -> Unit) {
    val kSt = IntArray(nCardTypes + 1)
    val iSt = IntArray(nCardTypes + 1)
    val jSt = IntArray(nCardTypes + 1)
    val selSt = LongArray(nCardTypes + 1)
    var sp = 0
    kSt[0] = k0
    rec@while (sp >= 0) {
        val k = kSt[sp]
        if (k == 0) {
            action(selSt[sp])
            sp--
            continue@rec
        }
        var i = iSt[sp]
        while (origin[i] == 0) {
            i++
            if (i >= nCardTypes) {
                sp--
                continue@rec
            }
        }
        iSt[sp] = i
        val m = origin[i]
        val sel = selSt[sp]
        val j = jSt[sp]++
        if (j <= minOf(k, m)) {
            selSt[sp] = sel + packedCard(i)
            sp++
            kSt[sp] = k - j
            iSt[sp] = i + 1
            jSt[sp] = 0
            selSt[sp] = sel
        } else {
            sp--
        }
    }
//    fun rec(i0: Int, k: Int, sel0: PackedCards) {
//        if (k == 0) {
//            action(sel0)
//            return
//        }
//        var i = i0
//        while (origin[i] == 0) {
//            i++
//            if (i >= nCardTypes) return
//        }
//        val m = origin[i]
//        var sel = sel0
//        for (j in 0..minOf(k, m)) {
//            rec(i + 1, k - j, sel)
//            sel += packedCard(i)
//        }
//    }
//    rec(0, k, 0L)
}

fun forAllCardMoves(cards: PackedCards, action: (up: Int, down: Int, acc: Int) -> Unit) {
    val c = arrayOfNulls<Card.Move>(USE_CARDS)
    var n = 0
    var acc = 0
    for (i in 0 until nCardTypes) for (j in 0 until cards[i]) {
        when (val type = allCardTypes[i]) {
            is Card.Move -> c[n++] = type
            is Card.Acc -> acc += type.value
        }
    }
    val maxDown = IntArray(9) { -1 }
    for (mask in 0 until (1 shl n)) {
        var up = 0
        var down = 0
        for (i in 0 until n) {
            if ((1 shl i) and mask != 0) {
                up += c[i]!!.up
            } else {
                down += c[i]!!.down
            }
        }
        maxDown[up] = maxOf(maxDown[up], down)
    }
    if (maxDown[0] == -1) {
        // No move cards at all
        action(0, 0, acc)
        return
    }
    var up = 0
    while (up < maxDown.size) {
        var nextUp = up + 1
        while (nextUp < maxDown.size && maxDown[nextUp] == -1) nextUp++
        if (nextUp >= maxDown.size || maxDown[nextUp] != maxDown[up]) {
            action(up, maxDown[up], acc)
        }
        up = nextUp
    }
}

fun main() {
    println("Total of ${allCardCounts.sum()} cards")
    var count = 0
    chooseCards(HAND_SIZE, allCards) { count++ }
    println("$count ways to choose $HAND_SIZE")
    val rnd = Random(1)
    println("Sampling card shuffles")
    repeat(10) {
        println(rnd.sampleCardsShuffle(6, allCards).packedCardsToString())
    }
    println("--- maxDownMoveForUp ---")
    for (up in maxDownMoveForUp.indices) {
        println("up=$up, down=${maxDownMoveForUp[up]}")
    }
}