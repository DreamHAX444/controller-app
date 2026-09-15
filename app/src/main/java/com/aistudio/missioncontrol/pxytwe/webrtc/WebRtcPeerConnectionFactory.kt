package com.aistudio.missioncontrol.pxytwe.webrtc

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

object WebRtcPeerConnectionFactory {
    private var isInitialized = false
    private var factory: PeerConnectionFactory? = null
    val eglBase: EglBase by lazy { EglBase.create() }

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        isInitialized = true
    }

    @Synchronized
    fun getFactory(): PeerConnectionFactory {
        if (factory != null) return factory!!
        
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(
            eglBase.eglBaseContext
        )

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        
        return factory!!
    }
}
