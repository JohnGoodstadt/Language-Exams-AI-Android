package com.goodstadt.john.language.exams.packages.ReferencePronouns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Language-neutral entry point for the existing Reference tab.
 * Pass the active content language or product-flavour code, such as "de" or "en".
 */
@Composable
fun PronounsReferenceDestination(
    languageTag: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source = remember(languageTag) {
        PronounsReferenceSources.forLanguageTag(languageTag)
    }
    val repository = remember(context, source) {
        FirestoreFirstPronounsReferenceRepository(
            context = context,
            source = source,
        )
    }
    val factory = remember(repository) {
        PronounsReferenceViewModel.Factory(repository)
    }
    val viewModel: PronounsReferenceViewModel = viewModel(factory = factory)

    PronounsReferenceRoute(
        viewModel = viewModel,
        modifier = modifier,
    )
}
