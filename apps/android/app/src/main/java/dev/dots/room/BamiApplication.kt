package dev.dots.room

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider

/** Activity and USB receiver share one reducer/socket even while the display sleeps. */
class BamiApplication:Application(),ViewModelStoreOwner {
 override val viewModelStore=ViewModelStore()
 val model:BamiViewModel get()=ViewModelProvider(viewModelStore,ViewModelProvider.AndroidViewModelFactory.getInstance(this))[BamiViewModel::class.java]
}
