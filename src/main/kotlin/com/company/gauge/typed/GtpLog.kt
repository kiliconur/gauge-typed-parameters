package com.company.gauge.typed

import com.intellij.openapi.diagnostic.Logger

/**
 * Single logging entry point for runtime diagnostics.
 *
 * Every line is written at INFO with a `[GTP]` prefix so the whole completion pipeline can be
 * followed in `idea.log` with one grep:
 *
 * ```
 * findstr /C:"[GTP]" idea.log
 * ```
 */
internal object GtpLog {

    private val LOG = Logger.getInstance("com.company.gauge.typed.GTP")

    fun info(message: String) = LOG.info("[GTP] $message")

    fun warn(message: String, t: Throwable? = null) {
        if (t == null) LOG.warn("[GTP] $message") else LOG.warn("[GTP] $message", t)
    }
}
