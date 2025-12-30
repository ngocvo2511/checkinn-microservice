package com.example.bookingservice.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: booking.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class BookingGrpcServiceGrpc {

  private BookingGrpcServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "booking.BookingGrpcService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreateBookingRequest,
      com.example.bookingservice.grpc.BookingResponse> getCreateBookingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateBooking",
      requestType = com.example.bookingservice.grpc.CreateBookingRequest.class,
      responseType = com.example.bookingservice.grpc.BookingResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreateBookingRequest,
      com.example.bookingservice.grpc.BookingResponse> getCreateBookingMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreateBookingRequest, com.example.bookingservice.grpc.BookingResponse> getCreateBookingMethod;
    if ((getCreateBookingMethod = BookingGrpcServiceGrpc.getCreateBookingMethod) == null) {
      synchronized (BookingGrpcServiceGrpc.class) {
        if ((getCreateBookingMethod = BookingGrpcServiceGrpc.getCreateBookingMethod) == null) {
          BookingGrpcServiceGrpc.getCreateBookingMethod = getCreateBookingMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.CreateBookingRequest, com.example.bookingservice.grpc.BookingResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateBooking"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.CreateBookingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.BookingResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BookingGrpcServiceMethodDescriptorSupplier("CreateBooking"))
              .build();
        }
      }
    }
    return getCreateBookingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetBookingRequest,
      com.example.bookingservice.grpc.BookingResponse> getGetBookingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetBooking",
      requestType = com.example.bookingservice.grpc.GetBookingRequest.class,
      responseType = com.example.bookingservice.grpc.BookingResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetBookingRequest,
      com.example.bookingservice.grpc.BookingResponse> getGetBookingMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetBookingRequest, com.example.bookingservice.grpc.BookingResponse> getGetBookingMethod;
    if ((getGetBookingMethod = BookingGrpcServiceGrpc.getGetBookingMethod) == null) {
      synchronized (BookingGrpcServiceGrpc.class) {
        if ((getGetBookingMethod = BookingGrpcServiceGrpc.getGetBookingMethod) == null) {
          BookingGrpcServiceGrpc.getGetBookingMethod = getGetBookingMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.GetBookingRequest, com.example.bookingservice.grpc.BookingResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetBooking"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.GetBookingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.BookingResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BookingGrpcServiceMethodDescriptorSupplier("GetBooking"))
              .build();
        }
      }
    }
    return getGetBookingMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static BookingGrpcServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceStub>() {
        @java.lang.Override
        public BookingGrpcServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BookingGrpcServiceStub(channel, callOptions);
        }
      };
    return BookingGrpcServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static BookingGrpcServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceBlockingStub>() {
        @java.lang.Override
        public BookingGrpcServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BookingGrpcServiceBlockingStub(channel, callOptions);
        }
      };
    return BookingGrpcServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static BookingGrpcServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BookingGrpcServiceFutureStub>() {
        @java.lang.Override
        public BookingGrpcServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BookingGrpcServiceFutureStub(channel, callOptions);
        }
      };
    return BookingGrpcServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void createBooking(com.example.bookingservice.grpc.CreateBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateBookingMethod(), responseObserver);
    }

    /**
     */
    default void getBooking(com.example.bookingservice.grpc.GetBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetBookingMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service BookingGrpcService.
   */
  public static abstract class BookingGrpcServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return BookingGrpcServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service BookingGrpcService.
   */
  public static final class BookingGrpcServiceStub
      extends io.grpc.stub.AbstractAsyncStub<BookingGrpcServiceStub> {
    private BookingGrpcServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BookingGrpcServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BookingGrpcServiceStub(channel, callOptions);
    }

    /**
     */
    public void createBooking(com.example.bookingservice.grpc.CreateBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateBookingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getBooking(com.example.bookingservice.grpc.GetBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetBookingMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service BookingGrpcService.
   */
  public static final class BookingGrpcServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<BookingGrpcServiceBlockingStub> {
    private BookingGrpcServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BookingGrpcServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BookingGrpcServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.example.bookingservice.grpc.BookingResponse createBooking(com.example.bookingservice.grpc.CreateBookingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateBookingMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.example.bookingservice.grpc.BookingResponse getBooking(com.example.bookingservice.grpc.GetBookingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetBookingMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service BookingGrpcService.
   */
  public static final class BookingGrpcServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<BookingGrpcServiceFutureStub> {
    private BookingGrpcServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BookingGrpcServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BookingGrpcServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.BookingResponse> createBooking(
        com.example.bookingservice.grpc.CreateBookingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateBookingMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.BookingResponse> getBooking(
        com.example.bookingservice.grpc.GetBookingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetBookingMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_BOOKING = 0;
  private static final int METHODID_GET_BOOKING = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_BOOKING:
          serviceImpl.createBooking((com.example.bookingservice.grpc.CreateBookingRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse>) responseObserver);
          break;
        case METHODID_GET_BOOKING:
          serviceImpl.getBooking((com.example.bookingservice.grpc.GetBookingRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.BookingResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getCreateBookingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.CreateBookingRequest,
              com.example.bookingservice.grpc.BookingResponse>(
                service, METHODID_CREATE_BOOKING)))
        .addMethod(
          getGetBookingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.GetBookingRequest,
              com.example.bookingservice.grpc.BookingResponse>(
                service, METHODID_GET_BOOKING)))
        .build();
  }

  private static abstract class BookingGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    BookingGrpcServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.example.bookingservice.grpc.BookingProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("BookingGrpcService");
    }
  }

  private static final class BookingGrpcServiceFileDescriptorSupplier
      extends BookingGrpcServiceBaseDescriptorSupplier {
    BookingGrpcServiceFileDescriptorSupplier() {}
  }

  private static final class BookingGrpcServiceMethodDescriptorSupplier
      extends BookingGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    BookingGrpcServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (BookingGrpcServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new BookingGrpcServiceFileDescriptorSupplier())
              .addMethod(getCreateBookingMethod())
              .addMethod(getGetBookingMethod())
              .build();
        }
      }
    }
    return result;
  }
}
