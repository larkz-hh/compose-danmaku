package xyz.larkzhh.compose_danmaku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.larkzhh.compose_danmaku.ui.theme.ComposedanmakuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposedanmakuTheme {
                DanmakuDemo()
            }
        }
    }
}