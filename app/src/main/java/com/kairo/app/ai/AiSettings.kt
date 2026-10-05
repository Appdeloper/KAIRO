package com.kairo.app.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** User-controlled AI switch plus where their own Worker lives. No model keys, ever (rule 5). */
data class AiSettings(
    val enabled: Boolean = true,
    val baseUrl: String = "",
    val deviceToken: String = "",
) {
    /** HTTPS only: cleartext is refused by the network security config anyway. */
    val urlIsValid: Boolean get() = baseUrl.trim().toHttpUrlOrNull()?.isHttps == true
    val isSetUp: Boolean get() = urlIsValid && deviceToken.isNotBlank()
    val isReady: Boolean get() = enabled && isSetUp
}
