package com.example.imageloader.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL

class ImageLoadAsyncTask(
    context: Context,
    private val onComplete: (Bitmap?) -> Unit
) : AsyncTask<String, Void, Bitmap?>() {
    private val contextRef = WeakReference(context)

    override fun doInBackground(vararg params: String): Bitmap? {
        if (params.isEmpty()) return null

        val urlString = params[0]
        return try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connect()
            val input = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            null
        }
    }

    override fun onPostExecute(result: Bitmap?) {
        if (contextRef.get() != null && !isCancelled) {
            onComplete(result)
        }
    }
}