package com.ebsoft.shollu.data.db

import com.ebsoft.shollu.data.db.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-2 regressions for default-preset seeding:
 *  - a seeded-once marker means a user who deleted EVERY preset never gets them back;
 *  - a failed table read aborts (never inserts on unknown table state);
 *  - the marker alone is not enough to skip the legacy empty-table contract of
 *    presetsToInsert (existing callers/tests).
 */
class SeedPlanRound2Test {

    private val presets = SholluDatabase.defaultPresets()

    @Test
    fun freshInstallWithoutMarkerSeedsAllPresets() {
        assertEquals(presets, SholluDatabase.seedPlan(seededMarker = false, existing = emptyList()))
    }

    @Test
    fun seededMarkerSuppressedReseedingEvenWhenTableEmpty() {
        // User deleted every preset after first launch: marker true + empty table -> nothing.
        assertTrue(SholluDatabase.seedPlan(seededMarker = true, existing = emptyList()).isEmpty())
        assertTrue(SholluDatabase.seedPlan(seededMarker = true, existing = presets).isEmpty())
    }

    @Test
    fun populatedTableNeverReseeded() {
        assertTrue(SholluDatabase.seedPlan(seededMarker = false, existing = presets).isEmpty())
        val partial = presets.take(2)
        assertTrue(SholluDatabase.seedPlan(seededMarker = false, existing = partial).isEmpty())
    }

    @Test
    fun failedTableReadAbortsInsteadOfInserting() {
        // Transient DB error maps to null: unknown state -> MUST NOT insert (would duplicate).
        assertTrue(SholluDatabase.seedPlan(seededMarker = false, existing = null).isEmpty())
    }

    @Test
    fun legacyPresetsToInsertKeepsEmptyTableContract() {
        assertEquals(presets.size, SholluDatabase.presetsToInsert(emptyList()).size)
        assertTrue(SholluDatabase.presetsToInsert(presets).isEmpty())
    }
}
