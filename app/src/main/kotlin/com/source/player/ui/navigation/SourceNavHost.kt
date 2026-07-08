package com.source.player.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.source.player.ui.components.Hairline
import com.source.player.ui.screens.*
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

// ---- Routes ----
object Routes {
  const val HOME = "home"
  const val LIBRARY = "library"
  const val SEARCH = "search"
  const val FOLDERS = "folders"
  const val SETTINGS = "settings"
  const val PLAYER = "player"
  const val QUEUE = "queue"
  const val ALBUM_DETAIL = "album/{albumId}"
  const val ARTIST_DETAIL = "artist/{artistId}"
  const val PLAYLIST_DETAIL = "playlist/{playlistId}"
  const val TAG_EDITOR = "tagedit/{songId}"

  fun albumDetail(albumId: Long) = "album/$albumId"
  fun artistDetail(artistId: Long) = "artist/$artistId"
  fun playlistDetail(playlistId: Long) = "playlist/$playlistId"
  fun tagEditor(songId: Long) = "tagedit/$songId"
}

data class BottomNavItem(
        val route: String,
        val label: String,
        val icon: ImageVector,
        val selectedIcon: ImageVector = icon,
)

// Vinyl bottom nav — Search is merged into Library (see SEARCH_MERGE.md).
// Folders is kept as a first-class destination, as requested.
val bottomNavItems =
        listOf(
                BottomNavItem(Routes.HOME, "Home", Icons.Rounded.Home),
                BottomNavItem(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
                BottomNavItem(Routes.QUEUE, "Queue", Icons.AutoMirrored.Rounded.QueueMusic),
                BottomNavItem(Routes.FOLDERS, "Folders", Icons.Rounded.Folder),
                BottomNavItem(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
        )

// Routes that should hide the bottom nav + mini player
private val fullScreenRoutes = setOf(Routes.PLAYER)

@Composable
fun SourceNavHost() {
  val navController = rememberNavController()
  val currentBackStack by navController.currentBackStackEntryAsState()
  val currentRoute = currentBackStack?.destination?.route

  val showBottomBar = currentRoute !in fullScreenRoutes

  Scaffold(
          bottomBar = {
            AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
            ) {
              SourceBottomNav(
                      navController = navController,
                      currentRoute = currentRoute,
              )
            }
          },
  ) { padding ->
    Box(Modifier.padding(padding)) {
      NavHost(
              navController = navController,
              startDestination = Routes.HOME,
              enterTransition = { fadeIn(tween(200)) + slideInHorizontally { it / 4 } },
              exitTransition = { fadeOut(tween(200)) },
              popEnterTransition = { fadeIn(tween(200)) },
              popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally { it / 4 } },
      ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.LIBRARY) { LibraryScreen(navController) }
        composable(Routes.SEARCH) { SearchScreen(navController) }
        composable(Routes.FOLDERS) { FoldersScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.PLAYER) { PlayerScreen(navController) }
        composable(Routes.QUEUE) { QueueScreen(navController) }
        composable(
                Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
        ) { AlbumDetailScreen(navController, it.arguments!!.getLong("albumId")) }
        composable(
                Routes.ARTIST_DETAIL,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
        ) { ArtistDetailScreen(navController, it.arguments!!.getLong("artistId")) }
        composable(
                Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
        ) { PlaylistDetailScreen(navController, it.arguments!!.getLong("playlistId")) }
        composable(
                Routes.TAG_EDITOR,
                arguments = listOf(navArgument("songId") { type = NavType.LongType }),
        ) { TagEditorScreen(navController, it.arguments!!.getLong("songId")) }
      }

      // MiniPlayer shown on all non-fullscreen routes
      if (showBottomBar) {
        Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
        ) {
          MiniPlayerBar(
                  modifier =
                          Modifier.padding(
                                  bottom =
                                          WindowInsets.navigationBars
                                                  .asPaddingValues()
                                                  .calculateBottomPadding()
                          ),
                  onTap = { navController.navigate(Routes.PLAYER) },
          )
        }
      }
    }
  }
}

@Composable
private fun SourceBottomNav(navController: NavController, currentRoute: String?) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(Modifier.background(colors.bg)) {
        Hairline()
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                        modifier =
                                Modifier.weight(1f)
                                        .clickable {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) colors.accent else colors.textMute,
                            modifier = Modifier.size(22.dp),
                    )
                    Text(
                            text = item.label,
                            style = text.pillLabel12,
                            color = if (selected) colors.accent else colors.textMute,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
