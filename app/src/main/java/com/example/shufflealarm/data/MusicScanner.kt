package com.example.shufflealarm.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class MusicScanner(private val context: Context) {

    fun scanTree(treeUri: Uri): List<Track> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<Track>()
        walk(root, results)
        // Deduplicate by uri
        return results.distinctBy { it.uri }
    }

    private fun walk(dir: DocumentFile, out: MutableList<Track>) {
        val files = dir.listFiles()
        for (f in files) {
            if (f.isDirectory) {
                walk(f, out)
            } else {
                if (isAudio(f)) {
                    val uri = f.uri.toString()
                    val name = f.name ?: ""
                    out.add(Track(uri = uri, name = name))
                }
            }
        }
    }

    private fun isAudio(file: DocumentFile): Boolean {
        val type = file.type ?: ""
        if (type.startsWith("audio/")) return true
        val name = (file.name ?: "").lowercase()
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") ||
                name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".flac")
    }
}
