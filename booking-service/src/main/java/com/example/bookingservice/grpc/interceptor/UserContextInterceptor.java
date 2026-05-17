package com.example.bookingservice.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
@GrpcGlobalServerInterceptor
public class UserContextInterceptor implements ServerInterceptor {

    public static final Context.Key<UUID> USER_ID_CONTEXT_KEY = Context.key("userId");
    private static final Metadata.Key<String> USER_ID_METADATA_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        UUID userId = extractUserId(headers);
        if (userId != null) {
            log.debug("Extracted user ID from gRPC metadata: {}", userId);
            MDC.put("userId", userId.toString());
            Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
            ServerCall.Listener<ReqT> listener = Contexts.interceptCall(context, call, headers, next);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } finally {
                        MDC.remove("userId");
                    }
                }

                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } finally {
                        MDC.remove("userId");
                    }
                }
            };
        }

        log.debug("No user ID found in gRPC metadata");
        return next.startCall(call, headers);
    }

    private UUID extractUserId(Metadata headers) {
        try {
            String userIdStr = headers.get(USER_ID_METADATA_KEY);
            if (userIdStr != null && !userIdStr.isEmpty()) {
                return UUID.fromString(userIdStr);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID format in gRPC metadata: {}", e.getMessage());
        }
        return null;
    }
}
