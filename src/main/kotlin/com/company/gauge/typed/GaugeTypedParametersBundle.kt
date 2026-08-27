package com.company.gauge.typed

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.GaugeTypedParametersBundle"

object GaugeTypedParametersBundle {

    private val INSTANCE = DynamicBundle(GaugeTypedParametersBundle::class.java, BUNDLE)

    @Nls
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String = INSTANCE.getMessage(key, *params)
}
