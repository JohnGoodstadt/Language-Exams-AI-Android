package com.goodstadt.john.language.exams.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.goodstadt.john.language.exams.models.VocabWord
//import junit.framework.TestCase.assertEquals
//import junit.framework.TestCase.assertFalse
//import junit.framework.TestCase.assertNotNull
//import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
//import org.junit.Assert.assertEquals
//import org.junit.Assert.assertFalse
//import org.junit.Assert.assertNotNull
//import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RecallingItemsTest {

    // 1. Mock all external dependencies
    @Mock
    private lateinit var mockApplication: Application
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    // A static mock for Android's Log class
    private lateinit var mockLog: MockedStatic<Log>

    // 2. The class we are testing
    private lateinit var recallingItemsManager: RecallingItems

    // 3. Setup for coroutines
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    private val testVocabWord = VocabWord(id = 1, 1, translation = "hola", romanisation = "h-e-l-l-o",partOfSpeech= "",word = "hello", group = "", sentences = emptyList())
    private val testVocabWord2 = VocabWord(id = 2, 1, translation = "hola 2", romanisation = "h-e-l-l-o 2",partOfSpeech= "",word = "hello 2", group = "", sentences = emptyList())
    private val testKey = "hello"
    private val testExamKey = "test_exam.json"

    // 4. This function runs BEFORE every single test
    @Before
    fun setUp() {
        // Mock Android's Log class to prevent it from crashing in a JVM test
        mockLog = Mockito.mockStatic(Log::class.java)

        // Set up the chain of mocks for SharedPreferences
        whenever(mockApplication.applicationContext).thenReturn(mockContext)
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)

        // Provide a default return value for our mocked user preferences
        val flow = MutableStateFlow(testExamKey)
        whenever(mockUserPreferencesRepository.selectedFileNameFlow).thenReturn(flow)

        // Create a new instance of the class for each test to ensure isolation
        recallingItemsManager = RecallingItems(
            application = mockApplication,
            userPreferencesRepository = mockUserPreferencesRepository,
            appScope = testScope // Use our test scope
        )
    }

    // This function runs AFTER every test for cleanup
    @After
    fun tearDown() {
        mockLog.close()
    }

    // --- Now, we write our actual tests ---
    // --- Inside RecallingItemsTest class ---

    @Test
    fun `focusOnWord - when adding a new item - list contains the item and save is called`() = runTest(testDispatcher) {
        // ARRANGE: The state is initially empty
        assertTrue(recallingItemsManager.items.value.isEmpty())

        // ACT: Call the function to add and focus on a new word
        recallingItemsManager.focusOnWord(testVocabWord)

        // ASSERT: Check the results
        val items = recallingItemsManager.items.value
        assertEquals(1, items.size)
        assertEquals(testKey, items.first().key)
        assertEquals(RecallState.Waiting, items.first().recallState)
        assertTrue(items.first().nextEventTime > 0, "nextEventTime should be set")

        // Verify that the save operation was triggered
        //verify(mockEditor).putString(eq(testExamKey), any())
        //verify(mockEditor).apply()
    }

    @Test
    fun `remove - when removing an existing item - list becomes empty and save is called`() = runTest(testDispatcher) {
        // ARRANGE: Add an item first
        recallingItemsManager.focusOnWord(testVocabWord)
        assertEquals(1, recallingItemsManager.items.value.size)

        // ACT: Remove the item
        recallingItemsManager.remove(testKey)

        // ASSERT: The list should now be empty
        assertTrue(recallingItemsManager.items.value.isEmpty())

        // Verify that save was called again to persist the empty list
        verify(mockEditor, times(2)).putString(any(), any()) // Once for add, once for remove
    }

    @Test
    fun `recalledOK - advances the stop number correctly`() = runTest(testDispatcher) {
        // ARRANGE: Add an item. It starts at stop number 1.
        recallingItemsManager.focusOnWord(testVocabWord)
        assertEquals(1, recallingItemsManager.items.value.first().currentStopNumber)

        // ACT: Mark the recall as OK
        recallingItemsManager.recalledOK(testKey)

        // ASSERT: The stop number should now be 2
        val updatedItem = recallingItemsManager.items.value.first()
        assertEquals(2, updatedItem.currentStopNumber)
        assertNotNull(updatedItem)
        assertTrue(updatedItem.prevEventTime > 0, "prevEventTime should be updated")

        // Verify that save was triggered
        verify(mockEditor, times(2)).putString(any(), any())
    }

    @Test
    fun `recalledOK - called multiple times - continues to advance stops`() = runTest(testDispatcher) {
        // ARRANGE: Add an item
        recallingItemsManager.focusOnWord(testVocabWord)
        assertEquals(1, recallingItemsManager.items.value.first().currentStopNumber)

        // ACT 1: First recall
        recallingItemsManager.recalledOK(testKey)
        // ASSERT 1
        assertEquals(2, recallingItemsManager.items.value.first().currentStopNumber)

        // ACT 2: Second recall
        recallingItemsManager.recalledOK(testKey)
        // ASSERT 2
        assertEquals(3, recallingItemsManager.items.value.first().currentStopNumber)

        // Verify save was triggered three times in total
        verify(mockEditor, times(3)).putString(any(), any())
    }

    @Test
    fun `amIRecalling - returns true for existing item and false for non-existing`() = runTest(testDispatcher) {
        // ARRANGE: Add an item
        recallingItemsManager.focusOnWord(testVocabWord)

        // ASSERT
        assertTrue(recallingItemsManager.amIRecalling(testKey))
        assertFalse(recallingItemsManager.amIRecalling("a_different_key"))
    }

    @Test
    fun `removeAll - clears all items from the list and saves`() = runTest(testDispatcher) {
        // ARRANGE: Add multiple items
        recallingItemsManager.focusOnWord(testVocabWord)
        recallingItemsManager.focusOnWord(testVocabWord2)
        assertEquals(2, recallingItemsManager.items.value.size)

        // ACT: Call removeAll
        recallingItemsManager.removeAll()

        // ASSERT: The list is empty
        assertTrue(recallingItemsManager.items.value.isEmpty())
        // Verify save was called for the final clear
        verify(mockEditor, times(3)).putString(any(), any())
    }
}