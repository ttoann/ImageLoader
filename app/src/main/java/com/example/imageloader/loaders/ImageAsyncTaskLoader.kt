package com.example.imageloader.loaders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.loader.content.AsyncTaskLoader
import java.io.IOException
import java.net.URL

class ImageAsyncTaskLoader(context: Context, private val imageUrl: String) : AsyncTaskLoader<Bitmap?>(context) {
    private var cachedResult: Bitmap? = null

    override fun onStartLoading() {
        if (cachedResult != null && !isReset) {
            deliverResult(cachedResult)
        } else {
            forceLoad()
        }
    }

    override fun loadInBackground(): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connect()
            val input = connection.getInputStream()
            cachedResult = BitmapFactory.decodeStream(input)
            cachedResult
        } catch (e: IOException) {
            null
        }
    }

    override fun deliverResult(data: Bitmap?) {
        if (isStarted && !isReset) {
            super.deliverResult(data)
        }
    }

    override fun onReset() {
        super.onReset()
        cachedResult = null
        isReset = true
    }

    private var isReset = false
}