package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.network.HttpException
import com.example.data.util.TcgdexHelper

/** Only missing assets advance to another language; transient failures remain retryable. */
@Composable
fun PocketCardImage(id: String, name: String, language: String = "es",
  highResolution: Boolean = false, modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Fit,
  unavailable: @Composable () -> Unit = { Text(name, style = MaterialTheme.typography.labelSmall) }) {
  val candidates = remember(id, language, highResolution) { TcgdexHelper.imageCandidates(id, language, highResolution) }
  var index by remember(candidates) { mutableStateOf(0) }
  var failed by remember(candidates) { mutableStateOf(false) }
  var loading by remember(candidates) { mutableStateOf(true) }
  var retry by remember(candidates) { mutableStateOf(0) }
  Box(modifier, contentAlignment = Alignment.Center) {
    if (failed) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { unavailable() }
      TextButton(onClick = { index = 0; failed = false; loading = true; retry++ }, contentPadding = PaddingValues(2.dp)) {
        Text("Reintentar", style = MaterialTheme.typography.labelSmall)
      }
    } else key(retry) {
      AsyncImage(model = candidates[index], contentDescription = name, contentScale = contentScale,
        modifier = Modifier.fillMaxSize(), onLoading = { loading = true },
        onSuccess = { loading = false }, onError = { state ->
          if ((state.result.throwable as? HttpException)?.response?.code == 404 && index < candidates.lastIndex) index++
          else { failed = true; loading = false }
        })
    }
    if (loading && !failed) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
  }
}
