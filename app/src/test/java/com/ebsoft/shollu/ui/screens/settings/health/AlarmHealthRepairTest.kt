package com.ebsoft.shollu.ui.screens.settings.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.security.AccessControlException

/**
 * Repair-failure classification.
 *
 * Seam: [AlarmHealth.classifyRepairFailure] — the pure half of the "Perbaiki Jadwal Alarm"
 * path's catch block.
 *
 * Guarded invariant: the repair path must keep its two failure kinds distinguishable. A
 * revoked exact-alarm capability surfaces as [SecurityException] from AlarmManager and must
 * select the permission copy ("izin sistem menolak"); every other scheduling/IO failure
 * selects the generic copy with the raw message. Collapsing either into the other would
 * tell a permission-less user to retry forever, or blame permissions for a transient error.
 */
class AlarmHealthRepairTest {

    @Test
    fun testClassifyRepairFailureMapsSecurityExceptionToPermissionDenied() {
        assertEquals(
            "AlarmManager answering SecurityException (capability revoked mid-sweep) is a permission failure",
            RepairFailureKind.PERMISSION_DENIED,
            AlarmHealth.classifyRepairFailure(SecurityException("Not allowed to schedule exact alarms"))
        )
        assertEquals(
            "any SecurityException subtype must classify as the permission kind too",
            RepairFailureKind.PERMISSION_DENIED,
            AlarmHealth.classifyRepairFailure(AccessControlException("denied"))
        )
    }

    @Test
    fun testClassifyRepairFailureMapsEveryOtherThrowableToSchedulingError() {
        for (t in listOf<Throwable>(
            IllegalStateException("scheduling mutex confusion"),
            IOException("DataStore read failed"),
            NullPointerException("missing context"),
            OutOfMemoryError("heap exhausted")
        )) {
            assertEquals(
                "${t::class.simpleName} must stay a scheduling/IO failure, never the permission copy",
                RepairFailureKind.SCHEDULING_ERROR,
                AlarmHealth.classifyRepairFailure(t)
            )
        }
    }
}
