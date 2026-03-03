// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.protovalidate.deployment

import build.buf.validate.AnyRules
import build.buf.validate.BoolRules
import build.buf.validate.BytesRules
import build.buf.validate.DoubleRules
import build.buf.validate.DurationRules
import build.buf.validate.EnumRules
import build.buf.validate.FieldMaskRules
import build.buf.validate.FieldRules
import build.buf.validate.Fixed32Rules
import build.buf.validate.Fixed64Rules
import build.buf.validate.FloatRules
import build.buf.validate.Ignore
import build.buf.validate.Int32Rules
import build.buf.validate.Int64Rules
import build.buf.validate.KnownRegex
import build.buf.validate.MapRules
import build.buf.validate.MessageRules
import build.buf.validate.OneofRules
import build.buf.validate.PredefinedRules
import build.buf.validate.RepeatedRules
import build.buf.validate.Rule
import build.buf.validate.SFixed32Rules
import build.buf.validate.SFixed64Rules
import build.buf.validate.SInt32Rules
import build.buf.validate.SInt64Rules
import build.buf.validate.StringRules
import build.buf.validate.TimestampRules
import build.buf.validate.UInt32Rules
import build.buf.validate.UInt64Rules
import com.google.protobuf.DescriptorProtos
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem

private const val FEATURE_NAME = "protovalidate"

class ProtovalidateProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun registerForReflection(): ReflectiveClassBuildItem {
        val classes = listOf(
            AnyRules::class.java,
            BoolRules::class.java,
            BytesRules::class.java,
            DescriptorProtos::class.java,
            DoubleRules::class.java,
            DurationRules::class.java,
            EnumRules::class.java,
            FieldMaskRules::class.java,
            FieldRules::class.java,
            Fixed32Rules::class.java,
            Fixed64Rules::class.java,
            FloatRules::class.java,
            Ignore::class.java,
            Int32Rules::class.java,
            Int64Rules::class.java,
            KnownRegex::class.java,
            MapRules::class.java,
            MessageRules::class.java,
            OneofRules::class.java,
            PredefinedRules::class.java,
            RepeatedRules::class.java,
            Rule::class.java,
            SFixed32Rules::class.java,
            SFixed64Rules::class.java,
            SInt32Rules::class.java,
            SInt64Rules::class.java,
            StringRules::class.java,
            TimestampRules::class.java,
            UInt32Rules::class.java,
            UInt64Rules::class.java,
        )
            .flatMap { getNestedClasses(it) }
            .toTypedArray()

        return ReflectiveClassBuildItem
            .builder(*classes)
            .constructors(true)
            .methods(true)
            .fields(true)
            .build()
    }
}

private fun getNestedClasses(clazz: Class<*>): List<Class<*>> {
    return listOf(clazz) + clazz.declaredClasses.flatMap { getNestedClasses(it) }
}
