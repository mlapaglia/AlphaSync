package com.alphasync

class MyCameraLinkEventListener {
    var onGpsSignalLost: (() -> Unit)? = null
    var onGpsSignalFound: (() -> Unit)? = null
    var onLocationReady: ((ByteArray) -> Unit)? = null
}