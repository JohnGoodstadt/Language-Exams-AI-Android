package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object TokenUsageManager {

    // These could later be fetched from Firebase Remote Config
   // var tokensPerCall = 230
    var waitDurationMillis = TimeUnit.HOURS.toMillis(1)

    var freeTokens = 4600
    var bundle99Tokens = 40000
    var bundle199Tokens = 85000
    var firestoreTotalTokenField = "llmTotalTokens"
    var firestoreCurrentToken = "llmCurrentTokens"

    private fun getFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun getAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun getCurrentTokenBalance(): Int {
        val uid = getAuth().currentUser?.uid ?: return 0
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()

        val balance = snapshot.getLong(firestoreCurrentToken)
        return if (balance == null) {
            // No token field exists yet — first use
            userDoc.update(firestoreCurrentToken, freeTokens,firestoreTotalTokenField,freeTokens)
                .addOnFailureListener {
                    // If update fails (e.g., document doesn't exist), create the field
                    userDoc.set(mapOf(firestoreCurrentToken to freeTokens, firestoreTotalTokenField to freeTokens), SetOptions.merge())
                }.await()
            freeTokens
        } else {
            balance.toInt()
        }
    }

    suspend fun deductTokens(amount: Int): Boolean {
        val uid = getAuth().currentUser?.uid ?: return false
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()
        val current = snapshot.getLong(firestoreCurrentToken)?.toInt() ?: 0
        if (current < amount)  {
            userDoc.update(firestoreCurrentToken, current - amount).await() //can be minus amount
            return false
        }
        else{
            userDoc.update(firestoreCurrentToken, current - amount).await()
            return true
        }


    }

    suspend fun addTokens(amount: Int) {
        val uid = getAuth().currentUser?.uid ?: return
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()
        val current = snapshot.getLong(firestoreCurrentToken)?.toInt() ?: 0
        userDoc.update(firestoreCurrentToken, current + amount).await()
    }

    suspend fun setTokens(amount: Int) {
        val uid = getAuth().currentUser?.uid ?: return
        val userDoc = getFirestore().collection("users").document(uid)
        userDoc.update(firestoreCurrentToken, amount).await()
    }

    suspend fun checkTokenAvailability(tokensNeeded: Int): Boolean {
        val uid = getAuth().currentUser?.uid ?: return false
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()
        val currentTokens = snapshot.getLong(firestoreCurrentToken)?.toInt() ?: 0
        return currentTokens >= tokensNeeded
    }
    //Do we have above zero tokens before call?
    suspend fun checkTokenAvailability(): Boolean {
        val uid = getAuth().currentUser?.uid ?: return false
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()
        val currentTokens = snapshot.getLong(firestoreCurrentToken)?.toInt() ?: 0
        return currentTokens >= 0
    }

    suspend fun deductTokensIfAvailable(tokensNeeded: Int): Boolean {
        val isAvailable = checkTokenAvailability(tokensNeeded)
        return if (isAvailable) deductTokens(tokensNeeded) else false
    }

    suspend fun checkAndShowTokenOptions(
        context: Context,
        tokensNeeded: Int,
        showDialog: (Boolean, (TokenTopUpOption) -> Unit) -> Unit,
        onContinue: () -> Unit
    ) {
        val uid = getAuth().currentUser?.uid ?: return
        val userDoc = getFirestore().collection("users").document(uid)
        val snapshot = userDoc.get().await()
        val currentTokens = snapshot.getLong(firestoreCurrentToken)?.toInt() ?: 0
        val lastTopUp = snapshot.getLong("lastTopUp") ?: 0

        if (currentTokens >= tokensNeeded) {
            userDoc.update(firestoreCurrentToken, currentTokens - tokensNeeded).await()
            onContinue()
        } else {
            val now = System.currentTimeMillis()
            val canTopUp = now - lastTopUp >= waitDurationMillis

            showDialog(canTopUp) {
                CoroutineScope(Dispatchers.IO).launch {
                    val newTokens = when (it) {
                        TokenTopUpOption.FREE -> {
                            userDoc.update(
                                mapOf(
                                    firestoreCurrentToken to freeTokens,
                                    "lastTopUp" to now
                                )
                            ).await()
                            bundle99Tokens
                        }
                        TokenTopUpOption.BUY_099 -> {
                            // TODO: Implement IAP for $0.99
                            userDoc.update(firestoreCurrentToken, bundle99Tokens).await()
                            bundle99Tokens
                        }
                        TokenTopUpOption.BUY_199 -> {
                            // TODO: Implement IAP for $1.99
                            userDoc.update(firestoreCurrentToken, bundle199Tokens).await()
                            bundle199Tokens
                        }
                    }
                    Log.d("TokenUsageManager", "Top-up success, added $newTokens tokens")
                    onContinue()
                }
            }
        }
    }
}

enum class TokenTopUpOption {
    FREE,
    BUY_099,
    BUY_199
}

@Composable
fun TokenOptionsDialog(canWait: Boolean, onOptionSelected: (TokenTopUpOption) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("You're out of tokens!", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose one of the options below to continue:")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onOptionSelected(TokenTopUpOption.FREE)
                        onDismiss()
                    },
                    enabled = canWait,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wait 1 hour for free tokens")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onOptionSelected(TokenTopUpOption.BUY_099)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buy 40,000 tokens for $0.99")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onOptionSelected(TokenTopUpOption.BUY_199)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buy 85,000 tokens for $1.99")
                }
            }
        }
    }
}
