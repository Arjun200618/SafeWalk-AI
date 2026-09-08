package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ContactPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("safewalk_prefs", Context.MODE_PRIVATE)

    private val _contactsFlow = MutableStateFlow(getContacts())
    val contactsFlow: StateFlow<List<EmergencyContact>> = _contactsFlow.asStateFlow()

    private val _contactFlow = MutableStateFlow(getContact())
    val contactFlow: StateFlow<EmergencyContact> = _contactFlow.asStateFlow()

    private val _customPhraseFlow = MutableStateFlow(getCustomEmergencyPhrase())
    val customPhraseFlow: StateFlow<String> = _customPhraseFlow.asStateFlow()

    private val _onboardingCompleteFlow = MutableStateFlow(isOnboardingComplete())
    val onboardingCompleteFlow: StateFlow<Boolean> = _onboardingCompleteFlow.asStateFlow()

    fun getContacts(): List<EmergencyContact> {
        val jsonStr = prefs.getString(KEY_CONTACTS_JSON, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<EmergencyContact>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val phone = obj.optString("phoneNumber", "")
                    val rel = obj.optString("relationship", "")
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    if (name.isNotBlank() || phone.isNotBlank()) {
                        list.add(
                            EmergencyContact(
                                id = id,
                                name = name,
                                phoneNumber = phone,
                                relationship = rel
                            )
                        )
                    }
                }
                return list
            } catch (_: Exception) {}
        }

        // Fallback to legacy single contact if available
        val legacyName = prefs.getString(KEY_NAME, "") ?: ""
        val legacyPhone = prefs.getString(KEY_PHONE, "") ?: ""
        val legacyRel = prefs.getString(KEY_RELATIONSHIP, "") ?: ""
        if (legacyName.isNotBlank() && legacyPhone.isNotBlank()) {
            val single = EmergencyContact(
                name = legacyName,
                phoneNumber = legacyPhone,
                relationship = legacyRel
            )
            return listOf(single)
        }
        return emptyList()
    }

    fun getContact(): EmergencyContact {
        return getContacts().firstOrNull() ?: EmergencyContact()
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val limited = contacts.take(MAX_CONTACTS)
        val arr = JSONArray()
        for (c in limited) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name.trim())
                put("phoneNumber", c.phoneNumber.trim())
                put("relationship", c.relationship.trim())
            }
            arr.put(obj)
        }
        val first = limited.firstOrNull()
        prefs.edit()
            .putString(KEY_CONTACTS_JSON, arr.toString())
            .putString(KEY_NAME, first?.name?.trim() ?: "")
            .putString(KEY_PHONE, first?.phoneNumber?.trim() ?: "")
            .putString(KEY_RELATIONSHIP, first?.relationship?.trim() ?: "")
            .apply()

        val updated = getContacts()
        _contactsFlow.value = updated
        _contactFlow.value = updated.firstOrNull() ?: EmergencyContact()
    }

    fun addContact(contact: EmergencyContact): Boolean {
        val current = getContacts().toMutableList()
        if (current.size >= MAX_CONTACTS) {
            return false
        }
        current.add(contact)
        saveContacts(current)
        return true
    }

    fun saveContact(contact: EmergencyContact) {
        val current = getContacts().toMutableList()
        val index = current.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            current[index] = contact
        } else if (current.size < MAX_CONTACTS) {
            current.add(contact)
        } else if (current.isNotEmpty()) {
            current[0] = contact
        } else {
            current.add(contact)
        }
        saveContacts(current)
    }

    fun deleteContact(id: String) {
        val current = getContacts().filterNot { it.id == id }
        saveContacts(current)
    }

    fun deleteContact() {
        prefs.edit()
            .remove(KEY_CONTACTS_JSON)
            .remove(KEY_NAME)
            .remove(KEY_PHONE)
            .remove(KEY_RELATIONSHIP)
            .apply()
        _contactsFlow.value = emptyList()
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
        const val MAX_CONTACTS = 5
        private const val KEY_CONTACTS_JSON = "emergency_contacts_json"
        private const val KEY_NAME = "contact_name"
        private const val KEY_PHONE = "contact_phone"
        private const val KEY_RELATIONSHIP = "contact_relationship"
        private const val KEY_CUSTOM_PHRASE = "custom_emergency_phrase"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        fun isValidPhoneNumber(phone: String): Boolean {
            val trimmed = phone.trim()
            if (trimmed.length < 5) return false
            val digitsCount = trimmed.count { it.isDigit() }
            if (digitsCount < 5 || digitsCount > 16) return false
            return trimmed.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it.isWhitespace() }
        }
    }
}
