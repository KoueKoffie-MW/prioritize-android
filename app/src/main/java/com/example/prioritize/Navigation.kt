package com.example.prioritize

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.prioritize.ui.screens.MainDashboard
import com.example.prioritize.ui.viewmodel.TaskViewModel

@Composable
fun MainNavigation(viewModel: TaskViewModel) {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    )
}
