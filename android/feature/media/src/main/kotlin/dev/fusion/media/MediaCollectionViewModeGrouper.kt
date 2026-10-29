// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * **Media Hub `LIST` / `GRID` / `COLUMNS` 3-Mode Collection Organizer** backing our original
 * `List`, `Grid`, and `Columns` vector logos inside `MediaHub.kt` (`Artist -> Album -> Track`
 * Miller columns, 2D album/video card mosaic, and sortable track table)!
 */
object MediaCollectionViewModeGrouper {
    const val SCOPE =
        "Media Hub LIST (track ledger), GRID (album/video mosaic), and COLUMNS (Artist -> Album -> Track) collection projection."

    data class MediaItemRecord(
        val id: String,
        val title: String,
        val artistOrOrigin: String,
        val albumOrFolder: String,
        val durationMillis: Long,
    )

    data class ColumnsProjection(
        val artists: List<String>,
        val selectedArtist: String,
        val albums: List<String>,
        val selectedAlbum: String,
        val tracks: List<MediaItemRecord>,
    )

    fun projectColumns(items: List<MediaItemRecord>, artistFilter: String?, albumFilter: String?): ColumnsProjection {
        if (items.isEmpty()) return ColumnsProjection(emptyList(), "", emptyList(), "", emptyList())
        val artists = items.map { it.artistOrOrigin }.distinct().sorted()
        val selArtist = artistFilter?.takeIf { it in artists } ?: artists.first()
        val byArtist = items.filter { it.artistOrOrigin == selArtist }
        val albums = byArtist.map { it.albumOrFolder }.distinct().sorted()
        val selAlbum = albumFilter?.takeIf { it in albums } ?: albums.first()
        val tracks = byArtist.filter { it.albumOrFolder == selAlbum }.sortedBy { it.title }
        return ColumnsProjection(artists, selArtist, albums, selAlbum, tracks)
    }
}
