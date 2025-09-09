package com.example.testapp.core

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FrameBus {
    private val _latest = MutableStateFlow<Bitmap?>(null)
    val latest = _latest.asStateFlow()

    fun update(bmp: Bitmap) { _latest.value = bmp }
}
