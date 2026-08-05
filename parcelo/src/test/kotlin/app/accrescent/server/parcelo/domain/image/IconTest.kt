// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.image

import app.accrescent.server.parcelo.core.unwrapErr
import arrow.core.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.io.path.createTempFile
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class IconTest {
    @Test
    fun `parse returns an icon for a valid PNG`(@TempDir tempDir: Path) {
        val path = pngPath(tempDir, REQUIRED_WIDTH, REQUIRED_HEIGHT)

        val result = Icon.parse(path)

        assertInstanceOf<Either.Right<Icon>>(result)
    }

    @Test
    fun `parse returns InvalidImage for a file which is not a PNG`(@TempDir tempDir: Path) {
        val path = createTempFile(tempDir)
        // The PNG signature is 89504e470d0a1a0a (https://www.w3.org/TR/png-3/#5PNG-file-signature),
        // so a single zero does not start a valid PNG file.
        path.writeBytes(byteArrayOf(0))

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.InvalidImage, error)
    }

    @Test
    fun `parse returns InvalidImage for a PNG whose image data is truncated`(
        @TempDir tempDir: Path,
    ) {
        // Truncating to the header leaves the dimensions readable but removes all image data
        val path = pngPath(tempDir, REQUIRED_WIDTH, REQUIRED_HEIGHT)
        path.writeBytes(path.readBytes().copyOf(PNG_HEADER_LENGTH))

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.InvalidImage, error)
    }

    @Test
    fun `parse returns IncorrectImageDimensions for a PNG which is too narrow`(
        @TempDir tempDir: Path,
    ) {
        val path = pngPath(tempDir, REQUIRED_WIDTH / 2, REQUIRED_HEIGHT)

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.IncorrectImageDimensions, error)
    }

    @Test
    fun `parse returns IncorrectImageDimensions for a PNG which is too short`(
        @TempDir tempDir: Path,
    ) {
        val path = pngPath(tempDir, REQUIRED_WIDTH, REQUIRED_HEIGHT / 2)

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.IncorrectImageDimensions, error)
    }

    @Test
    fun `parse returns IncorrectImageDimensions for a PNG declaring huge dimensions`(
        @TempDir tempDir: Path,
    ) {
        // Rewrite the image's declared dimensions to be far larger than the image data it actually
        // contains.
        val path = pngPath(tempDir, 1, 1)
        val bytes = path.readBytes()
        val header = ByteBuffer.wrap(bytes)
        header.putInt(IHDR_WIDTH_OFFSET, Int.MAX_VALUE).putInt(IHDR_HEIGHT_OFFSET, Int.MAX_VALUE)
        val crc = CRC32().apply { update(bytes, IHDR_TYPE_OFFSET, IHDR_CRC_INPUT_LENGTH) }
        // A CRC-32 is by definition 32 bits wide, so this conversion is lossless
        @Suppress("TruncatingIntegerConversion")
        header.putInt(IHDR_CRC_OFFSET, crc.value.toInt())
        path.writeBytes(bytes)

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.IncorrectImageDimensions, error)
    }

    @Test
    fun `parse returns Io if reading from path fails`(@TempDir tempDir: Path) {
        // Create a file we can't read so that we know the path we pass to Icon.parse() doesn't
        // contain a readable file
        val path = createTempFile(
            directory = tempDir,
            attributes = arrayOf(PosixFilePermissions.asFileAttribute(emptySet())),
        )

        val error = Icon.parse(path).unwrapErr()

        assertEquals(IconParseError.Io, error)
    }
}

/**
 * Writes a valid PNG image of the given dimensions to a new file in [directory] and returns its
 * path.
 */
private fun pngPath(directory: Path, width: Int, height: Int): Path {
    val path = createTempFile(directory, suffix = ".png")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    check(ImageIO.write(image, "PNG", path.toFile())) { "failed to encode PNG" }

    return path
}

private const val REQUIRED_WIDTH = 512
private const val REQUIRED_HEIGHT = 512

// The byte offsets and lengths of the PNG header's fields. A PNG file starts with an 8-byte
// signature followed by the IHDR chunk, which consists of a 4-byte data length, a 4-byte chunk
// type, 13 bytes of data (beginning with the image's 4-byte width and height), and a 4-byte CRC of
// the chunk's type and data.
// See https://www.w3.org/TR/png-3/#5Chunk-layout and https://www.w3.org/TR/png-3/#11IHDR.
private const val IHDR_TYPE_OFFSET = 12
private const val IHDR_WIDTH_OFFSET = 16
private const val IHDR_HEIGHT_OFFSET = 20
private const val IHDR_CRC_INPUT_LENGTH = 17
private const val IHDR_CRC_OFFSET = 29
private const val PNG_HEADER_LENGTH = 33
