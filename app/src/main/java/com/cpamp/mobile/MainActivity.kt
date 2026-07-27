package com.cpamp.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cpamp.mobile.ui.theme.CPAMPMobileTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CPAMPMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Welcome()
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CPAMP Mobile", style = MaterialTheme.typography.headlineMedium)
        Text("Secure remote management for CPA Manager Plus")
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    CPAMPMobileTheme { Welcome() }
}

