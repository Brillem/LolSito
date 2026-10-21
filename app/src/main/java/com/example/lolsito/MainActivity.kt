package com.example.lolsito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.lolsito.navigation.LolSitoApp
import com.example.lolsito.ui.theme.LolSitoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LolSitoTheme {
                LolSitoApp(container = (application as LolSitoApplication).container)
            }
        }
    }
}

