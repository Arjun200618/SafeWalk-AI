package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("safewalk_prefs", Context.MODE_PRIVATE)

    private val _contactFlow = MutableStateFlow(getContact())
    val contactFlow: StateFlow<EmergencyContact> = _contactFlow.asStateFlow()

    private val _customPhraseFlow = MutableStateFlow(getCustomEmergencyPhrase())
    val customPhraseFlow: StateFlow<String> = _customPhraseFlow.asStateFlow()

    private val _onboardingCompleteFlow = MutableStateFlow(isOnboardingComplete())
    val onboardingCompleteFlow: StateFlow<Boolean> = _onboardingCompleteFlow.asStateFlow()

    fun getContact(): EmergencyContact {
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val rel = prefs.getString(KEY_RELATIONSHIP, "") ?: ""
        return EmergencyContact(name = name, phoneNumber = phone, relationship = rel)
    }

    fun saveContact(contact: EmergencyContact) {
        prefs.edit()
            .putString(KEY_NAME, contact.name.trim())
            .putString(KEY_PHONE, contact.phoneNumber.trim())
            .putString(KEY_RELATIONSHIP, contact.relationship.trim())
            .apply()
        _contactFlow.value = contact
    }

    fun deleteContact() {
        prefs.edit()
            .remove(KEY_NAME)
            .remove(KEY_PHONE)
            .remove(KEY_RELATIONSHIP)
            .apply()
        _contactFlow.value = EmergencyContact()
    }

    fun getCustomEmergencyPhrase(): String {
        return prefs.getString(KEY_CUSTOM_PHRASE, "") ?: ""
    }

    fun saveCustomEmergencyPhrase(phrase: String) {
        prefs.edit().putString(KEY_CUSTOM_PHRASE, phrase.trim()).apply()
        _customPhraseFlow.value = phrase.trim()
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, complete).apply()
        _onboardingCompleteFlow.value = complete
    }

    companion object {
        private const val KEY_NAME = "contact_name"
        private const val KEY_PHONE = "contact_phone"
        private const val KEY_RELATIONSHIP = "contact_relationship"
        private const val KEY_CUSTOM_PHRASE = "custom_emergency_phrase"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
