package com.source.player.data.smb

/** Connection settings for one SMB/CIFS share (Freebox Server, any NAS). */
data class SmbConfig(
        val host: String, // IP or hostname, e.g. "192.168.1.254" or "mafreebox.freebox.fr"
        val share: String, // e.g. "Freebox"
        val path: String = "", // optional sub-folder inside the share, e.g. "Musique"
        val username: String = "", // empty = guest/anonymous
        val password: String = "",
        val displayName: String = "Freebox",
) {
        /**
         * `smb://host/share/path/` — always ends with '/'. Leading/trailing/duplicate
         * slashes in [share] and [path] are tolerated. Segments are NOT percent-encoded:
         * jcifs-ng 2.1 takes SMB URL paths verbatim (no %-decoding), and raw spaces,
         * accents, '#', '?' and '%' all round-trip (verified against 2.1.10).
         */
        val rootUrl: String
                get() = buildString {
                        append(SCHEME)
                        append(host.trim().trim('/'))
                        append('/')
                        for (segment in segments(share) + segments(path)) {
                                append(segment)
                                append('/')
                        }
                }

        val isGuest: Boolean
                get() = username.isBlank()

        /** Password-free representation, safe for logs. */
        override fun toString(): String =
                "SmbConfig(host=$host, share=$share, path=$path, user=${username.ifBlank { "<guest>" }}, " +
                        "displayName=$displayName)"

        companion object {
                const val SCHEME = "smb://"

                fun isSmbUrl(s: String?) = s?.startsWith(SCHEME, ignoreCase = true) == true

                private fun segments(s: String): List<String> =
                        s.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
        }
}
