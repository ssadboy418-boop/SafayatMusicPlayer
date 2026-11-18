package com.safayat.musicplayer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var songList: LinearLayout
    private lateinit var statusText: TextView

    private val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player = ExoPlayer.Builder(this).build()

        songList = findViewById(R.id.songList)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.scanButton)
            .setOnClickListener {
                checkPermission()
            }

        checkPermission()
    }

    private fun checkPermission() {
        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            statusText.text = "Audio permission required."
        }
    }

    private fun loadSongs() {

        songList.removeAllViews()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        var count = 0

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleColumn = it.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistColumn = it.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            while (it.moveToNext()) {

                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"

                val contentUri =
                    android.content.ContentUris.withAppendedId(uri, id)

                val item = TextView(this).apply {
                    text = "♫  $title\n     $artist"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setPadding(18, 22, 18, 22)
                    setBackgroundColor(Color.rgb(35, 35, 48))

                    setOnClickListener {
                        player.setMediaItem(MediaItem.fromUri(contentUri))
                        player.prepare()
                        player.play()

                        Toast.makeText(
                            this@MainActivity,
                            "Playing: $title",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                params.setMargins(0, 8, 0, 8)
                songList.addView(item, params)

                count++
            }
        }

        statusText.text = if (count > 0)
            "$count songs found. Tap a song to play."
        else
            "No music found on your device."
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
