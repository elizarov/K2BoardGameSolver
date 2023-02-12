import kotlin.random.Random

const val HAND_SIZE = 6

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

fun chooseCards(k: Int, origin: PackedCards, action: (PackedCards) -> Unit) {
    fun rec(i0: Int, k: Int, sel0: PackedCards) {
        if (k == 0) {
            action(sel0)
            return
        }
        var i = i0
        while (origin[i] == 0) {
            i++
            if (i >= nCardTypes) return
        }
        val m = origin[i]
        var sel = sel0
        for (j in 0..minOf(k, m)) {
            rec(i + 1, k - j, sel)
            sel += packedCard(i)
        }
    }
    rec(0, k, 0L)
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
}