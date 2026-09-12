package com.seweryn.radiocar.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seweryn.radiocar.ui.RadioViewModel
import org.junit.Rule
import org.junit.Test

class RadioScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun radioScreen_rendersSuccessfully() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RadioViewModel(app)
        composeTestRule.setContent {
            RadioScreen(viewModel = viewModel)
        }
    }
}
