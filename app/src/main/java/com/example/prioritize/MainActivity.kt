package com.example.prioritize

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.prioritize.data.TaskDatabase
import com.example.prioritize.data.TaskRepository
import com.example.prioritize.theme.PrioritizeTheme
import com.example.prioritize.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    private val database by lazy { TaskDatabase.getDatabase(applicationContext) }
    private val repository by lazy { TaskRepository(database.taskDao()) }

    private val viewModel: TaskViewModel by viewModels {
        TaskViewModel.Factory(application, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            PrioritizeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(viewModel)
                }
            }
        }
    }
}
