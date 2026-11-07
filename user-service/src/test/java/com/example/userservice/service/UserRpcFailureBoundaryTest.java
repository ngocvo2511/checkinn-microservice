package com.example.userservice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.checkinn.user.grpc.*;
import com.example.userservice.security.UserRpcAccess;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRpcFailureBoundaryTest {
    final String email="synthetic-rpc-boundary@example.invalid",detail="SYNTHETIC-PRIVATE-STORAGE-DETAIL";
    final Logger logger=(Logger)LoggerFactory.getLogger(UserGrpcService.class);
    final ListAppender<ILoggingEvent> captured=new ListAppender<>();
    Level oldLevel;boolean oldAdditive;
    @BeforeEach void capture(){oldLevel=logger.getLevel();oldAdditive=logger.isAdditive();logger.setLevel(Level.DEBUG);logger.setAdditive(false);captured.start();logger.addAppender(captured);}
    @AfterEach void restore(){logger.detachAppender(captured);captured.stop();logger.setLevel(oldLevel);logger.setAdditive(oldAdditive);}
    static Stream<org.junit.jupiter.params.provider.Arguments> failures() {
        return Stream.of("register","login","reset","get").flatMap(action->Stream.of("database","transaction","delivery")
                .map(kind->org.junit.jupiter.params.provider.Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("failures")
    void dependencyFaultsAreUnavailableWithoutLeakingIdentityOrExceptionPayload(String action,String kind) {
        var users=mock(UserService.class);var rpc=new UserGrpcService(users);
        RuntimeException fault=switch(kind) {
            case "database" -> new org.springframework.dao.DataAccessResourceFailureException(detail);
            case "transaction" -> new org.springframework.transaction.TransactionSystemException(detail);
            default -> new RuntimeException("Delivery envelope",new org.springframework.amqp.AmqpException(detail));
        };
        doThrow(fault).when(users).registerUser(any(),any());doThrow(fault).when(users).login(any(),any());
        doThrow(fault).when(users).resetPassword(any(),any());doThrow(fault).when(users).getUserById(any());
        var userResponse=new Recorder<UserResponse>();var resetResponse=new Recorder<com.google.protobuf.Empty>();
        Recorder<?> response=action.equals("reset")?resetResponse:userResponse;
        Context.current().withValue(UserRpcAccess.ACTOR,new UserRpcAccess.Actor(UUID.randomUUID().toString(),"ADMIN")).run(()->{
            switch(action) {
                case "register" -> rpc.registerUser(RegisterRequest.newBuilder().setUsername("synthetic-rpc-boundary").setEmail(email).setPassword("synthetic-unused-marker").build(),userResponse);
                case "login" -> rpc.loginUser(LoginRequest.newBuilder().setUsernameOrEmail(email).setPassword("synthetic-unused-marker").build(),userResponse);
                case "reset" -> rpc.resetPassword(ResetPasswordRequest.newBuilder().setEmail(email).setNewPassword("synthetic-unused-marker").build(),resetResponse);
                case "get" -> rpc.getUserById(GetUserRequest.newBuilder().setId(UUID.randomUUID().toString()).build(),userResponse);
                default -> throw new AssertionError("Unknown action");
            }
        });
        assertThat(response.error).isNotNull();assertThat(Status.fromThrowable(response.error).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(Status.fromThrowable(response.error).getDescription()).doesNotContain(detail,email);
        assertThat(response.value).isNull();assertThat(response.complete).isFalse();
        String logs=captured.list.stream().map(event->event.getFormattedMessage()+(event.getThrowableProxy()==null?"":ThrowableProxyUtil.asString(event.getThrowableProxy()))).collect(Collectors.joining("\n"));
        assertThat(logs).doesNotContain(email,detail,"synthetic-unused-marker");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void authorizedExistingAccountWithUnknownProfileNameIsNotMisreportedAsMissing(boolean profilePresent) {
        var users=mock(UserService.class);var user=new com.example.userservice.model.User();UUID id=UUID.randomUUID();
        user.setId(id);user.setUsername("synthetic-user");user.setEmail(email);user.setRole(com.example.userservice.model.Role.CUSTOMER);
        if(profilePresent)user.setProfile(new com.example.userservice.model.UserProfile());
        when(users.getUserById(id)).thenReturn(user);Recorder<UserResponse> response=new Recorder<>();
        Context.current().withValue(UserRpcAccess.ACTOR,new UserRpcAccess.Actor(id.toString(),"CUSTOMER")).run(()->new UserGrpcService(users).getUserById(GetUserRequest.newBuilder().setId(id.toString()).build(),response));
        assertThat(response.error).isNull();assertThat(response.complete).isTrue();assertThat(response.value.getId()).isEqualTo(id.toString());assertThat(response.value.getFullName()).isEmpty();
    }
    static class Recorder<T> implements StreamObserver<T> {
        T value;Throwable error;boolean complete;
        public void onNext(T value){this.value=value;}public void onError(Throwable error){this.error=error;}public void onCompleted(){complete=true;}
    }
}
