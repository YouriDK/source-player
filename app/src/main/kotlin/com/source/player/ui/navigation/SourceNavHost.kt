package com.source.player.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.source.player.ui.screens.*

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

val bottomNavItems =
        listOf(
                BottomNavItem(Routes.HOME, "Home", Icons.Rounded.Home),
                BottomNavItem(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
                BottomNavItem(Routes.SEARCH, "Search", Icons.Rounded.Search),
                BottomNavItem(Routes.FOLDERS, "Folders", Icons.Rounded.Folder),
                BottomNavItem(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
        )

// Routes that should hide the bottom nav + mini player
private val fullScreenRoutes = setOf(Routes.PLAYER, Routes.QUEUE)

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
  NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = androidx.compose.ui.unit.Dp.Unspecified,
  ) {
    bottomNavItems.forEach { item ->
      val selected = currentRoute == item.route
      NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(item.route) {
                  popUpTo(navController.graph.startDestinationId) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = { Icon(item.icon, contentDescription = item.label) },
              label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
              colors =
                      NavigationBarItemDefaults.colors(
                              selectedIconColor = MaterialTheme.colorScheme.primary,
                              selectedTextColor = MaterialTheme.colorScheme.primary,
                              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                              indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      ),
      )
    }
  }
}
