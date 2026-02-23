package com.source.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
        navController: NavController,
        vm: PlayerViewModel = hiltViewModel(),
) {
  val queue by vm.queueItems.collectAsState()
  val currentIndex by vm.queueIndex.collectAsState()

  Column(Modifier.fillMaxSize().systemBarsPadding()) {
    // Top bar
    TopAppBar(
            title = { Text("Queue", style = MaterialTheme.typography.headlineLarge) },
            navigationIcon = {
              IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Back")
              }
            },
            colors =
                    TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                    ),
    )

    Text(
            text = "${queue.size} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
      itemsIndexed(queue, key = { i, _ -> i }) { i, item ->
        val isCurrentTrack = i == currentIndex
        ListItem(
                headlineContent = {
                  Text(
                          item.mediaMetadata.title?.toString() ?: "—",
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          color =
                                  if (isCurrentTrack) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurface,
                          style =
                                  if (isCurrentTrack) MaterialTheme.typography.titleMedium
                                  else MaterialTheme.typography.bodyMedium,
                  )
                },
                supportingContent = {
                  Text(
                          item.mediaMetadata.artist?.toString() ?: "—",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                leadingContent = {
                  if (isCurrentTrack) {
                    Icon(
                            Icons.Rounded.VolumeUp,
                            "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                  } else {
                    Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 24.dp)
                    )
                  }
                },
                trailingContent = {
                  Icon(
                          Icons.Rounded.DragHandle,
                          "Drag",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                modifier = Modifier.clickable { vm.skipToQueueItem(i) }.animateItem(),
                colors =
                        ListItemDefaults.colors(
                                containerColor =
                                        if (isCurrentTrack)
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.3f
                                                )
                                        else MaterialTheme.colorScheme.background,
                        ),
        )
        HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
        )
      }
    }
  }
}
