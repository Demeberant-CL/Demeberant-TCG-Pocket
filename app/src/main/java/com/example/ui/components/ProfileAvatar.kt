package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.ProfileAvatars

private object TrainerAtlas {
  @Volatile private var bitmap: ImageBitmap? = null
  @Synchronized fun get(resources: android.content.res.Resources): ImageBitmap =
    bitmap ?: ImageBitmap.imageResource(resources, R.drawable.trainer_avatars).also { bitmap = it }
}

@Composable
fun ProfileAvatar(id: String, modifier: Modifier = Modifier, description: String = "Avatar de perfil") {
  val atlas = TrainerAtlas.get(LocalContext.current.resources)
  val index = ProfileAvatars.ids.indexOf(ProfileAvatars.normalize(id))
  Canvas(modifier.clip(CircleShape).semantics { contentDescription = description }) {
    drawImage(atlas, srcOffset = IntOffset(index % 3 * atlas.width / 3, index / 3 * atlas.height / 2),
      srcSize = IntSize(atlas.width / 3, atlas.height / 2), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
  }
}

@Composable
fun AvatarPickerDialog(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
  var selected by rememberSaveable(current) { mutableStateOf(ProfileAvatars.normalize(current)) }
  AlertDialog(onDismissRequest = onClose, title = { Text("Elige tu avatar") }, text = {
    LazyVerticalGrid(columns = GridCells.Adaptive((80f * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp), modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      items(ProfileAvatars.ids.size) { n ->
        val id = ProfileAvatars.ids[n]
        Column(Modifier.clickable { selected = id }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          ProfileAvatar(id, Modifier.size(72.dp).border(if (id == selected) 3.dp else 0.dp,
            MaterialTheme.colorScheme.primary, CircleShape), ProfileAvatars.names[n])
          Text(if (id == selected) "Elegido" else ProfileAvatars.names[n], style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }, confirmButton = { Button(onClick = { onSave(selected) }) { Text("Usar avatar") } },
    dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}
