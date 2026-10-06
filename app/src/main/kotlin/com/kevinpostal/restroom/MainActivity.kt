package com.kevinpostal.restroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinpostal.restroom.ui.FinderScreen
import com.kevinpostal.restroom.ui.RestroomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = UiTestMode(intent)
        if (mode.active && savedInstanceState == null) Core.init("", nowSec())   // memory only, deterministic
        val source: DataSource = if (mode.active) FixtureNet(mode) else Net(applicationContext)
        setContent {
            RestroomTheme {
                val vm: FinderViewModel = viewModel(factory = viewModelFactory { initializer { FinderViewModel(source) } })
                FinderScreen(vm, mode)
            }
        }
    }
}
