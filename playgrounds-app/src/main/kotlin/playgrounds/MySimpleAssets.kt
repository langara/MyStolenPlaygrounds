package pl.mareklangiewicz.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import kotlinx.coroutines.*
import pl.mareklangiewicz.uwidgets.*

@Composable
fun MySimpleAssets(path: String) {
    var values by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val assets = LocalContext.current.assets
    LaunchedEffect(path, assets) {
        values = withContext(Dispatchers.IO) {
            assets.list(path).orEmpty().map { name ->
                val content = assets.open("$path/$name").bufferedReader().use { it.readLine().take(300) }
                name to content
            }
        }
    }
    MyFancyFrame(title = path) {
        Column {
            for ((name, content) in values) UColumn {
                Text(name)
                Text(content)
            }
        }
    }
}
