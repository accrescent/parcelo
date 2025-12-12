// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.nativeimage

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
import io.quarkus.runtime.annotations.RegisterForReflection

/**
 * Reflection configuration to enable GraalVM Native Image support for protovalidate-java
 */
@Suppress("unused")
@RegisterForReflection(
    targets = [
        AnyRules::class,
        BoolRules::class,
        BytesRules::class,
        DescriptorProtos::class,
        DoubleRules::class,
        DurationRules::class,
        EnumRules::class,
        FieldMaskRules::class,
        FieldRules::class,
        Fixed32Rules::class,
        Fixed64Rules::class,
        FloatRules::class,
        Ignore::class,
        Int32Rules::class,
        Int64Rules::class,
        KnownRegex::class,
        MapRules::class,
        MessageRules::class,
        OneofRules::class,
        PredefinedRules::class,
        RepeatedRules::class,
        Rule::class,
        SFixed32Rules::class,
        SFixed64Rules::class,
        SInt32Rules::class,
        SInt64Rules::class,
        StringRules::class,
        TimestampRules::class,
        UInt32Rules::class,
        UInt64Rules::class,
    ],
)
class ProtovalidateReflectionConfig
