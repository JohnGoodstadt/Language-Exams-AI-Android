package com.goodstadt.john.language.exams.data

import android.content.Context
import android.content.res.Resources
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
// ... imports
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import com.goodstadt.john.language.exams.models.LanguagesControlFile
import com.goodstadt.john.language.exams.viewmodels.TabsViewModel
import io.mockk.coEvery

class VocabRepositoryTest {

    // Declare mocks for all dependencies
    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var mockGoogleCloudTts: GoogleCloudTTS
    private lateinit var mockAudioPlayerService: AudioPlayerService
    private lateinit var mockControlRepository: ControlRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockVocabRepository: VocabRepository
    private lateinit var viewModel: TabsViewModel

    // The class we are testing
    private lateinit var repository: VocabRepository

    @Before
    fun setUp() {
        // Create mock instances
        mockContext = mockk()
        mockResources = mockk()
        mockGoogleCloudTts = mockk()
        mockAudioPlayerService = mockk()

        mockVocabRepository = mockk()
        mockUserPreferencesRepository = mockk()
        mockControlRepository = mockk() // <-- New

        // Define default behavior for the new mock
        every { mockUserPreferencesRepository.getSelectedVoiceName() } returns "test-voice-name"
        // We need a dummy LanguageCodeDetails object for the mock to return successfully
        val dummyDetails: LanguageCodeDetails = mockk(relaxed = true)
        coEvery { mockControlRepository.getActiveLanguageDetails() } returns Result.success(dummyDetails)


        // When the code asks the context for its resources, return our mock resources
        every { mockContext.resources } returns mockResources
        // When the code asks the context for its package name, return a dummy name
        every { mockContext.packageName } returns "com.dummy.package"

        // Initialize the repository with all the mock dependencies
        repository = VocabRepository(
                context = mockContext,
                googleCloudTts = mockGoogleCloudTts,
                audioPlayerService = mockAudioPlayerService
        )
    }

    @Test
    fun `getVocabData successfully loads and parses valid JSON`() = runTest {
        // 1. Arrange
        val fileNameToTest = "vocab_data_a2"
        val fakeResourceId = 12345 // A dummy resource ID
        // A minimal, valid JSON string that matches our VocabFile structure
        val fakeJsonString = """
            {
              "fileformat": 1, "location": 1, "sheetname": "Test Sheet",
              "updatedDate": 1, "uploaddate": 1.0, "id": "test_id",
              "native": "en", "name": "English", "romanized": false,
              "nativename": "English", "googlevoiceprefix": "prefix",
              "voicename": "voice", "tabtitles": ["Menu1"],
              "categories": [] 
            }
        """.trimIndent()
        val fakeInputStream = ByteArrayInputStream(fakeJsonString.toByteArray())

        // --- Define the behavior of our mocks ---
        // When getIdentifier is called with our test filename, return the fake ID
        every {
            mockResources.getIdentifier(fileNameToTest, "raw", "com.dummy.package")
        } returns fakeResourceId

        // When openRawResource is called with our fake ID, return the fake JSON stream
        every { mockResources.openRawResource(fakeResourceId) } returns fakeInputStream


        // 2. Act
        val result = repository.getVocabData(fileNameToTest)


        // 3. Assert
        assertThat(result.isSuccess).isTrue()
        val vocabFile = result.getOrNull()
        assertThat(vocabFile).isNotNull()
        assertThat(vocabFile?.sheetName).isEqualTo("Test Sheet")
    }

    @Test
    fun `getVocabData returns failure when resource not found`() = runTest {
        // 1. Arrange
        val nonExistentFile = "non_existent_file"
        // When getIdentifier is called for this file, return 0 (meaning "not found")
        every {
            mockResources.getIdentifier(nonExistentFile, "raw", "com.dummy.package")
        } returns 0


        // 2. Act
        val result = repository.getVocabData(nonExistentFile)


        // 3. Assert
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(Exception::class.java)
        assertThat(exception?.message).contains("Resource file not found: $nonExistentFile.json")
    }
    // Update the ViewModel instantiation in BOTH of your tests
    @Test
    fun `when repository returns success, vocabUiState is Success`() {
        // ... Arrange ...

        // Act: Initialize the ViewModel with ALL THREE mocks
        viewModel = TabsViewModel(mockVocabRepository, mockUserPreferencesRepository, mockControlRepository)

        // ... Assert ...
    }

    @Test
    fun `when repository returns failure, vocabUiState is Error`() {
        // ... Arrange ...

        // Act: Initialize the ViewModel with ALL THREE mocks
        viewModel = TabsViewModel(mockVocabRepository, mockUserPreferencesRepository, mockControlRepository)

        // ... Assert ...
    }
}