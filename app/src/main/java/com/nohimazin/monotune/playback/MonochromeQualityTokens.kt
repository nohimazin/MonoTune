package com.nohimazin.monotune.playback

import com.nohimazin.monotune.constants.AudioQuality

internal fun AudioQuality.toMonochromeQualityTokens(): List<String> = when (this) {
    AudioQuality.AUTO -> listOf("LOSSLESS", "HIGH", "LOW")
    AudioQuality.LOW -> listOf("LOW")
    AudioQuality.HIGH -> listOf("HIGH", "LOW")
    AudioQuality.LOSSLESS -> listOf("LOSSLESS", "HIGH", "LOW")
    AudioQuality.HI_RES_LOSSLESS -> listOf("HI_RES_LOSSLESS", "LOSSLESS", "HIGH", "LOW")
}
