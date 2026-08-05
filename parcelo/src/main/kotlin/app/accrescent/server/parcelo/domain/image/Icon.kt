// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.image

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.io.IOException
import java.nio.file.Path
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.io.path.inputStream

private const val PNG_FORMAT_NAME = "PNG"
private const val REQUIRED_WIDTH = 512
private const val REQUIRED_HEIGHT = 512

/**
 * A parsed app icon.
 *
 * An icon is a PNG image with 512x512 pixel dimensions.
 */
class Icon private constructor() {
    companion object {
        /**
         * Parses an icon from a file on the local filesystem.
         *
         * @param path the path to read the icon from.
         */
        fun parse(path: Path): Either<IconParseError, Icon> = either {
            val reader = ImageIO
                .getImageReadersByFormatName(PNG_FORMAT_NAME)
                .asSequence()
                .firstOrNull()
                ?: raise(IconParseError.Io)

            try {
                path.inputStream().use { fileStream ->
                    val imageStream = ImageIO.createImageInputStream(fileStream)
                        ?: raise(IconParseError.Io)

                    imageStream.use {
                        reader.input = it

                        val width = reader.getWidth(0)
                        val height = reader.getHeight(0)
                        ensure(width == REQUIRED_WIDTH && height == REQUIRED_HEIGHT) {
                            IconParseError.IncorrectImageDimensions
                        }

                        reader.read(0)

                        Icon()
                    }
                }
            } catch (_: IIOException) {
                raise(IconParseError.InvalidImage)
            } catch (_: IOException) {
                raise(IconParseError.Io)
            } finally {
                reader.dispose()
            }
        }
    }
}

/**
 * An error that occurred while attempting to parse an icon.
 */
sealed class IconParseError {
    /**
     * The file is not a valid PNG image.
     */
    data object InvalidImage : IconParseError()

    /**
     * The file is a valid PNG image, but it does not have the required dimensions.
     */
    data object IncorrectImageDimensions : IconParseError()

    /**
     * An I/O error occurred while parsing the icon.
     */
    data object Io : IconParseError()
}
