package com.example.bookingservice.grpc.interceptor;

import io.grpc.Status;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public final class GrpcAccess {
    private GrpcAccess() {}

    public static Authentication actor() {
        Authentication actor = UserContextInterceptor.ACTOR.get();
        if (actor == null) throw Status.UNAUTHENTICATED.asRuntimeException();
        return actor;
    }

    public static void require(boolean allowed) {
        if (!allowed) throw Status.PERMISSION_DENIED.asRuntimeException();
    }

    public static Throwable error(Exception error) {
        if (error instanceof io.grpc.StatusRuntimeException || error instanceof io.grpc.StatusException) return error;
        if (error instanceof ResponseStatusException response) {
            return switch (response.getStatusCode().value()) {
                case 404 -> Status.NOT_FOUND.asRuntimeException();
                case 409 -> Status.FAILED_PRECONDITION.asRuntimeException();
                case 503 -> Status.UNAVAILABLE.asRuntimeException();
                default -> Status.INTERNAL.asRuntimeException();
            };
        }
        if (error instanceof IllegalArgumentException) return Status.INVALID_ARGUMENT.asRuntimeException();
        return Status.INTERNAL.withDescription("Request could not be completed").asRuntimeException();
    }
}
