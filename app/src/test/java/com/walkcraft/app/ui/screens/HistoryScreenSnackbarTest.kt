package com.walkcraft.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryScreenSnackbarTest {

    @Test
    fun consumeSavedSessionMessage_removesKey() {
        val handle = SavedStateHandle(mapOf(SNACKBAR_SAVED_SESSION_ID_KEY to "session123"))

        val message = consumeSavedSessionMessage(handle)

        assertEquals("Workout saved", message)
        assertNull(handle.get<String>(SNACKBAR_SAVED_SESSION_ID_KEY))
    }

    @Test
    fun consumeSavedSessionMessage_returnsNullWhenMissing() {
        val handle = SavedStateHandle()

        val message = consumeSavedSessionMessage(handle)

        assertNull(message)
    }
}
