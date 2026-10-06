package app.aurora

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionName", "unused") // Lo llama iosApp desde Swift.
fun MainViewController() = ComposeUIViewController { App() }
