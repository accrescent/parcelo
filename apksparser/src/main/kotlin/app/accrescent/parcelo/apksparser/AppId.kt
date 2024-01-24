// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.apksparser

/**
 * A well-formed Android application ID. Its [value] property is guaranteed to contain a valid
 * Android app ID.
 */
public class AppId private constructor(public val value: String) {
    public companion object {
        /**
         * Parses the given string into an Android application ID according to
         * https://developer.android.com/studio/build/configure-app-module. Specifically, it
         * verifies:
         *
         * 1. The string contains two segments (one or more dots).
         * 2. Each segment starts with a letter.
         * 3. All characters are alphanumeric or an underscore.
         *
         * If any of these conditions are not met, this function returns null.
         */
        public fun parseFromString(s: String): AppId? {
            val segments = s.split(".")
            if (segments.size < 2) {
                return null
            }

            for (segment in segments) {
                when {
                    segment.isEmpty() -> return null
                    !segment[0].isLetter() -> return null
                    !alphanumericUnderscoreRegex.matches(segment) -> return null
                }
            }

            return AppId(s)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is AppId && this.value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

private val alphanumericUnderscoreRegex = Regex("""^[a-zA-Z0-9_]+$""")
