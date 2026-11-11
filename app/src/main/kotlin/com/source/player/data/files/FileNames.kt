package com.source.player.data.files

/** Pure naming rules shared by the Folders tab's move / delete operations. */
object FileNames {

    /**
     * [name] if no entry of [taken] already uses it (case-insensitive: Freebox disks are
     * often FAT/exFAT/NTFS), else the first free "name (n).ext" — "song.flac" →
     * "song (1).flac", folder "Vol. 2" → "Vol. 2 (1)" (a folder has no extension). A
     * leading dot is not an extension separator (".hidden" → ".hidden (1)").
     */
    fun uniqueName(name: String, taken: Set<String>, isDirectory: Boolean): String {
        val lowerTaken = taken.mapTo(HashSet()) { it.lowercase() }
        if (name.lowercase() !in lowerTaken) return name
        val dot = name.lastIndexOf('.')
        val splitExt = !isDirectory && dot > 0 && dot < name.length - 1
        val stem = if (splitExt) name.substring(0, dot) else name
        val ext = if (splitExt) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (candidate.lowercase() !in lowerTaken) return candidate
            n++
        }
    }

    /**
     * Names [entries] (name, isDirectory) take in a trash folder that already holds
     * [trashNames]. An entry keeps its name when the trash doesn't have it; otherwise it
     * gets a " (n)" suffix that is free both in the trash and among [siblingNames] (the
     * entry is renamed in place before moving, next to its siblings).
     */
    fun trashNames(
        entries: List<Pair<String, Boolean>>,
        trashNames: Set<String>,
        siblingNames: Set<String>,
    ): List<String> {
        val inTrash = trashNames.mapTo(HashSet()) { it.lowercase() }
        val used = HashSet<String>()
        return entries.map { (name, isDirectory) ->
            val target = if (name.lowercase() !in inTrash && name.lowercase() !in used) {
                name
            } else {
                uniqueName(name, trashNames + siblingNames + used, isDirectory)
            }
            used += target.lowercase()
            target
        }
    }

    /** True when [a] and [b] name the same entry on a case-insensitive disk. */
    fun sameName(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)
}
