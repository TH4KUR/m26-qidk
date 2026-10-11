package com.megathon.incident.tam

import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.Tense
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Some cue words are also first names: "bina" (without), "diya" (did), "will", "may". A capitalised one is a person,
 * not a cue: "Bina saw the incident" must not read as a negated clause (found by B's synthetic gold, where 62 labels
 * were rejected by the validator because of it).
 */
class NameCueCollisionTest {
    private val tam = TamDetector(TamCueLists.loadDefault())

    private fun first(text: String) = tam.analyze(text).first().tam

    @Test fun aCapitalisedBinaIsAPersonNotAWithout() = assertEquals(Modality.ASSERTED, first("Bina saw the incident.").modality)

    @Test fun aLowercaseBinaStillMeansWithout() = assertEquals(Modality.NEGATED, first("usne bina bataye kaam kiya").modality)

    @Test fun aCapitalisedDiyaIsAPersonNotAPastCue() = assertEquals(Tense.UNKNOWN, first("Diya saw the smoke.").tense)

    @Test fun midSentenceWillAndMayAreNames() {
        assertEquals(Modality.ASSERTED, first("We found it and then Will saw the fire.").modality)
        assertEquals(Modality.ASSERTED, first("The pump failed and then May switched it off.").modality)
    }

    @Test fun aLowercaseWillIsStillAPlan() = assertEquals(Modality.PLANNED, first("we will call the plumber").modality)

    @Test fun anInitialWillStaysAPlan() = assertEquals(Modality.PLANNED, first("Will call the plumber tomorrow.").modality)
}
