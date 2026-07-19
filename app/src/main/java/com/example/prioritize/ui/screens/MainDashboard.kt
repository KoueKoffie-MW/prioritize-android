package com.example.prioritize.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.prioritize.data.Task
import com.example.prioritize.ui.components.AddRepeatingTaskDialog
import com.example.prioritize.ui.components.AddSpecialDateDialog
import com.example.prioritize.ui.components.BreakdownDialog
import com.example.prioritize.ui.viewmodel.TaskViewModel
import androidx.compose.material.icons.filled.Add

/** Type-safe tab indices — eliminates magic integer comparisons throughout MainDashboard. */
private enum class DashboardTab { FOCUS, SCRATCH_PAD, MATRIX, HORIZON, BRAIN }

/** Single shared colour configuration for all NavigationBarItem instances. */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor  = Color.Black,
    selectedTextColor  = Color(0xFFBB86FC),
    unselectedIconColor = Color.Gray,
    unselectedTextColor = Color.Gray,
    indicatorColor     = Color(0xFFBB86FC)
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.FOCUS) }

    var showActionSheet by remember { mutableStateOf(false) }
    var showRepeatingDialog by remember { mutableStateOf(false) }
    var showSpecialDateDialog by remember { mutableStateOf(false) }

    // Hoisted breakdown dialog state — shared across all tabs so any future screen
    // can trigger a task breakdown without re-implementing the dialog locally.
    var activeTaskForBreakdown by remember { mutableStateOf<Task?>(null) }
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()

    if (showRepeatingDialog) {
        AddRepeatingTaskDialog(
            onDismiss = { showRepeatingDialog = false },
            onSave = { repTask ->
                viewModel.saveRepeatingTask(repTask)
                showRepeatingDialog = false
            }
        )
    }

    if (showSpecialDateDialog) {
        AddSpecialDateDialog(
            onDismiss = { showSpecialDateDialog = false },
            onSave = { date ->
                viewModel.saveSpecialDate(date)
                showSpecialDateDialog = false
            }
        )
    }

    // Single shared BreakdownDialog instance for all tabs. Triggered via onBreakdownClick
    // lambda passed into each child screen. This keeps the dialog off the back-stack and
    // avoids duplicating the dialog composition in every screen.
    activeTaskForBreakdown?.let { task ->
        BreakdownDialog(
            task = task,
            isModelAvailable = isModelAvailable,
            onGenerateLocal = {
                viewModel.parser.generateSubTasksLocally(task.title)
            },
            onGenerateCloudPrompt = {
                viewModel.parser.generateBreakdownPrompt(task.title)
            },
            onParsePastedText = { text ->
                viewModel.parser.parseSubTasksFromResponse(text)
            },
            onSaveSubTasks = { subs ->
                viewModel.saveSubTasks(subs)
            },
            onDismiss = { activeTaskForBreakdown = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF151522)) {
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.FOCUS,
                    onClick  = { selectedTab = DashboardTab.FOCUS },
                    icon     = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Focus List") },
                    label    = { Text("Focus") },
                    colors   = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.SCRATCH_PAD,
                    onClick  = { selectedTab = DashboardTab.SCRATCH_PAD },
                    icon     = { Icon(Icons.Default.Create, contentDescription = "Scratch Pad") },
                    label    = { Text("Scratch Pad") },
                    colors   = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.MATRIX,
                    onClick  = { selectedTab = DashboardTab.MATRIX },
                    icon     = { Icon(Icons.Default.Star, contentDescription = "Matrix") },
                    label    = { Text("Matrix") },
                    colors   = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.HORIZON,
                    onClick  = { selectedTab = DashboardTab.HORIZON },
                    icon     = { Icon(Icons.Default.DateRange, contentDescription = "Horizon") },
                    label    = { Text("Horizon") },
                    colors   = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.BRAIN,
                    onClick  = { selectedTab = DashboardTab.BRAIN },
                    icon     = { Icon(Icons.Default.Face, contentDescription = "Brain Profile") },
                    label    = { Text("Brain") },
                    colors   = navItemColors()
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == DashboardTab.HORIZON || selectedTab == DashboardTab.FOCUS) {
                Box {
                    FloatingActionButton(
                        onClick = { showActionSheet = !showActionSheet },
                        containerColor = Color(0xFFBB86FC),
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    
                    DropdownMenu(
                        expanded = showActionSheet,
                        onDismissRequest = { showActionSheet = false },
                        modifier = Modifier.background(Color(0xFF28283C))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Repeating Task", color = Color.White) },
                            onClick = {
                                showActionSheet = false
                                showRepeatingDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Important Date", color = Color.White) },
                            onClick = {
                                showActionSheet = false
                                showSpecialDateDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val isKeyboardVisible = WindowInsets.isImeVisible
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    bottom = if (isKeyboardVisible) 0.dp else innerPadding.calculateBottomPadding()
                )
                .background(Color(0xFF0F0F1A))
        ) {
            // Animated tab transitions: higher-index tabs slide in from right
            @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally { if (forward) it else -it } + fadeIn()
                    val exit  = slideOutHorizontally { if (forward) -it else it } + fadeOut()
                    enter togetherWith exit
                },
                label = "tabTransition"
            ) { tab ->
                // Exhaustive when — compiler enforces all DashboardTab cases are handled
                when (tab) {
                    DashboardTab.FOCUS      -> FocusListScreen(
                        viewModel = viewModel,
                        onBreakdownClick = { task -> activeTaskForBreakdown = task }
                    )
                    DashboardTab.SCRATCH_PAD -> ScratchPadScreen(viewModel = viewModel)
                    DashboardTab.MATRIX     -> MatrixScreen(viewModel = viewModel)
                    DashboardTab.HORIZON    -> HorizonScreen(viewModel = viewModel)
                    DashboardTab.BRAIN      -> BrainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
