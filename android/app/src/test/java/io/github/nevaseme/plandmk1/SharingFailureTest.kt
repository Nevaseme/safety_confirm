package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.nearby.sharingFailure
import org.junit.Assert.*
import org.junit.Test

class SharingFailureTest {
    @Test fun identifiesPlayServicesWhenOnlyItsAdvertisingPermissionIsDenied() {
        val error = sharingFailure(8038, true, false, "8038")
        assertTrue(error.showPlayServicesSettings)
        assertTrue(error.message.contains("Google Play開発者サービス"))
    }
    @Test fun ownPermissionDenialKeepsTheAppRecoveryPath() {
        assertFalse(sharingFailure(8038, false, false, "8038").showPlayServicesSettings)
    }
    @Test fun missingDiagnosticEvidenceDoesNotBlamePlayServices() {
        assertFalse(sharingFailure(8038, true, null, "8038").showPlayServicesSettings)
        assertFalse(sharingFailure(8038, true, true, "8038").showPlayServicesSettings)
    }
    @Test fun unrelatedFailureRetainsItsDetailAndNormalRecovery() {
        val error = sharingFailure(8001, true, false, "radio unavailable")
        assertFalse(error.showPlayServicesSettings)
        assertTrue(error.message.contains("radio unavailable"))
    }
}
