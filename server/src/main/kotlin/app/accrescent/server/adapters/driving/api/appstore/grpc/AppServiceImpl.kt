package app.accrescent.server.adapters.driving.api.appstore.grpc

import build.buf.gen.accrescent.appstore.v1.AppServiceGrpcKt
import kotlin.coroutines.CoroutineContext

class AppServiceImpl(
    context: CoroutineContext,
) : AppServiceGrpcKt.AppServiceCoroutineImplBase(context)
