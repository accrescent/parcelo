// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.appstore

import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.core.unwrap
import arrow.core.None
import arrow.core.Option
import arrow.core.Some

/**
 * An app store listing's source text language.
 */
enum class ListingLanguage {
    /**
     * American English.
     */
    EN_US;

    companion object {
        /**
         * Creates a listing language from its [BCP-47](https://www.rfc-editor.org/info/bcp47/) tag.
         *
         * @param tag the BCP-47 language tag to create a listing language from.
         * @return the listing language associated with the provided BCP-47 language tag, or [None]
         * if the language tag doesn't represent a valid listing language.
         */
        fun fromLanguageTag(tag: UString): Option<ListingLanguage> {
            return when (tag.value) {
                "en-US" -> Some(EN_US)
                else -> None
            }
        }
    }

    /**
     * Returns the [BCP-47](https://www.rfc-editor.org/info/bcp47/) language tag of this listing
     * language.
     *
     * @return the BCP-47 language tag of this listing language.
     */
    fun languageTag(): UString {
        val str = when (this) {
            EN_US -> "en-US"
        }

        return UString.fromString(str).unwrap()
    }
}
