package com.megathon.incident.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumNormalizerTest {
    private fun type(s: String?) = EnumNormalizer.incidentType(s)
    private fun sev(s: String?) = EnumNormalizer.severity(s)

    @Test fun exactWireValueWins() = assertEquals(IncidentType.SPILL_CHEMICAL, type("spill_chemical"))
    @Test fun wireValueIsCaseAndSpaceInsensitive() = assertEquals(IncidentType.WATER_LEAK_FLOOD, type(" Water_Leak_Flood "))
    @Test fun leakSynonym() = assertEquals(IncidentType.WATER_LEAK_FLOOD, type("Water Leak"))
    @Test fun burstPipe() = assertEquals(IncidentType.WATER_LEAK_FLOOD, type("burst pipe"))
    @Test fun smokeIsFire() = assertEquals(IncidentType.FIRE_SMOKE, type("smoke from the printer"))
    @Test fun sparksAreElectrical() = assertEquals(IncidentType.ELECTRICAL, type("sparks from socket"))
    @Test fun theftIsSecurity() = assertEquals(IncidentType.SECURITY, type("theft"))
    @Test fun robberyIsSecurity() = assertEquals(IncidentType.SECURITY, type("robbery"))
    @Test fun potholeIsPropertyDamage() = assertEquals(IncidentType.PROPERTY_DAMAGE, type("pothole"))
    @Test fun hindiRomanisedFire() = assertEquals(IncidentType.FIRE_SMOKE, type("aag"))
    @Test fun hindiDevanagariFire() = assertEquals(IncidentType.FIRE_SMOKE, type("आग"))
    @Test fun hindiTheft() = assertEquals(IncidentType.SECURITY, type("chori"))
    @Test fun hindiWater() = assertEquals(IncidentType.WATER_LEAK_FLOOD, type("paani ka leak"))
    @Test fun unknownTypeIsRejectedNotCoercedToOther() = assertNull(type("banana"))
    @Test fun nullAndBlankAreNull() { assertNull(type(null)); assertNull(type("  ")) }
    @Test fun gasLeakIsChemicalNotWater() = assertEquals(IncidentType.SPILL_CHEMICAL, type("gas leak"))

    @Test fun severityWire() = assertEquals(Severity.MODERATE, sev("moderate"))
    @Test fun noneIsAStatedLevel() = assertEquals(Severity.NONE, sev("none"))
    @Test fun minorFromSentence() = assertEquals(Severity.MINOR, sev("Minor, nobody was hurt"))
    @Test fun seriousIsSevere() = assertEquals(Severity.SEVERE, sev("pretty serious"))
    @Test fun lifeThreateningIsCritical() = assertEquals(Severity.CRITICAL, sev("life threatening"))
    @Test fun hindiSmall() = assertEquals(Severity.MINOR, sev("chhota sa"))
    @Test fun hindiSerious() = assertEquals(Severity.SEVERE, sev("bahut gambhir"))
    @Test fun dontKnowIsNotASeverity() = assertNull(sev("I don't know"))
    @Test fun noIdeaIsNotASeverity() = assertNull(sev("no idea"))
    @Test fun pataNahiIsNotASeverity() = assertNull(sev("pata nahi"))
}
