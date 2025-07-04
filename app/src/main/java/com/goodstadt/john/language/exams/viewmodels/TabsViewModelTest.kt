// <project-root>/app/src/test/java/com/goodstadt/john/language/exams/viewmodels/TabsViewModelTest.kt
package com.goodstadt.john.language.exams.viewmodels

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TabsViewModelTest {

    private lateinit var viewModel: TabsViewModel

    @Before
    fun setUp() {
        // For a simple ViewModel like this, we can instantiate it directly.
        // For ViewModels with dependencies, you'd use a testing library like Mockk.
        viewModel = TabsViewModel()
    }

    @Test
    fun `tab1MenuItems initial state is correct`() {
        val expectedItems = listOf("Personal", "Home", "Shopping")
        val actualItems = viewModel.tab1MenuItems.value
        assertThat(actualItems).isEqualTo(expectedItems)
    }

    @Test
    fun `tab5MenuItems initial state is correct`() {
        val expectedItems = listOf("Settings", "Search", "Quiz")
        val actualItems = viewModel.tab5MenuItems.value
        assertThat(actualItems).isEqualTo(expectedItems)
    }
}