package com.extrairapk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.extrairapk.app.ui.screens.AppListScreen
import com.extrairapk.app.ui.theme.ExtrairApkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExtrairApkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppListScreen(viewModel = viewModel)
                }
            }
        }
    }
}
