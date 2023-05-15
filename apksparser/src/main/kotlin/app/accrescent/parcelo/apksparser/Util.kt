package app.accrescent.parcelo.apksparser

import java.nio.ByteBuffer

internal fun ByteBuffer.moveToByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
