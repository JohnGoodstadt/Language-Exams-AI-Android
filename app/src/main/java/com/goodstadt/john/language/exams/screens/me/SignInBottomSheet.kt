package com.goodstadt.john.language.exams.screens.me


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
//import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
//import androidx.credentials.CredentialManager

import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.BuildConfig

import com.goodstadt.john.language.exams.viewmodels.SignInViewModel
//import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInBottomSheet(
    onDismiss: () -> Unit,
    vm: SignInViewModel = viewModel() // Uses the VM we defined earlier
) {
    val context = LocalContext.current
    val isLoggedIn by vm.isUserLoggedIn.collectAsState()
    val errorMsg by vm.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

//    val GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID //"396419988924-n5lold2mlkm7r45j00qog6mjh29d7gd8.apps.googleusercontent.com"

//    val credentialManager = CredentialManager.create(context)
//    val googleIdOption = GetGoogleIdOption.Builder()
//        .setFilterByAuthorizedAccounts(false)
//        .setServerClientId(GOOGLE_CLIENT_ID)
//        .build()
//
//    val request = GetCredentialRequest.Builder()
//        .addCredentialOption(googleIdOption)
//        .build()
//
//    scope.launch {
//        try {
//            val result = credentialManager.getCredential(context, request)
//            vm.handleSignIn(result)
//            onDismiss()
//        } catch (e: GetCredentialException) {
//            // Handle user cancellation or Error 10 here
//            Timber.e("Credential Manager Error: ${e.message}")
//        }
//    }


    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 24.dp), // Add bottom padding for nav bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header ---
            Icon(
                imageVector = if (isLoggedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isLoggedIn) "Account Management" else "Save Your Progress",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isLoggedIn)
                    "You are currently signed in. Your progress is synced."
                else
                    "Sign in with Google to keep your exam progress safe, and use across devices, even if you uninstall the app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Error Display ---
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Action Button ---
            Button(
                onClick = {
                    if (isLoggedIn) {
                        //googleSignInClient.signOut()
                        vm.onSignOutClicked()
                        onDismiss() // Close sheet after signing out
                    } else {
                       // launcher.launch(googleSignInClient.signInIntent)
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        scope.launch {
                            try {
                                val result = credentialManager.getCredential(context, request)
                                vm.handleSignIn(result)
                                onDismiss()
                            } catch (e: GetCredentialException) {
                                // Handle user cancellation or Error 10 here
                                Timber.e("Credential Manager Error: ${e.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.run {
                    buttonColors(
                                containerColor = if (isLoggedIn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                                contentColor = if (isLoggedIn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                            )
                }
            ) {
                if (isLoggedIn) {
                    Text("Sign Out")
                } else {
                    // You can use a painterResource for the Google 'G' icon here
                    Text("Sign in with Google")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (BuildConfig.DEBUG){

                TextButton(onClick = onDismiss) {
                    Text("uid: ${Firebase.auth.uid?.take(6)} (D)")
                }
            }

            // Cancel Button
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}


