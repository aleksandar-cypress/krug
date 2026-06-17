package org.krug.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dagger.hilt.android.AndroidEntryPoint
import org.krug.app.navigation.KrugNavHost
import org.krug.app.ui.theme.KrugTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KrugApp()
        }
    }
}

@Composable
fun KrugApp() {
    KrugTheme {
        Surface(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        ) {
            KrugNavHost()
        }
    }
}

@Preview
@Composable
private fun KrugAppPreview() {
    KrugApp()
}
