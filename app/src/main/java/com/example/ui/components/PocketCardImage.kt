package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.network.HttpException
import com.example.data.util.TcgdexHelper
import com.example.data.util.ImageAvailability

/** Only missing assets advance to another language; transient failures remain retryable. */
@Composable
fun PocketCardImage(id: String, name: String, language: String = "es",
  highResolution: Boolean = false, modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Fit,
  unavailable: @Composable () -> Unit = { Text(name, style = MaterialTheme.typography.labelSmall) }) {
  val allCandidates = remember(id, language, highResolution) { TcgdexHelper.imageCandidates(id, language, highResolution) }
  var candidates by remember(allCandidates) { mutableStateOf(ImageAvailability.session.candidates(allCandidates)) }
  var index by remember(candidates) { mutableStateOf(0) }
  var failed by remember(candidates) { mutableStateOf(candidates.isEmpty()) }
  var loading by remember(candidates) { mutableStateOf(true) }
  var retry by remember(candidates) { mutableStateOf(0) }
  BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val compact = maxWidth < 100.dp || maxHeight < 120.dp
    val retryImage: () -> Unit = {
      ImageAvailability.session.reset(allCandidates)
      com.example.data.network.PocketHttp.missingResources.reset(allCandidates)
      candidates = allCandidates; index = 0; failed = false; loading = true; retry++
    }
    if (failed) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { unavailable() }
      if (compact) IconButton(onClick = retryImage) {
        Icon(Icons.Default.Refresh, contentDescription = "Reintentar imagen de $name")
      } else TextButton(onClick = retryImage, contentPadding = PaddingValues(2.dp)) {
        Text("Reintentar", style = MaterialTheme.typography.labelSmall)
      }
    } else key(retry) {
      AsyncImage(model = candidates[index], contentDescription = name, contentScale = contentScale,
        modifier = Modifier.fillMaxSize(), onLoading = { loading = true },
        onSuccess = { loading = false; ImageAvailability.session.recordSuccess(candidates[index]) }, onError = { state ->
          if ((state.result.throwable as? HttpException)?.response?.code == 404) {
            ImageAvailability.session.recordMissing(candidates[index])
            if (index < candidates.lastIndex) index++ else { failed = true; loading = false }
          } else { failed = true; loading = false }
        })
    }
    if (loading && !failed) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
  }
}
