package app.accrescent.parcelo.apksparser

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

internal val manifestReader: ObjectReader = XmlMapper.builder()
    .defaultUseWrapper(false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()
    .registerKotlinModule()
    .readerFor(AndroidManifest::class.java)
