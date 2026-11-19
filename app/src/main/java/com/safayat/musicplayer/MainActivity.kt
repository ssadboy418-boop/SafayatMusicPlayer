
package com.safayat.musicplayer
import androidx.core.content.ContextCompat

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: android.net.Uri,
    val duration: Long
)

class MainActivity : Activity() {

private lateinit var player: MediaController
private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var root: LinearLayout
    private lateinit var screen: LinearLayout
    private lateinit var mini: LinearLayout

    private val songs = mutableListOf<Song>()
    private val artworkExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val favorites = mutableSetOf<Long>()

    private val purple = Color.rgb(168, 85, 247)
    private val bg = Color.rgb(12, 12, 19)
    private val card = Color.rgb(29, 27, 42)
    private val muted = Color.rgb(165, 160, 180)
    private val white = Color.WHITE

    private var tab = "Home"
    private var currentSong: Song? = null
    private var shuffle = false
    private var repeat = false
    private var filter = ""

    private val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

val token = SessionToken(
    this,
    ComponentName(this, MusicService::class.java)
)

val future = MediaController.Builder(this, token).buildAsync()
controllerFuture = future

future.addListener({
    try {
        player = future.get()

        createLayout()
        checkPermission()

    } catch (e: Exception) {
        Toast.makeText(
            this,
            "Player connection failed: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}, ContextCompat.getMainExecutor(this))
    }

    private fun createLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }

        mini = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(card)
            visibility = View.GONE
        }
val scrollView = ScrollView(this).apply {
    isFillViewport = true
    isVerticalScrollBarEnabled = false
}

scrollView.addView(screen)

root.addView(
    scrollView,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f
    )
)

        root.addView(
            mini,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(70)
            )
        )

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(8), dp(5), dp(8))
            setBackgroundColor(Color.rgb(20, 19, 29))
        }

        val tabs = listOf("Home", "Songs", "Albums", "Artists", "Library")

        tabs.forEach { name ->
            val item = TextView(this).apply {
                text = when (name) {
                    "Home" -> "⌂\nHome"
                    "Songs" -> "♫\nSongs"
                    "Albums" -> "▣\nAlbums"
                    "Artists" -> "♙\nArtists"
                    else -> "♥\nLibrary"
                }
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (tab == name) purple else muted)
                setPadding(dp(5), dp(3), dp(5), dp(3))
                setOnClickListener {
                    tab = name
                    render()
                }
            }

            nav.addView(
                item,
                LinearLayout.LayoutParams(0, dp(52), 1f)
            )
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
            )
        )

        setContentView(root)
        render()
    }

    private fun checkPermission() {
        val audioGranted =
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        val notificationNeeded =
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (!audioGranted) {
            val requested = mutableListOf(permission)

            if (notificationNeeded) {
                requested.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            requestPermissions(requested.toTypedArray(), 100)
        } else {
            loadSongs()

            if (notificationNeeded) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                loadSongs()
            } else {
                render()
            }
        }
    }

    private fun loadSongs() {
        songs.clear()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )
            val albumColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM
            )
            val durationColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                val uri = android.content.ContentUris.withAppendedId(
                    collection,
                    id
                )

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        album = cursor.getString(albumColumn) ?: "Unknown Album",
                        uri = uri,
                        duration = cursor.getLong(durationColumn)
                    )
                )
            }
        }

        render()
    }    private fun render() {
        screen.removeAllViews()

        when (tab) {
            "Home" -> homeScreen()
            "Songs" -> songScreen("Songs")
            "Albums" -> songScreen("Albums")
            "Artists" -> songScreen("Artists")
            "Library" -> favoriteScreen()
        }

        updateMini()
    }

    private fun homeScreen() {
        heading("SAFAYAT MUSIC", "Welcome back")
        txt("Your music, your world.", 14f, muted)
        space(18)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Color.rgb(39, 27, 58), 18)
            setPadding(dp(12), dp(15), dp(12), dp(15))
        }

        stats.addView(stat("♫", songs.size.toString(), "Songs"),
            LinearLayout.LayoutParams(0, dp(70), 1f))
        stats.addView(stat("▣", songs.map { it.album }.distinct().size.toString(), "Albums"),
            LinearLayout.LayoutParams(0, dp(70), 1f))
        stats.addView(stat("♙", songs.map { it.artist }.distinct().size.toString(), "Artists"),
            LinearLayout.LayoutParams(0, dp(70), 1f))
        screen.addView(stats)

        space(22)
        txt("Featured Track", 19f, white, true)
        space(10)

        val featured = songs.firstOrNull()
        if (featured != null) {
            val hero = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
                background = rounded(Color.rgb(72, 39, 105), 20)
                setOnClickListener { playSong(featured) }
            }

            hero.addView(
                artworkView(featured, 112),
                LinearLayout.LayoutParams(dp(112), dp(112))
            )

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, 0, 0)
            }
            details.addView(txt("NOW IN YOUR LIBRARY", 10f, Color.LTGRAY, true))
            details.addView(txt(featured.title, 17f, white, true))
            details.addView(txt(featured.artist, 12f, Color.LTGRAY))
            spaceInside(details, 9)
            details.addView(txt("▶  PLAY NOW", 13f, white, true))

            hero.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            screen.addView(hero)
        } else {
            empty("Your library is empty", "Allow audio permission to find your music.")
        }

        space(22)
        txt("Quick Access", 19f, white, true)
        space(10)

        feature("♫", "All Songs", "Explore your music collection") {
            tab = "Songs"
            render()
        }
        feature("♥", "Favorite Songs", "Your favorite tracks") {
            tab = "Library"
            render()
        }
        feature("▣", "Albums", "Browse your albums") {
            tab = "Albums"
            render()
        }

        space(18)
        txt("Recently Added", 19f, white, true)
        space(10)

        if (songs.isNotEmpty()) {
            songs.takeLast(6).reversed().forEach { songRow(it) }
        }
    }

    private fun songScreen(type: String) {
        heading("MY LIBRARY", type)

        txt(
            when (type) {
                "Albums" -> "Browse your albums"
                "Artists" -> "Explore your artists"
                else -> "All your music in one place"
            },
            13f,
            muted
        )

        space(14)

        val search = EditText(this).apply {
            hint = when (type) {
                "Albums" -> "Search albums..."
                "Artists" -> "Search artists..."
                else -> "Search songs or artists..."
            }
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(card, 14)
        }

        screen.addView(
            search,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        space(12)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        screen.addView(
            list,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun showItems(query: String) {
            list.removeAllViews()

            val filtered = when (type) {
                "Albums" -> songs
                    .filter { it.album.contains(query, true) }
                    .distinctBy { it.album }

                "Artists" -> songs
                    .filter { it.artist.contains(query, true) }
                    .distinctBy { it.artist }

                else -> songs.filter {
                    it.title.contains(query, true) ||
                    it.artist.contains(query, true) ||
                    it.album.contains(query, true)
                }
            }

            if (filtered.isEmpty()) {
                val message = if (songs.isEmpty()) {
                    "No music found. Scan your device or allow permission."
                } else {
                    "Nothing found"
                }

                list.addView(
                    txt(message, 14f, muted)
                )
                return
            }

            filtered.forEach { song ->
                if (type == "Albums") {
                    feature(
                        "▣",
                        song.album,
                        song.artist
                    ) {
                        tab = "Songs"
                        render()
                    }.also { list.addView(it) }
                } else if (type == "Artists") {
                    feature(
                        "♙",
                        song.artist,
                        "Tap to browse artist"
                    ) {
                        tab = "Songs"
                        render()
                    }.also { list.addView(it) }
                } else {
                    list.addView(songRow(song))
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                showItems(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        showItems("")
    }

    private fun favoriteScreen() {
        heading("YOUR COLLECTION", "Favorites")

        txt(
            "Songs you have marked as favorite",
            13f,
            muted
        )

        space(15)

        val favoriteSongs = songs.filter {
            favorites.contains(it.id)
        }

        if (favoriteSongs.isEmpty()) {
            empty(
                "No favorites yet",
                "Tap the heart on a song to add it here."
            )
        } else {
            favoriteSongs.forEach {
                songRow(it)
            }
        }
    }    private fun artworkView(song: Song, size: Int): ImageView {
        val image = ImageView(this)
        val key = song.uri.toString()

        image.tag = key
        image.setImageResource(android.R.drawable.ic_media_play)
        image.scaleType = ImageView.ScaleType.CENTER_INSIDE
        image.background = rounded(Color.rgb(75, 43, 105), 12)
        image.clipToOutline = true
        image.contentDescription = "${song.album} artwork"

        artworkExecutor.execute {
            val bitmap = try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, song.uri)
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) {
                        android.graphics.BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size
                        )
                    } else null
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                runOnUiThread {
                    if (image.tag == key) {
                        image.setImageBitmap(bitmap)
                        image.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            }
        }

        return image
    }

    private fun songRow(song: Song): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(8), dp(9))
            background = rounded(card, 14)
        }

        val artwork = artworkView(song, 48)

        row.addView(
            artwork,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(5), 0)
        }

        info.addView(
            txt(song.title, 14f, white, true)
        )

        info.addView(
            txt(song.artist, 11f, muted)
        )

        row.addView(
            info,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val heart = TextView(this).apply {
            text = if (favorites.contains(song.id)) "♥" else "♡"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(
                if (favorites.contains(song.id)) purple else muted
            )

            setOnClickListener {
                if (favorites.contains(song.id)) {
                    favorites.remove(song.id)
                } else {
                    favorites.add(song.id)
                }
                render()
            }
        }

        row.addView(
            heart,
            LinearLayout.LayoutParams(dp(38), dp(48))
        )

        val duration = txt(
            time(song.duration),
            10f,
            muted
        )

        row.addView(duration)

        row.setOnClickListener {
            playSong(song)
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        wrapper.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        spaceInside(wrapper, 7)

        return wrapper
    }

    private fun playSong(song: Song) {
        currentSong = song

        val index = songs.indexOf(song)

        if (index >= 0) {
            player.setMediaItems(
                songs.map {
                    MediaItem.Builder()
                        .setUri(it.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(it.title)
                                .setArtist(it.artist)
                                .setAlbumTitle(it.album)
                                .build()
                        )
                        .build()
                },
                index,
                0
            )
        } else {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            )
        }

        player.prepare()
        player.play()

        showNowPlaying()
        updateMini()
    }

    private fun showNowPlaying() {
        val song = currentSong ?: return

        screen.removeAllViews()

        val back = txt("‹   BACK", 15f, purple, true)
        back.setOnClickListener {
            render()
        }
        screen.addView(back)

        space(25)

        val artwork = TextView(this).apply {
            text = "♫"
            textSize = 100f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(
                Color.rgb(93, 49, 145),
                28
            )
        }

        screen.addView(
            artwork,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230)
            )
        )

        space(25)

        txt(
            song.title,
            23f,
            white,
            true
        ).apply {
            gravity = Gravity.CENTER
        }.also { screen.addView(it) }

        txt(
            song.artist,
            14f,
            muted
        ).apply {
            gravity = Gravity.CENTER
        }.also { screen.addView(it) }

        space(20)

        val seek = SeekBar(this).apply {
            max = 1000
            progressTintList =
                android.content.res.ColorStateList.valueOf(purple)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(white)
        }

        screen.addView(seek)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val currentTime = txt("0:00", 11f, muted)
        val totalTime = txt(time(song.duration), 11f, muted)

        timeRow.addView(
            currentTime,
            LinearLayout.LayoutParams(0, dp(25), 1f)
        )

        timeRow.addView(totalTime)

        screen.addView(timeRow)

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        val duration = player.duration
                        if (duration > 0) {
                            player.seekTo(
                                duration * progress / 1000
                            )
                        }
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}

                override fun onStopTrackingTouch(bar: SeekBar?) {}
            }
        )

val progressHandler = android.os.Handler(
    android.os.Looper.getMainLooper()
)

val progressUpdater = object : Runnable {
    override fun run() {
        val duration = player.duration
        val position = player.currentPosition

        if (duration > 0) {
            seek.progress =
                ((position * 1000) / duration).toInt()

            currentTime.text = time(position)
            totalTime.text = time(duration)
        }

        if (player.isPlaying) {
            progressHandler.postDelayed(this, 500)
        }
    }
}

player.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playButton.text = if (isPlaying) "Ⅱ" else "▶"

        progressHandler.removeCallbacks(progressUpdater)

        if (isPlaying) {
            progressHandler.post(progressUpdater)
        } else {
            currentTime.text = time(player.currentPosition)
        }
    }
})
        space(20)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val shuffleButton = button("⇄")
        val previousButton = button("|◀")
        playButton = button(if (player.isPlaying) "Ⅱ" else "▶")
        val nextButton = button("▶|")
        val repeatButton = button("↻")

        shuffleButton.setOnClickListener {
            shuffle = !shuffle
            player.shuffleModeEnabled = shuffle
            shuffleButton.setTextColor(
                if (shuffle) purple else white
            )
        }

        previousButton.setOnClickListener {
            player.seekToPreviousMediaItem()
        }

        playButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            playButton.text = if (player.isPlaying) "Ⅱ" else "▶"
        }

        nextButton.setOnClickListener {
            player.seekToNextMediaItem()
        }

        repeatButton.setOnClickListener {
            repeat = !repeat
            player.repeatMode =
                if (repeat) Player.REPEAT_MODE_ONE
                else Player.REPEAT_MODE_OFF

            repeatButton.setTextColor(
                if (repeat) purple else white
            )
        }

        listOf(
            shuffleButton,
            previousButton,
            playButton,
            nextButton,
            repeatButton
        ).forEach { control ->
            controls.addView(
                control,
                LinearLayout.LayoutParams(0, dp(55), 1f)
            )
        }

        screen.addView(controls)

        space(18)

        val openQueue = txt(
            "PLAYING FROM YOUR MUSIC LIBRARY",
            11f,
            muted
        )

        openQueue.gravity = Gravity.CENTER
        screen.addView(openQueue)
    }

    private lateinit var playButton: TextView

    private fun updateMini() {
        mini.removeAllViews()

        val song = currentSong ?: run {
            mini.visibility = View.GONE
            return
        }

        mini.visibility = View.VISIBLE

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        info.addView(
            txt(song.title, 13f, white, true)
        )

        info.addView(
            txt(song.artist, 10f, muted)
        )

        row.addView(
            info,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val play = button(if (player.isPlaying) "Ⅱ" else "▶")

        play.setOnClickListener {
            if (player.isPlaying) player.pause()
            else player.play()

            updateMini()
        }

        val next = button("▶|")

        next.setOnClickListener {
            player.seekToNextMediaItem()
        }

        row.addView(play)
        row.addView(next)

        mini.addView(row)

        mini.setOnClickListener {
            showNowPlaying()
        }
    }    private fun stat(icon: String, value: String, label: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        box.addView(txt(icon, 20f, purple, true))
        box.addView(txt(value, 19f, white, true))
        box.addView(txt(label, 10f, muted))

        return box
    }

    private fun heading(small: String, big: String) {
        txt(small, 11f, purple, true)
        space(5)
        txt(big, 25f, white, true)
        space(12)
    }

    private fun feature(
        icon: String,
        title: String,
        subtitle: String,
        action: () -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(card, 15)
            setOnClickListener { action() }
        }

        val symbol = TextView(this).apply {
            text = icon
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(purple)
        }

        box.addView(
            symbol,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        info.addView(txt(title, 14f, white, true))
        info.addView(txt(subtitle, 11f, muted))

        box.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        box.addView(txt("›", 25f, purple))

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        wrapper.addView(box)
        spaceInside(wrapper, 8)

        return wrapper
    }

    private fun empty(title: String, subtitle: String) {
        space(35)

        val message = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(20), dp(15), dp(20))
        }

        message.addView(txt("♫", 45f, purple, true))

        message.addView(
            txt(title, 18f, white, true).apply {
                gravity = Gravity.CENTER
            }
        )

        message.addView(
            txt(subtitle, 12f, muted).apply {
                gravity = Gravity.CENTER
            }
        )

        screen.addView(
            message,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                -2
            )
        )
    }

    private fun button(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(card, 50)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
    }

    private fun txt(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun space(value: Int) {
        screen.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(value))
        )
    }

    private fun spaceInside(
        parent: LinearLayout,
        value: Int
    ) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(value))
        )
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun time(milliseconds: Long): String {
        if (milliseconds <= 0) return "0:00"

        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    override fun onDestroy() {
        artworkExecutor.shutdownNow()
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
