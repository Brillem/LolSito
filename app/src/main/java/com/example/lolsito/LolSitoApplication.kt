package com.example.lolsito

import android.app.Application
import com.example.lolsito.core.di.AppContainer

class LolSitoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
