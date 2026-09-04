package cyclelens.core

interface CycleRules {
    val handSize: Int
    val deckSize: Int
    val cardsRequiredToCycle: Int
}

object StandardCycleRules : CycleRules {
    override val handSize: Int = 4
    override val deckSize: Int = 8
    override val cardsRequiredToCycle: Int = 4
}
