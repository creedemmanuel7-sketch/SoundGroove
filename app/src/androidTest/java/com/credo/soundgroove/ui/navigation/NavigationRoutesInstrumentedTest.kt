package com.credo.soundgroove.ui.navigation

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke instrumenté — encodage route dossier. */
@RunWith(AndroidJUnit4::class)
class NavigationRoutesInstrumentedTest {

    @Test
    fun folderDetail_roundTripsEncodedPath() {
        val path = "/storage/Music/My Band/Live"
        val encoded = Routes.folderDetail(path).substringAfter("folder/")
        assertEquals(path, Uri.decode(encoded))
    }
}
