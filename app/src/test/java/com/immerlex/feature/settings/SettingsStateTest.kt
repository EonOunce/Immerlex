package com.immerlex.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {

    @Test
    fun aFullDraftIsNotConfiguredUntilItIsSaved() {
        val draft = SettingsState(baseUrl = "https://api.example.com", model = "m", apiKey = "k")
        assertFalse(draft.aiConfigured)
        assertTrue(draft.copy(aiConfigSaved = true).aiConfigured)
    }

    @Test
    fun aFreshMachineIsNotConfigured() {
        assertFalse(SettingsState().aiConfigured)
        assertFalse(SettingsState().backupConfigured)
    }

    @Test
    fun backupNeedsBothTheAccountAndASavedPassword() {
        assertFalse(SettingsState(backupAccount = "a@b.c").backupConfigured)
        assertFalse(SettingsState(backupPasswordSaved = true).backupConfigured)
        assertTrue(SettingsState(backupAccount = "a@b.c", backupPasswordSaved = true).backupConfigured)
    }
}