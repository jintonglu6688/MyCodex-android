package com.mycodex.mobile.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgreementKeyBackendTest {
    @Test
    fun existingWrappedKeyWinsAfterAndroidUpgrade() {
        assertEquals(
            AgreementKeyBackend.Wrapped,
            selectExistingAgreementKeyBackend(
                apiLevel = 36,
                wrappedExists = true,
                nativeExists = true,
            ),
        )
    }

    @Test
    fun existingNativeKeyRequiresAndroid12() {
        assertNull(
            selectExistingAgreementKeyBackend(
                apiLevel = 30,
                wrappedExists = false,
                nativeExists = true,
            ),
        )
        assertEquals(
            AgreementKeyBackend.NativeKeystore,
            selectExistingAgreementKeyBackend(
                apiLevel = 31,
                wrappedExists = false,
                nativeExists = true,
            ),
        )
    }

    @Test
    fun newIdentityUsesBackendSupportedByItsApiLevel() {
        assertEquals(AgreementKeyBackend.Wrapped, selectNewAgreementKeyBackend(apiLevel = 26))
        assertEquals(AgreementKeyBackend.Wrapped, selectNewAgreementKeyBackend(apiLevel = 30))
        assertEquals(AgreementKeyBackend.NativeKeystore, selectNewAgreementKeyBackend(apiLevel = 31))
        assertEquals(AgreementKeyBackend.NativeKeystore, selectNewAgreementKeyBackend(apiLevel = 36))
    }
}
