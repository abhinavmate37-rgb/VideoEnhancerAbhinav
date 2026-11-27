package com.abhinav.videoenhancer

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.transformer.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var enhanceBtn: Button
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(48, 48, 48, 48)

        val pickBtn = Button(this)
        pickBtn.text = "Pick Video"
        pickBtn.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "video/*"
            startActivityForResult(i, 1)
        }

        enhanceBtn = Button(this)
        enhanceBtn.text = "Enhance to 4K"
        enhanceBtn.isEnabled = false
        enhanceBtn.setOnClickListener { enhance() }

        status = TextView(this)
        status.text = "Pick a video to start"
        status.gravity = Gravity.CENTER

        layout.addView(pickBtn)
        layout.addView(enhanceBtn)
        layout.addView(status)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            videoUri = data?.data
            enhanceBtn.isEnabled = videoUri != null
            status.text = "Video selected. Tap Enhance to 4K"
        }
    }

    private fun enhance() {
        val uri = videoUri ?: return
        enhanceBtn.isEnabled = false
        status.text = "Enhancing... please wait"

        val r = MediaMetadataRetriever()
        r.setDataSource(this, uri)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
        r.release()
        val dispW = if (rot == 90 || rot == 270) h else w
        val dispH = if (rot == 90 || rot == 270) w else h
        val targetHeight = if (dispH > dispW) 3840 else 2160

        val outFile = File(cacheDir, "enhanced.mp4")
        if (outFile.exists()) outFile.delete()

        val encoderFactory = DefaultEncoderFactory.Builder(this)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(50_000_000).build()
            )
            .build()

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(listOf(), listOf(Presentation.createForHeight(targetHeight))))
            .build()

        val transformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    saveToGallery(outFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    status.text = "Error: " + exportException.message
                    enhanceBtn.isEnabled = true
                }
            })
            .build()

        transformer.start(edited, outFile.absolutePath)
    }

    private fun saveToGallery(file: File) {
        if (Build.VERSION.SDK_INT < 29) {
            status.text = "Done, but saving needs Android 10 or newer"
            enhanceBtn.isEnabled = true
            return
        }
        val values = ContentValues()
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "enhanced_" + System.currentTimeMillis() + ".mp4")
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoEnhancer")
        val dest = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (dest != null) {
            contentResolver.openOutputStream(dest)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            status.text = "Done! Saved in Movies/VideoEnhancer"
        } else {
            status.text = "Could not save the video"
        }
        enhanceBtn.isEnabled = true
    }
}
