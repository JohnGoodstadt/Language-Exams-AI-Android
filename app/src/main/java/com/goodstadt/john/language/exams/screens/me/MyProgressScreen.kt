package com.goodstadt.john.language.exams.screens.me


//import com.goodstadt.john.language.exams.ui.gamification.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.screens.shared.BadgeShowcaseSection
import com.goodstadt.john.language.exams.screens.shared.gamification.AIWriterCard
import com.goodstadt.john.language.exams.screens.shared.gamification.AuditDashboardHeader
import com.goodstadt.john.language.exams.screens.shared.gamification.GlobalProgressRow
import com.goodstadt.john.language.exams.screens.shared.gamification.QuickReferenceRow
import com.goodstadt.john.language.exams.screens.shared.gamification.QuizMasteryCard
import com.goodstadt.john.language.exams.screens.shared.gamification.ReferenceGroupCard
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import com.goodstadt.john.language.exams.screens.shared.gamification.SkillBreakdownView
import com.goodstadt.john.language.exams.screens.shared.gamification.TopicProgressRow
import com.goodstadt.john.language.exams.screens.shared.gamification.XPSummaryCard
import com.goodstadt.john.language.exams.viewmodels.ActivityChartCard
import com.goodstadt.john.language.exams.viewmodels.ConsistencyHeatmap
import com.goodstadt.john.language.exams.viewmodels.LifetimeStatsGrid
import com.goodstadt.john.language.exams.viewmodels.MyProgressViewModel
import com.goodstadt.john.language.exams.viewmodels.ProfileHeaderView
import com.goodstadt.john.language.exams.viewmodels.SectionHeader
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProgressScreen(
    xpManager: XPManager,
    viewModel: MyProgressViewModel = hiltViewModel(),
    onNavigate: (SideQuestNavTarget) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val xpState by xpManager.state.collectAsState()
    val levelInfo = remember(xpState) {
        xpManager.getLevelProgress(xpState.currentLevel)
    }

    val auditStats by viewModel.auditStats.collectAsState()
    val currentSkillLevel by viewModel.currentSkillLevel.collectAsState(initial = "B1")
    val unlockedLevels by viewModel.unlockedAuditLevels.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->

        if (uiState == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val  state = uiState!!

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // ==========================================
                // 🟢 NEW: AUDIT DASHBOARD HEADER
                // ==========================================
                AuditDashboardHeader(
                    stats = auditStats,
                    currentLevel = currentSkillLevel,
                    unlockedLevels = unlockedLevels,
                    onNavigateToAudit = {
                        // Use your existing navigation logic to jump to the Audit
                        //onNavigate(SideQuestNavTarget.Reference)
                        // Note: You may need a specific target like:
                        // onNavigate(SideQuestNavTarget.StartAudit)
                        Timber.e("onNavigateToAudit")
                    },
                    onAdjustLevel = {
                        // Action to trigger level picker
                    },
                    onResetAudit = {
                        viewModel.resetAudit()
                    }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // ==========================================
                // 1. GLOBAL OVERVIEW (Identity & Habits)
                // ==========================================

                ProfileHeaderView(
                    userLevel = state.xpState.currentLevel, // e.g. "B1"
                    streak = state.xpState.currentStreak,
                    gems = state.xpState.gems
                )

                
                ConsistencyHeatmap(xpManager = viewModel.getXpManager()) // Assuming you exposed the getter

                Spacer(modifier = Modifier.height(12.dp))

                ActivityChartCard(xpManager = viewModel.getXpManager())

                LifetimeStatsGrid(
                    totalXP = state.xpState.levels.values.sumOf { it.xp },
                    totalXPString = "${levelInfo.currentXPInBracket}/${levelInfo.requiredXPForBracket}",
                    badges = state.xpState.earnedBadges.size,
                    longestStreak = state.xpState.longestStreak,
                    gems = state.xpState.gems,
                    userType = viewModel.getXpManager().getUserType()
                )

                XPSummaryCard(
                    xpState = xpState,
                    progressInfo = levelInfo
                )

                SkillBreakdownView(xpManager)

                // ==========================================
                // 2. MAIN QUEST (Vocab)
                // ==========================================

                SectionHeader(title = "Main Quest", icon = Icons.Default.Map, color = Color(0xFF2196F3)) // Blue

                if (state.mainQuestProgress.isNotEmpty()) {
                    // Reusing the TopicMastery Row logic but inside a Card
                    val (grandTotalMastered, grandTotalWords) = viewModel.calculateGrandTotals()
                    GlobalProgressRow(
                        heard = grandTotalMastered,
                        total = grandTotalWords
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            // ✅ MAKE THE WHOLE CARD CLICKABLE
                            .clickable {
                                // 0 represents the first tab (Screen.Tab1)
                                onNavigate(SideQuestNavTarget.MainTab(0))
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Show top 5 or all? Let's show all for the full page
                            state.mainQuestProgress.forEach { category ->
                                TopicProgressRow(category = category)
                            }
                        }
                    }
                } else {
                    Text("Start listening to vocab lists to see data here.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }

                // ==========================================
                // 3. SIDE QUESTS (Bonus)
                // ==========================================

                SectionHeader(title = "Side Quests", icon = Icons.Default.AutoAwesome, color = Color(0xFF9C27B0)) // Purple

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {




                    // C. Reference
                    state.sideQuestData?.let { refData ->
                        ReferenceGroupCard(category = refData.conjugations, color = Color(0xFF3F51B5),onNavigate = onNavigate )
                        ReferenceGroupCard(category = refData.adjectives, color = Color(0xFF009688),onNavigate = onNavigate )
                        ReferenceGroupCard(category = refData.pairs, color = Color(0xFFFF9800),onNavigate = onNavigate )

                        SectionHeader(title = "Quick Reference", icon = Icons.Default.AutoAwesome, color = Color(0xFF9C27B0)) // Purple
                        // Quick Refs
                        refData.quickRefs.forEach {
                            QuickReferenceRow(category = it,onNavigate = onNavigate)
                        }
                    }
                }

                SectionHeader(title = "Bonus", icon = Icons.Default.AutoAwesome, color = Color(0xFF9C27B0)) // Purple
                // A. Quiz
                QuizMasteryCard(quizManager = viewModel.quizManager,onNavigate = onNavigate )

                // B. AI
                AIWriterCard(count = state.aiCount, heard = state.aiHeard,onNavigate = onNavigate )

                BadgeShowcaseSection(xpState = state.xpState)
                // ==========================================
                // 4. FOOTER / ADMIN
                // ==========================================

                // Only show in Debug builds or if you have a dev toggle
                // if (BuildConfig.DEBUG) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Zone of Danger", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.hardReset() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset All Progress")
                    }
                }
            }
        }
    }
}