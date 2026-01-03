package com.example.hotelservice.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import java.util.UUID;

/**
 * gRPC Interceptor to extract user ID from metadata and store in Context
 * Supports both "x-user-id" header and future JWT token parsing
 */
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
            Context context = Context.current().withValue(USER_ID_CONTEXT_KEY, userId);
            return Contexts.interceptCall(context, call, headers, next);
        } else {
            log.debug("No user ID found in gRPC metadata");
            return next.startCall(call, headers);
        }
    }

    /**
     * Extract user ID from gRPC metadata
     * Priority: x-user-id header > JWT token (if implemented)
     */
    private UUID extractUserId(Metadata headers) {
        try {
            // Try to get user ID from x-user-id header
            String userIdStr = headers.get(USER_ID_METADATA_KEY);
            if (userIdStr != null && !userIdStr.isEmpty()) {
                return UUID.fromString(userIdStr);
            }

            // TODO: Add JWT token parsing here if needed
            // String authHeader = headers.get(AUTHORIZATION_METADATA_KEY);
            // if (authHeader != null && authHeader.startsWith("Bearer ")) {
            //     String token = authHeader.substring(7);
            //     return parseUserIdFromJwt(token);
            // }

        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID format in gRPC metadata: {}", e.getMessage());
        }

        return null;
    }
}
