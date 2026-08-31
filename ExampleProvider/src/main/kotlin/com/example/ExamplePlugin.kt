package com.example

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ExamplePlugin: Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity

        // Mevcut sağlayıcılar
        registerMainAPI(ExampleProvider())

        // Yeni dinamik Inat TV sağlayıcısı
        registerMainAPI(DynamicLiveProvider())

        // Şablonun beraberinde getirdiği BlankFragment ayarlar menüsünü koruyoruz
        openSettings = {
            val frag = BlankFragment(this)
            activity?.let {
                frag.show(it.supportFragmentManager, "Frag")
            }
        }
    }
}
