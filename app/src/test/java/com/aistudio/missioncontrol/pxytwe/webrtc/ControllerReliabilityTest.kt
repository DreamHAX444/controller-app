package com.aistudio.missioncontrol.pxytwe.webrtc

import org.junit.Assert.*
import org.junit.Test
import com.aistudio.missioncontrol.pxytwe.webrtc.signaling.SignalingMessageGuard

class ControllerReliabilityTest {

    @Test
    fun testReady_duplicateIgnored() {
        val guard = SignalingMessageGuard()
        guard.startNewGeneration(2L)
        assertFalse(guard.shouldAcceptReady(2L))
    }

    @Test
    fun testReady_staleIgnored() {
        val guard = SignalingMessageGuard()
        guard.startNewGeneration(2L)
        assertFalse(guard.shouldAcceptReady(1L))
    }

    @Test
    fun testOffer_duplicateRejected() {
        val guard = SignalingMessageGuard()
        guard.startNewGeneration(1L)
        assertTrue(guard.shouldAcceptOffer(1L))
        assertFalse(guard.shouldAcceptOffer(1L))
    }

    @Test
    fun testOffer_staleRejected() {
        val guard = SignalingMessageGuard()
        guard.startNewGeneration(2L)
        assertFalse(guard.shouldAcceptOffer(1L))
    }

    @Test
    fun testIce_duplicateRejected() {
        val guard = SignalingMessageGuard()
        guard.startNewGeneration(1L)
        assertTrue(guard.shouldAcceptIceCandidate(1L, "candidate1"))
        assertFalse(guard.shouldAcceptIceCandidate(1L, "candidate1"))
    }
}
