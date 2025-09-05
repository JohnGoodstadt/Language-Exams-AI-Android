package com.goodstadt.john.language.exams.storage

import android.util.Log
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    // Add other event types here (e.g., Navigate, ShowDialog, etc.)
}
