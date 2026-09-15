package com.aistudio.missioncontrol.pxytwe.webrtc.signaling

class SignalingMessageGuard {
    var currentGeneration = -1L
        private set

    private var lastProcessedOfferGeneration = -1L
    private var lastProcessedAnswerGeneration = -1L
    private val processedIceCandidates = mutableSetOf<String>()

    fun startNewGeneration(generation: Long) {
        currentGeneration = generation
        processedIceCandidates.clear()
    }

    fun shouldAcceptReady(payloadGeneration: Long): Boolean {
        return payloadGeneration > currentGeneration
    }

    fun shouldAcceptOffer(payloadGeneration: Long): Boolean {
        if (payloadGeneration < currentGeneration) return false
        if (payloadGeneration == currentGeneration && lastProcessedOfferGeneration == currentGeneration) return false
        lastProcessedOfferGeneration = payloadGeneration
        return true
    }

    fun shouldAcceptAnswer(payloadGeneration: Long): Boolean {
        if (payloadGeneration < currentGeneration) return false
        if (payloadGeneration == currentGeneration && lastProcessedAnswerGeneration == currentGeneration) return false
        lastProcessedAnswerGeneration = payloadGeneration
        return true
    }

    fun shouldAcceptIceCandidate(payloadGeneration: Long, candidateId: String): Boolean {
        if (payloadGeneration < currentGeneration) return false
        if (processedIceCandidates.contains(candidateId)) return false
        processedIceCandidates.add(candidateId)
        return true
    }
}
