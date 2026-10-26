# Plan : indexer le start folder Freebox (mode Local / Stream)

Cadré le 08/10/2026, réalisé en 2.8.0 (09/10/2026). Conservé comme trace des décisions produit.

## Décisions produit (validées)

- Indexer dans Room UNIQUEMENT le start folder Freebox (API Freebox OS : `fs/ls` + HTTP Range sur `/dl/`).
- **Interrupteur « Mode » dans les Réglages : Local ou Stream.** Les deux sources ne se mélangent jamais.
  Bibliothèque, accueil, recherche, Dossiers : filtrés sur la source active. Pas de badge, pas de grisé.
- **Playlists séparées par mode** (une config avec réseau, une sans).
- En Stream, box injoignable : bandeau « Freebox injoignable » en haut de la bibliothèque, lecture échoue proprement.
- Changer de mode ne touche pas la file de lecture en cours.
- Scan incrémental (taille + mtime du listing). Rescan auto au lancement en Wi-Fi seulement, max 1 parcours complet / 30 min,
  + bouton Rescan existant. Scan automatique jamais en 4G ; scan manuel possible en 4G avec avertissement. Premier scan en arrière-plan, non bloquant.
- Changement de start folder = purge + scan du nouveau. Pas de start folder ou dé-appairage = purge.
- Pochettes mises en cache sur le téléphone au scan.
- **Un seul dossier musique Freebox, sur un seul disque.** Il est choisi une fois, juste après l'appairage
  (navigateur en mode sélection, les disques ne sont visibles QUE là ; proposer d'office un dossier Music/Musique trouvé
  à la racine d'un disque), modifiable dans Réglages → Freebox → « Dossier musique : … [Modifier] ».
- **Ce dossier est la racine de tout ce que l'app montre côté Freebox** : le fil d'Ariane s'arrête à lui, impossible de remonter aux disques.
- **Plus d'écran Freebox séparé** : en mode Stream, l'onglet Dossiers = le dossier musique Freebox en navigation live, même UI que
  Dossiers en Local. L'entrée « Freebox » de la liste Dossiers disparaît. La pref `startFolder` devient « dossier musique ».
- **En Stream sans dossier choisi** : écran vide avec un bouton « Choisir le dossier de musique » (on peut passer en Stream avant de choisir).
- La section Dossiers en mode Local reste telle quelle.

## Détails tranchés le 09/10/2026

1. Après le choix du dossier : bascule automatique en mode Stream, premier scan lancé en arrière-plan, l'utilisateur garde la main.
2. Pendant le premier scan : rien ne bloque. Bibliothèque et accueil se remplissent par paquets de 200 ; avant la 1re écriture,
   état vide « Indexation en cours… » + bandeau de progression discret (pas « No music found »).
3. Changement de dossier musique : les playlists Stream sont conservées, entrées orphelines masquées, réapparaissent si le même dossier est rechoisi.
4. Accueil en Stream : `dateAdded` = mtime à la 1re détection (pas 3 000 nouveautés au 1er scan). **Chantier séparé à venir** :
   repenser la page d'accueil, l'utilisateur n'ajoute pas de nouveaux sons régulièrement (hors périmètre de ce plan).
5. « Aller à l'album / l'artiste » depuis la file de lecture fonctionne même si la piste n'est pas du mode actif (chargement par id).
6. Appairage révoqué depuis Freebox OS : bandeau « Appairage perdu, réappairer » en Stream ; retour en Local possible à tout moment.
7. Disque renommé / dossier introuvable : bandeau + bouton « Choisir le dossier de musique » qui rouvre le sélecteur ; PAS de purge
   tant qu'un nouveau dossier n'a pas été choisi.
8. Accès distant (4G, hors LAN) : **la lecture doit marcher comme en Wi-Fi** (accès distant Freebox configuré, mode REMOTE de
   `EndpointResolver`). Scan automatique : Wi-Fi seulement. Scan manuel (bouton Rescan) : autorisé en 4G avec un avertissement
   sur le volume de données (~0,5 à 1 Mo par nouveau fichier). Cast/Sonos : même réseau que l'enceinte, inhérent.
9. Réglages → Freebox : « Vider le cache des pochettes » avec la taille occupée.
10. Permission audio du téléphone : demandée au lancement comme aujourd'hui, mais la refuser ne bloque pas le mode Stream.
11. Version 2.8.0.

## Constats sur le code (HEAD c735f3c)

- La DB est en v2 (`app/schemas/.../2.json`), pas de `Migrations.kt`, pas d'`addMigrations`.
- **`MediaScanner` supprime les orphelins sur TOUTE la table** (songs, albums, artists, genres) : sans correction,
  tout scan local effacerait les lignes Freebox. Bloquant, à faire en premier.
- `FoldersViewModel` calcule la racine par plus long préfixe commun sur `songDao.getAllFlow()` : il faut filtrer sur local.
- La lecture est déjà prête : `PlaybackController.songEntityToMediaItem` fait `setUri(song.path)` ; avec `path = fbx://…`
  (même URL que l'écran live via `FreeboxPaths.toUrl`) on passe par `FreeboxDataSource`, `FreeboxStreamTags`, proxy `/fbx/` pour Cast/Sonos.
- Ids négatifs OK (queue persistée `toLongOrNull`, routes `NavType.LongType`).
- **Bug latent `PlayerScreen.kt` ~l.422** : le lien éditeur de tags est bloqué via `isFreeboxUrl(song?.mediaId)` ; pour une piste
  indexée le mediaId est numérique → tester `localConfiguration?.uri`.
- `FreeboxApiClient.download()` charge tout le corps en mémoire : ne pas l'utiliser pour lire les tags ; `openDownloadStream` existe.
- `FreeboxSessionManager.withSession` refait un login sur 403 / auth_required / invalid_token.
- `EndpointResolver.current` n'est jamais invalidé au changement de réseau ; REMOTE ≠ joignable (pas de vérif).
- Pas de WorkManager, pas de tests. Modèle de composant applicatif : `MediaStoreWatcher` (singleton + scope, démarré dans `SourceApplication`).
- Code de référence à reprendre : `git show 1dcc9da:app/src/main/kotlin/com/source/player/data/smb/SmbScanner.kt`
  (+ `SmbIds.kt`, `SmbArtCache.kt`, `SmbMediaDataSource.kt`, `data/scanner/AlbumArtEnricher.kt`, diff de `MediaScanner.kt`,
  `Entities.kt`, `Daos.kt`, `Migrations.kt`, `SongOptionsSheet.kt`, `TagEditorScreen.kt`, `TagEditorViewModel.kt`).

## Lots

Ordre : 0 → (1 ‖ 4) → 2 → 3 → 5.

### Lot 0 — Schéma v3 + scanners par source (~0,5 j, opus)
- `Entities.kt` : `SOURCE_LOCAL="local"`, `SOURCE_FREEBOX="freebox"`, colonne `source` (default local) + `Index("source")`
  sur songs/albums/artists/genres ; `SongEntity.remoteMtime: Long = 0` ; extensions `isRemote`.
- **Playlists par mode** : colonne `source` sur `PlaylistEntity` (default local), index ; les DAO playlists filtrent par source.
- `Migrations.kt` recréé : `MIGRATION_2_3` (repris de 1dcc9da + `remoteMtime` + `playlists.source`). Le DDL doit correspondre
  exactement à ce que Room génère (noms d'index `index_<table>_source`, defaults). `AppDatabase.version = 3`, `addMigrations` dans `di/Modules.kt`.
- `Daos.kt` : `getIdsBySource`, `getAllBySource`, `getAllBySourceFlow`, `countBySourceFlow`, `deleteBySource` sur les 4 DAO + playlists.
- `MediaScanner.kt` : diff de 1dcc9da (previous/deleteOrphans limités à SOURCE_LOCAL, Last.fm délégué à `AlbumArtEnricher`).
- `FoldersViewModel.kt` : `getAllBySourceFlow(SOURCE_LOCAL)`.
- Vérif : `./gradlew :app:assembleDebug`, `3.json` généré et commité, comparer à `git show 1dcc9da:app/schemas/.../3.json`.
  Installer HEAD (v2) avec playlists puis le nouveau build par-dessus : app s'ouvre, playlists intactes, scan local OK.

### Lot 1 — Accès Freebox borné + joignabilité (~0,5 j, opus, ‖ lot 0)
- `FreeboxApiClient.readRange(base, token, pathB64, offset, length)` via `openDownloadStream` + header Range, lit au plus `length`
  octets que la box réponde 206 ou 200, mappe les statuts vers `FreeboxError` (403 → re-login via `withSession`).
- `FreeboxClient.readRange(url, offset, length, requireLan=false)` ; `requireLan=true` lève `FreeboxUnreachableException`
  si mode ≠ LAN (garde 4G). `currentMode()`.
- Nouveau `FreeboxReachability` (@Singleton) : `StateFlow<Reach>` {UNKNOWN, LAN, REMOTE, UNREACHABLE, NOT_PAIRED}, `available`,
  `probe()` = `checkConnection()` (login réel), `registerDefaultNetworkCallback` → `resolver.invalidate()` + `dropSession()` + probe debounce 2 s,
  max 1 probe / 30 s sauf forcé, jamais sur le main thread. `reportFailure()` branché dans `FreeboxDataSource`.
- Ajouter `EndpointResolver.invalidate()` (corrige aussi la lecture live avec base LAN périmée).
- Vérif : build, écran live non régressé, couper le Wi-Fi en lecture → REMOTE confirmé ou UNREACHABLE, retour Wi-Fi détecté.

### Lot 2 — `FreeboxLibraryScanner` (~1–1,5 j, opus, après 0 et 1)
Package `data/freebox/library/` : `FreeboxIds` (= SmbIds, FNV-1a 64 bits négatif), `FreeboxArtCache` (= SmbArtCache, `filesDir/freebox_art`),
`FreeboxMediaDataSource` (MediaDataSource : `getSize` = taille du listing, `readAt` par blocs 128 Ko, LRU 16 blocs, préchargement 256 Ko,
plafond ~16 Mo/fichier → fallback tags déduits du nom), `FreeboxLibraryScanner` (port de SmbScanner : parcours en largeur `client.list`,
MAX_DEPTH 32, extensions audio mutualisées avec FreeboxViewModel, échec racine = abandon, échec sous-dossier = passe partielle sans suppression ;
incrémental sur `size && remoteMtime` ; sémaphore 3 ; `path = fbx url`, `folderPath = parentUrl`, `source = FREEBOX`, `dateAdded` = mtime à la
1re détection ; upsert par lots de 200 ; albums/artistes/genres recalculés depuis la DB à la fin ; suppression des orphelins si parcours complet ;
`fetchFolderCovers` plafonné 10 Mo ; `clearGeneration` + `writeMutex` ; fusion des scans concurrents ; `AlbumArtEnricher` après scan ;
API `progress`, `progressMessage`, `lastError`, `scan(root)`, `purge()` ; tout `list`/`readRange` en `requireLan=true`).
Vérif sur la vraie box : 1er scan (octets/fichier en logcat, durée, pas de 403 non récupéré), 2e scan sans readRange, toucher/ajouter/supprimer un fichier.

### Lot 3 — Orchestration (~0,5 j, opus, après 1 et 2)
`FreeboxLibrarySync` (@Singleton, modèle MediaStoreWatcher) : observe `client.startFolder` vs `prefs.freeboxIndexedRoot` → purge + scan forcé ;
root null → purge. `maybeScan(force)` : root + appairé + probe == LAN + réseau non facturé ; hors forcé, dernier scan complet > 30 min.
`SourceApplication.start()`, `MainActivity.onStart → maybeScan()` avant le test de permission audio. Prefs `freeboxIndexedRoot`, `freeboxLastScanAt`,
effacées dans `clearFreeboxSettings`. `SettingsViewModel.scanLibrary()` lance local + Freebox forcé (en 4G : dialogue d'avertissement données puis scan avec `requireLan=false`) ; sous-titre combiné ;
dialogue Freebox « N titres indexés · dernier scan » + `lastError`. `HomeViewModel` : progression combinée.

### Lot 4 — UI mode Local / Stream (~0,5 j, sonnet, après 0)
- Pref `libraryMode` (LOCAL / STREAM) dans DataStore ; section Réglages avec l'interrupteur, visible seulement si Freebox appairée.
  Si dé-appairage en mode Stream → retour Local.
- Tous les ViewModels de bibliothèque / accueil / recherche / playlists lisent les Flow filtrés par la source active
  (`flatMapLatest` sur la pref).
- Dossiers en Stream : réutiliser `FreeboxScreen`/`FreeboxViewModel` comme contenu de l'onglet, racine = dossier musique
  (breadcrumbs tronqués à cette racine, pas de `goUp` au-dessus), retirer la ligne « Freebox » de `FoldersScreen`, retirer
  « Set as start folder / Clear » du menu. Sans dossier choisi : état vide + bouton « Choisir le dossier de musique ».
- Sélecteur de dossier : `FreeboxScreen` en mode `pick` (racine = disques, bouton « Choisir ce dossier » sur chaque écran),
  lancé automatiquement après un appairage réussi et depuis Réglages → Freebox → Modifier. Suggestion automatique d'un dossier
  Music/Musique/music trouvé à la racine d'un disque.
- En Stream + `FreeboxReachability.available == false` : bandeau « Freebox injoignable » en tête de bibliothèque ; `_playbackError` à la lecture.
- Changement de mode : la file en cours n'est pas touchée.
- Éditeur de tags : masquer « Edit Tags » dans `SongOptionsSheet` si `isRemote`, désactiver dans `TagEditorScreen` / `TagEditorViewModel.save()`,
  corriger le test de `PlayerScreen` (URI, pas mediaId).
- Reprise Sonos : `resolveSongFromSonosUri` ne retrouve pas `/fbx/` → conversion vers `fbx://` (optionnel).

### Lot 5 — Recette manuelle (~0,5 j)
Appairage → start folder → 1er scan avec progression ; lecture locale / Cast / Sonos en LAN puis 4G (lecture OK, scan interdit) ;
coexistence (scan local ne change pas `countBySource(freebox)` et inversement) ; bascule de mode ; playlists par mode ;
redémarrage de la box pendant un scan (passe partielle, pas de suppression, erreur dans Réglages) ; session expirée → re-login ;
écran live et Dossiers locale non régressés.

## Effort total ~4 j (lot 4 passe à ~1 j avec le sélecteur et l'onglet Dossiers en Stream)
