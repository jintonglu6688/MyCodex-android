package com.mycodex.mobile.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AndroidUpdateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("android_updates", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun storesOnlyPackageIdentityRelativePathAndThrottleState() {
        val store = AndroidUpdateStore(context)
        val identity = UpdatePackageIdentity(2, "update.apk", 10, "a".repeat(64))
        store.lastAutomaticCheckAttemptAt = 123
        store.saveVerified(identity, "2/update.apk")
        assertEquals(123, store.lastAutomaticCheckAttemptAt)
        assertEquals(identity to "2/update.apk", store.loadVerified())
        store.clearVerified()
        assertNull(store.loadVerified())
    }
}
