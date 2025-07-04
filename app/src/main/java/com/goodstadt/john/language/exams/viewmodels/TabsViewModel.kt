// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/viewmodels/TabsViewModel.kt
package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor() : ViewModel() {

    private val _tab1MenuItems = MutableStateFlow(listOf("Personal", "Home", "Shopping"))
    val tab1MenuItems = _tab1MenuItems.asStateFlow()

    private val _tab2MenuItems = MutableStateFlow(listOf("Travel", "Leisure", "Weather"))
    val tab2MenuItems = _tab2MenuItems.asStateFlow()

    private val _tab3MenuItems = MutableStateFlow(listOf("Past Tense", "Patterns", "Verbs"))
    val tab3MenuItems = _tab3MenuItems.asStateFlow()

    private val _tab5MenuItems = MutableStateFlow(listOf("Settings", "Search", "Quiz"))
    val tab5MenuItems = _tab5MenuItems.asStateFlow()
}