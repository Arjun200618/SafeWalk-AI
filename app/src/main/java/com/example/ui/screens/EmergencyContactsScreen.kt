package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactPreferences
import com.example.data.EmergencyContact
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    contacts: List<EmergencyContact>,
    customPhrase: String,
    onSaveContact: (EmergencyContact) -> Unit,
    onDeleteContact: (String) -> Unit,
    onSaveCustomPhrase: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Backward compatibility overload
    currentContact: EmergencyContact = contacts.firstOrNull() ?: EmergencyContact(),
    onDeleteAllContacts: (() -> Unit)? = null
) {
    val configuredContacts = contacts.filter { it.isConfigured }

    // State for Adding / Editing a Contact
    var isFormVisible by remember { mutableStateOf(configuredContacts.isEmpty()) }
    var editingContactId by remember { mutableStateOf<String?>(null) }
    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }
    var inputRelationship by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }

    var phraseInput by remember(customPhrase) { mutableStateOf(customPhrase) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun resetForm() {
        editingContactId = null
        inputName = ""
        inputPhone = ""
        inputRelationship = ""
        phoneError = null
        isFormVisible = false
    }

    fun startEdit(contact: EmergencyContact) {
        editingContactId = contact.id
        inputName = contact.name
        inputPhone = contact.phoneNumber
        inputRelationship = contact.relationship
        phoneError = null
        isFormVisible = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Emergency Contacts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header with saved count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EMERGENCY CONTACTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Saved count pill
                Box(
                    modifier = Modifier
                        .background(
                            color = if (configuredContacts.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.15f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (configuredContacts.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.4f)
                            else Color(0xFFF59E0B).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${configuredContacts.size} of 5 Saved",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (configuredContacts.isNotEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                }
            }

            Text(
                text = "Save up to 5 emergency contacts. When danger is detected and countdown finishes, SafeWalk AI automatically sends your GPS location and emergency alert to all saved contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                lineHeight = 18.sp
            )

            // Saved Contacts List
            if (configuredContacts.isNotEmpty()) {
                configuredContacts.forEachIndexed { index, contact ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (contact.relationship.isNotBlank()) {
                                        Text(
                                            text = " • ${contact.relationship}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Edit Action
                            IconButton(onClick = { startEdit(contact) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Contact",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Delete Action
                            IconButton(
                                onClick = {
                                    onDeleteContact(contact.id)
                                    onDeleteAllContacts?.invoke()
                                    if (editingContactId == contact.id) {
                                        resetForm()
                                    }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Contact removed.")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Contact",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Add or Edit Form Card
            if (isFormVisible) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingContactId != null) "Edit Contact" else "Add Emergency Contact (${configuredContacts.size + 1} of 5)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (configuredContacts.isNotEmpty()) {
                                IconButton(onClick = { resetForm() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Form",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Contact Name *") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_name_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = {
                                inputPhone = it
                                phoneError = null
                            },
                            label = { Text("Phone Number *") },
                            isError = phoneError != null,
                            supportingText = {
                                phoneError?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_phone_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = inputRelationship,
                            onValueChange = { inputRelationship = it },
                            label = { Text("Relationship (e.g. Partner, Parent, Friend)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (configuredContacts.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { resetForm() },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }

                            Button(
                                onClick = {
                                    val trimmedName = inputName.trim()
                                    val trimmedPhone = inputPhone.trim()

                                    if (trimmedName.isBlank() || trimmedPhone.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Please enter both a name and phone number.")
                                        }
                                        return@Button
                                    }

                                    if (!ContactPreferences.isValidPhoneNumber(trimmedPhone)) {
                                        phoneError = "Please enter a valid phone number (digits and standard formatting)."
                                        return@Button
                                    }

                                    val contactToSave = EmergencyContact(
                                        id = editingContactId ?: UUID.randomUUID().toString(),
                                        name = trimmedName,
                                        phoneNumber = trimmedPhone,
                                        relationship = inputRelationship.trim()
                                    )
                                    onSaveContact(contactToSave)
                                    resetForm()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Contact saved successfully.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_contact_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Contact", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (configuredContacts.size < ContactPreferences.MAX_CONTACTS) {
                // "Add Emergency Contact" Button
                Button(
                    onClick = {
                        resetForm()
                        isFormVisible = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_contact_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Emergency Contact (${configuredContacts.size}/5)",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Notice when 5 contacts maxed out
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Maximum of 5 emergency contacts configured. All 5 contacts will be alerted in an emergency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // CUSTOM EMERGENCY PHRASE CONFIGURATION
            Text(
                text = "CUSTOM EMERGENCY VOICE PHRASE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "In addition to \"Help me\", \"Emergency\", and \"Save me\", you can set one custom safe phrase to trigger the alarm hands-free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        label = { Text("Custom Emergency Phrase (e.g. Code Red)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onSaveCustomPhrase(phraseInput)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Custom phrase saved: \"$phraseInput\"")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D9488)
                        )
                    ) {
                        Text("Update Custom Phrase", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // PREVIEW OF EMERGENCY MESSAGE
            Text(
                text = "ALERT MESSAGE PREVIEW",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "EMERGENCY ALERT! SafeWalk AI detected a possible danger. Please check on me immediately.\n\nMy current location:\nLatitude: 37.77490\nLongitude: -122.41940\n\nGoogle Maps:\nhttps://www.google.com/maps?q=37.77490,-122.41940",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
