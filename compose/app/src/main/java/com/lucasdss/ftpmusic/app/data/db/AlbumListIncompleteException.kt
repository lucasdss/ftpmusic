package com.lucasdss.ftpmusic.app.data.db

/** Thrown when getAlbumList2 fails mid-pagination (ADR-0068 fail-closed). */
internal class AlbumListIncompleteException : IllegalStateException("Album list incomplete — aborting sync")
