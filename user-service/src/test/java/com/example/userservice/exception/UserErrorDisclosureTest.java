package com.example.userservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkinn.user.grpc.LoginRequest;
import com.checkinn.user.grpc.RegisterRequest;
import com.checkinn.user.grpc.UserResponse;
import com.example.userservice.service.UserGrpcService;
import com.example.userservice.service.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** F-9F-1 (Batch 9G): only deliberate business messages leave user-service; unexpected failures are generic. */
class UserErrorDisclosureTest {
    static final String SECRET = "jdbc:postgresql://secret-host password=synthetic";

    @RestController
    static class Probe {
        @GetMapping("/probe/business") String business() { throw new UserBusinessException("Mật khẩu hiện tại không đúng"); }
        @GetMapping("/probe/bug") String bug() { throw new IllegalStateException(SECRET); }
        @GetMapping("/probe/runtime") String runtime() { throw new RuntimeException(SECRET); }
        @GetMapping("/probe/checked") String checked() throws Exception { throw new java.io.IOException(SECRET); }
        @GetMapping("/probe/session") String session() { throw new com.example.userservice.security.SessionStoreUnavailableException(); }
        @GetMapping("/probe/denied") String denied() { throw new org.springframework.security.access.AccessDeniedException(SECRET); }
        @GetMapping("/probe/header") String header(@RequestHeader("Authorization") String authorization) { return authorization; }
    }

    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void businessRejectionKeepsItsStatusAndUserFacingMessage() throws Exception {
        mvc.perform(get("/probe/business")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng"));
    }

    @Test void unexpectedFailuresAreGeneric500sWithoutTheExceptionText() throws Exception {
        for (String path : new String[]{"/probe/runtime", "/probe/checked"})
            mvc.perform(get(path)).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(content().string(not(containsString("secret-host"))));
        // Other unchecked failures (e.g. IllegalStateException) take the same generic path.
        mvc.perform(get("/probe/bug")).andExpect(status().isInternalServerError()).andExpect(content().string(not(containsString("secret-host"))));
    }

    @Test void dependencyAuthorizationAndRequestShapeFailuresKeepDistinctStatuses() throws Exception {
        mvc.perform(get("/probe/session")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mvc.perform(get("/probe/denied")).andExpect(status().isForbidden()).andExpect(content().string(not(containsString("secret-host"))));
        mvc.perform(get("/probe/header")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void grpcForwardsOnlyBusinessMessages() {
        var users = mock(UserService.class);
        var rpc = new UserGrpcService(users);
        doThrow(new UserBusinessException("Tên đăng nhập đã tồn tại")).when(users).registerUser(any(), any());
        doThrow(new RuntimeException(SECRET)).when(users).login(any(), any());
        var register = new Recorder(); var login = new Recorder();
        rpc.registerUser(RegisterRequest.newBuilder().setUsername("synthetic").setEmail("synthetic@example.invalid").setPassword("synthetic-pass").build(), register);
        rpc.loginUser(LoginRequest.newBuilder().setUsernameOrEmail("synthetic").setPassword("synthetic-pass").build(), login);
        assertThat(Status.fromThrowable(register.error).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(Status.fromThrowable(register.error).getDescription()).isEqualTo("Tên đăng nhập đã tồn tại");
        assertThat(Status.fromThrowable(login.error).getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(Status.fromThrowable(login.error).getDescription()).isEqualTo("Request rejected").doesNotContain("secret-host");
    }

    static final class Recorder implements StreamObserver<UserResponse> {
        Throwable error;
        @Override public void onNext(UserResponse value) {}
        @Override public void onError(Throwable t) { error = t; }
        @Override public void onCompleted() {}
    }
}
