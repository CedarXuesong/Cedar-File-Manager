package com.openxida.cedarmanager.nativebridge

object NativeBridge {
    init {
        System.loadLibrary("cedar_native")
    }

    external fun architecture(): String
}
