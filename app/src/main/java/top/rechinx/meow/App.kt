package top.rechinx.meow

import android.app.Application
import androidx.room.Room
import android.content.Context
import android.os.Environment
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import okhttp3.OkHttpClient
import org.koin.android.ext.android.startKoin
import top.rechinx.meow.di.AppComponent

class App: Application() {

    private var basePath: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Stetho.initializeWithDefaults(this)

        // for koin
        startKoin(this, AppComponent.modules())

    }

    fun getBasePath(): String {
        if (basePath == null) {
            basePath = getExternalFilesDir(null).absolutePath
            //basePath = Environment.getExternalStorageDirectory().absolutePath + "/Meow/"
        }
        return basePath!!
    }


    companion object {

        lateinit var instance: App

    }
}