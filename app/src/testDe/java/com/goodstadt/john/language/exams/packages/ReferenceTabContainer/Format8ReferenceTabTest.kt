package com.goodstadt.john.language.exams.packages.ReferenceTabContainer

import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Format8ReferenceTabTest {
    @get:Rule val mainRule = MainCoroutineRule()
    private val json = Json { ignoreUnknownKeys = true }
    private val parentId = "GermanReferencePronounsOpenAI"
    private val repository: ContentRepository = mockk()
    private val viewModels = mutableListOf<ReferenceTabContainerViewModel>()

    private fun source(relative: String): String =
        listOf(File("src/de/$relative"), File("app/src/de/$relative"))
            .first { it.isFile }.readText(Charsets.UTF_8)
    private fun manifest() = json.decodeFromString<AppUIManifest>(source("assets/debug_manifest.json"))
    private fun file(docId: String): Format8File = json.decodeFromString(
        source("res/raw/${LanguageConfig.mapLogicalToResourceName(docId)}.json")
    )
    private fun viewModel(config: AppUIManifest = manifest()): ReferenceTabContainerViewModel {
        val appConfig: AppConfigRepository = mockk()
        every { appConfig.getAppUiManifest() } returns config
        val preferences: UserPreferencesRepository = mockk()
        every { preferences.selectedVoiceNameFlow } returns emptyFlow()
        return ReferenceTabContainerViewModel(appConfig, preferences, repository, mockk(), mockk())
            .also { viewModels += it }
    }
    @After fun stopViewModels() { viewModels.forEach { it.viewModelScope.cancel() } }

    @Test fun manifestRecognizesBothFormat8SpellingsAndScreenRoutes() {
        val definition = manifest().sheetRegistry.getValue(parentId)
        assertEquals(SheetDataType.FORMAT_8, definition.sheetDataType)
        assertEquals(ScreenType.GROUPED_FORMAT_8_SCREEN, definition.screenType)
        assertEquals(6, definition.subTabs!!.size)
        assertTrue(definition.subTabs!!.all { it.sheetDataType == SheetDataType.FORMAT_8 })
        assertEquals(ScreenType.FORMAT_8_SCREEN, json.decodeFromString<ScreenType>("\"Format8Screen\""))
        assertEquals(SheetDataType.UNKNOWN, json.decodeFromString<SheetDataType>("\"NotAFormat\""))
    }

    @Test fun parentAndAllSixSubTabsLoadAndReturningUsesTheirCachedFiles() {
        coEvery { repository.getFormat8Data(any()) } coAnswers { Result.success(file(firstArg())) }
        val vm = viewModel()
        val tabs = manifest().sheetRegistry.getValue(parentId).subTabs!!
        vm.onTabSelected(parentId)
        vm.ensureFormat8TabLoaded(parentId)
        assertEquals(tabs.first().firestoreDocumentId, vm.uiState.value.selectedFormat8Sheets[parentId])
        for (tab in tabs.drop(1)) vm.onFormat8SubTabSelected(parentId, tab.firestoreDocumentId)
        assertEquals(6, vm.uiState.value.format8Cache.size)
        assertFalse(vm.uiState.value.isLoadingSheet)
        assertNull(vm.uiState.value.sheetError)
        vm.onTabSelected("quiz")
        vm.onTabSelected(parentId)
        assertEquals(tabs.last().firestoreDocumentId, vm.uiState.value.selectedFormat8Sheets[parentId])
        for (tab in tabs) {
            vm.onFormat8SubTabSelected(parentId, tab.firestoreDocumentId)
            coVerify(exactly = 1) { repository.getFormat8Data(tab.firestoreDocumentId) }
        }
    }

    @Test fun pronomenIIIHasNineSheetsAndKeepsItsSelectionSeparateFromPronomenII() {
        val objectParent = "GermanReferenceObjectPronouns"
        val config = manifest()
        val objectDefinition = config.sheetRegistry.getValue(objectParent)
        assertEquals("Pronomen III", objectDefinition.title)
        assertEquals(SheetDataType.FORMAT_8, objectDefinition.sheetDataType)
        assertEquals(ScreenType.GROUPED_FORMAT_8_SCREEN, objectDefinition.screenType)
        val tabs = objectDefinition.subTabs!!
        assertEquals(9, tabs.size)
        assertTrue(tabs.all { it.sheetDataType == SheetDataType.FORMAT_8 })
        assertTrue(config.layouts.referenceTab.order.contains(objectParent))
        coEvery { repository.getFormat8Data(any()) } coAnswers { Result.success(file(firstArg())) }
        val vm = viewModel(config)
        val previousDoc = config.sheetRegistry.getValue(parentId).subTabs!![2].firestoreDocumentId
        vm.onTabSelected(parentId)
        vm.onFormat8SubTabSelected(parentId, previousDoc)
        vm.onTabSelected(objectParent)
        vm.ensureFormat8TabLoaded(objectParent)
        assertEquals(tabs.first().firestoreDocumentId, vm.uiState.value.selectedFormat8Sheets[objectParent])
        for (tab in tabs.drop(1)) vm.onFormat8SubTabSelected(objectParent, tab.firestoreDocumentId)
        assertFalse(vm.uiState.value.isLoadingSheet)
        assertNull(vm.uiState.value.sheetError)
        vm.onTabSelected(parentId)
        assertEquals(previousDoc, vm.uiState.value.selectedFormat8Sheets[parentId])
        vm.onTabSelected(objectParent)
        assertEquals(tabs.last().firestoreDocumentId, vm.uiState.value.selectedFormat8Sheets[objectParent])
        for (tab in tabs) {
            vm.onFormat8SubTabSelected(objectParent, tab.firestoreDocumentId)
            val loaded = vm.uiState.value.format8Cache.getValue(tab.firestoreDocumentId)
            assertEquals(tab.firestoreDocumentId, loaded.sheetname)
            coVerify(exactly = 1) { repository.getFormat8Data(tab.firestoreDocumentId) }
        }
    }

    @Test fun initialFormat8SelectionLoadsWithoutATapAndNavigationDoesNotLoadItTwice() {
        val initial = manifest().let { it.copy(layouts = it.layouts.copy(referenceTab = LayoutDefinition(listOf(parentId)))) }
        val doc = initial.sheetRegistry.getValue(parentId).subTabs!!.first().firestoreDocumentId
        coEvery { repository.getFormat8Data(doc) } returns Result.success(file(doc))
        val vm = viewModel(initial)
        vm.ensureFormat8TabLoaded(parentId)
        assertEquals(parentId, vm.uiState.value.selectedTabId)
        assertNotNull(vm.uiState.value.format8Cache[doc])
        coVerify(exactly = 1) { repository.getFormat8Data(doc) }
    }

    @Test fun failedLoadExposesAnErrorAndCanBeRetried() {
        val doc = manifest().sheetRegistry.getValue(parentId).subTabs!!.first().firestoreDocumentId
        coEvery { repository.getFormat8Data(doc) } returnsMany listOf(
            Result.failure(IllegalStateException("Could not load sheet")), Result.success(file(doc))
        )
        val vm = viewModel()
        vm.onTabSelected(parentId)
        assertEquals("Could not load sheet", vm.uiState.value.sheetError)
        assertFalse(vm.uiState.value.isLoadingSheet)
        vm.onTabSelected(parentId)
        assertNull(vm.uiState.value.sheetError)
        assertNotNull(vm.uiState.value.format8Cache[doc])
        coVerify(exactly = 2) { repository.getFormat8Data(doc) }
    }

    @Test fun switchingSubTabsIgnoresOldLoadCompletionAndKeepsTheNewSpinner() {
        val tabs = manifest().sheetRegistry.getValue(parentId).subTabs!!
        val firstDoc = tabs[0].firestoreDocumentId
        val secondDoc = tabs[1].firestoreDocumentId
        val first = CompletableDeferred<Result<Format8File>>()
        val second = CompletableDeferred<Result<Format8File>>()
        coEvery { repository.getFormat8Data(firstDoc) } coAnswers { withContext(NonCancellable) { first.await() } }
        coEvery { repository.getFormat8Data(secondDoc) } coAnswers { second.await() }
        val vm = viewModel()
        vm.onTabSelected(parentId)
        assertTrue(vm.uiState.value.isLoadingSheet)
        vm.onFormat8SubTabSelected(parentId, secondDoc)
        first.complete(Result.failure(IllegalStateException("Old failure")))
        assertTrue(vm.uiState.value.isLoadingSheet)
        assertNull(vm.uiState.value.sheetError)
        assertEquals(secondDoc, vm.uiState.value.selectedFormat8Sheets[parentId])
        second.complete(Result.success(file(secondDoc)))
        assertFalse(vm.uiState.value.isLoadingSheet)
        assertNull(vm.uiState.value.sheetError)
        assertNotNull(vm.uiState.value.format8Cache[secondDoc])
    }

    @Test fun existingFormat2DispatchStillLoadsAndCaches() {
        val definition = SheetDefinition("Format 2", ScreenType.FORMAT_2_SCREEN, SheetDataType.FORMAT_2, "legacy")
        val config = AppUIManifest(mapOf("legacyTab" to definition), Layouts(referenceTab = LayoutDefinition(listOf("legacyTab"))))
        coEvery { repository.getFormat2Data("legacy") } returns Result.success(Format2File(fileformat = 2))
        val vm = viewModel(config)
        vm.onTabSelected("legacyTab")
        vm.onTabSelected("legacyTab")
        assertNotNull(vm.uiState.value.format2Cache["legacy"])
        coVerify(exactly = 1) { repository.getFormat2Data("legacy") }
    }
}
