package com.example.testapp.core

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object FrameBus {
    private val _latest = MutableStateFlow<Bitmap?>(null)
    val latest: StateFlow<Bitmap?> = _latest
    fun update(bmp: Bitmap) { _latest.value = bmp }
}
