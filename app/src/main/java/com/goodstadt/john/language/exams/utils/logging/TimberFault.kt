package com.goodstadt.john.language.exams.utils.logging

import android.util.Log
import timber.log.Timber

/**
 * A simple logging class to mimic Timber for our extension function experiment.
 * It's a singleton created using the 'object' keyword.
 */
object TimberFault {

    private const val FAULT_TAG = "FAULT"

    /**
     * Logs a message with 'FAULT' priority.
     */
    fun f(message: String, vararg args: Any?) {
        Timber.tag(FAULT_TAG).wtf(message, *args)
    }

    /**
     * An overload for logging a fault with an associated exception/throwable.
     */
    fun f(t: Throwable, message: String, vararg args: Any?) {
        Timber.tag(FAULT_TAG).wtf(t, message, *args)
    }

    fun f(
        message: String,
        secondaryText: String = "",
        area: String = ""
    ) {
        // 1. Encode the extra data into a JSON string tag
        val tag = FaultDataEncoder.encode(secondaryText, area)

        // 2. Call wtf() with the custom tag
        Timber.tag(tag).wtf(message)
    }
    fun f(
        message: String,
        localizedMessage:String = "",
        secondaryText: String = "",
        area: String = ""
    ) {
        // 1. Encode the extra data into a JSON string tag
        val tag = FaultDataEncoder.encode(secondaryText, area)

        // 2. Call wtf() with the custom tag
        Timber.tag(tag).wtf(message)
    }
    fun f(
        t: Throwable,
        message: String,
        secondaryText: String = "",
        area: String = ""
    ) {
        val tag = FaultDataEncoder.encode(secondaryText, area)
        Timber.tag(tag).wtf(t, message)
    }
}