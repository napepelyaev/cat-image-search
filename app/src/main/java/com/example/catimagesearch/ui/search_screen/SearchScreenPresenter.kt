package com.example.catimagesearch.ui.search_screen

import android.R.attr.label
import android.content.*
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import com.example.catimagesearch.IRetrofitServices
import com.example.catimagesearch.R
import com.example.catimagesearch.data.database.SavedQueryDao
import com.example.catimagesearch.data.entity.SavedQueryModel
import com.example.catimagesearch.data.google_responce.Item
import com.example.catimagesearch.data.google_responce.ResponseModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.Executors


class SearchScreenPresenter (
    private var api: IRetrofitServices,
    private var dao: SavedQueryDao,
    private var context: Context,
): Callback<ResponseModel> {

    //гугловская штука
    companion object{
        private const val API_KEY = "AIzaSyAq9xyzCZlcQFmCgKm633EZp1AFbmYYWfQ"
        private const val CX = "8b8faaa29e4af41f0"
    }

    private lateinit var view: SearchScreen
    private val coroutineIO = CoroutineScope(Dispatchers.Main)


    fun onCreate(view: SearchScreen) {
        this.view = view
    }
    fun search(query: String) {
        view.showLoader()
        api.getData(API_KEY, CX, query, "image").enqueue(this)
    }


    private fun updateResponseList(items: List<Item?>){
        view.updateList(items)
    }

    override fun onResponse(call: Call<ResponseModel>, response: Response<ResponseModel>) {
        view.hideLoader()

        if (response.isSuccessful){
            response.body()?.items?.let { updateResponseList(it) }
            saveQuery(response.body()?.queries?.request?.first()?.searchTerms.toString())
        } else {
            view.showMessage("Код ошибки: "+response.code().toString())
        }
    }

    override fun onFailure(call: Call<ResponseModel>, t: Throwable) {
        view.hideLoader()
        view.showMessage(t.localizedMessage)
    }

    private fun saveQuery(query: String) {
        coroutineIO.launch {
            dao.insert(SavedQueryModel(query = query, date = Date().toString()))
        }
    }

    fun inputTextFocus(){
        coroutineIO.launch {
            view.showSavedQueries(dao.getStringQuery())
        }
    }



    private fun clickedDownloadButton(link: String) {
        val myExecutor = Executors.newSingleThreadExecutor()
        val myHandler = Handler(Looper.getMainLooper())

        // When Button is clicked, executor will
        // fetch the image and handler will display it.
        // Once displayed, it is stored locally

            myExecutor.execute {
                val mImage = mLoad(link)
                myHandler.post {
                    if(mImage!=null){
                        mSaveMediaToStorage(mImage)
                    }
                }

        }
    //        val job = GlobalScope.launch {
//            val mImage = mLoad(link)
//
//                if(mImage!=null){
//                    mSaveMediaToStorage(mImage)
//                }
//
//        }

//        view.showToast("clicked download, link: $link")
    }

    private fun clickedCopyButton(link: String) {
        val clipboard: ClipboardManager? =
            getSystemService(
                context,
                ClipboardManager::class.java)

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(label.toString(), link))

            view.showToast("Ссылка скопирована в буфер обмена")
        }
        else {
            view.showToast("Ошибка при копировании в буфер обмена")
        }

    }

    private fun clickedShareButton(link: String) {
        view.clickedShareButton(link)
    }

    fun clickedButton(link: String, type: String) {
        when(type) {
            SearchScreen.DOWNLOAD -> clickedDownloadButton(link)
            SearchScreen.SHARE -> clickedShareButton(link)
            SearchScreen.COPY -> clickedCopyButton(link)
            else -> view.showToast("Ошибка, link: $link")

        }
    }

    // Function to establish connection and load image
    private fun mLoad(string: String): Bitmap? {
        val url: URL = mStringToURL(string)!!
        val connection: HttpURLConnection?
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.connect()
            val inputStream: InputStream = connection.inputStream
            val bufferedInputStream = BufferedInputStream(inputStream)
            return BitmapFactory.decodeStream(bufferedInputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            view.showToast( "Error", Toast.LENGTH_LONG)
        }
        return null
    }

    // Function to convert string to URL
    private fun mStringToURL(string: String): URL? {
        try {
            return URL(string)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }
        return null
    }

    // Function to save image on the device.
    // Refer: https://www.geeksforgeeks.org/circular-crop-an-image-and-save-it-to-the-file-in-android/
    private fun mSaveMediaToStorage(bitmap: Bitmap?) {
        val filename = "${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
            context.contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }

        fos?.use {
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, it)
            view.showToast("Загружено")
        }
    }
}