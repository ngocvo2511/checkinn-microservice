package com.example.bookingservice.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: payment.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class PaymentGrpcServiceGrpc {

  private PaymentGrpcServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "payment.PaymentGrpcService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreatePaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getCreatePaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreatePayment",
      requestType = com.example.bookingservice.grpc.CreatePaymentRequest.class,
      responseType = com.example.bookingservice.grpc.PaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreatePaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getCreatePaymentMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.CreatePaymentRequest, com.example.bookingservice.grpc.PaymentResponse> getCreatePaymentMethod;
    if ((getCreatePaymentMethod = PaymentGrpcServiceGrpc.getCreatePaymentMethod) == null) {
      synchronized (PaymentGrpcServiceGrpc.class) {
        if ((getCreatePaymentMethod = PaymentGrpcServiceGrpc.getCreatePaymentMethod) == null) {
          PaymentGrpcServiceGrpc.getCreatePaymentMethod = getCreatePaymentMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.CreatePaymentRequest, com.example.bookingservice.grpc.PaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreatePayment"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.CreatePaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.PaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentGrpcServiceMethodDescriptorSupplier("CreatePayment"))
              .build();
        }
      }
    }
    return getCreatePaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getGetPaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPayment",
      requestType = com.example.bookingservice.grpc.GetPaymentRequest.class,
      responseType = com.example.bookingservice.grpc.PaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getGetPaymentMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentRequest, com.example.bookingservice.grpc.PaymentResponse> getGetPaymentMethod;
    if ((getGetPaymentMethod = PaymentGrpcServiceGrpc.getGetPaymentMethod) == null) {
      synchronized (PaymentGrpcServiceGrpc.class) {
        if ((getGetPaymentMethod = PaymentGrpcServiceGrpc.getGetPaymentMethod) == null) {
          PaymentGrpcServiceGrpc.getGetPaymentMethod = getGetPaymentMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.GetPaymentRequest, com.example.bookingservice.grpc.PaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPayment"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.GetPaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.PaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentGrpcServiceMethodDescriptorSupplier("GetPayment"))
              .build();
        }
      }
    }
    return getGetPaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentByBookingRequest,
      com.example.bookingservice.grpc.PaymentResponse> getGetPaymentByBookingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPaymentByBooking",
      requestType = com.example.bookingservice.grpc.GetPaymentByBookingRequest.class,
      responseType = com.example.bookingservice.grpc.PaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentByBookingRequest,
      com.example.bookingservice.grpc.PaymentResponse> getGetPaymentByBookingMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.GetPaymentByBookingRequest, com.example.bookingservice.grpc.PaymentResponse> getGetPaymentByBookingMethod;
    if ((getGetPaymentByBookingMethod = PaymentGrpcServiceGrpc.getGetPaymentByBookingMethod) == null) {
      synchronized (PaymentGrpcServiceGrpc.class) {
        if ((getGetPaymentByBookingMethod = PaymentGrpcServiceGrpc.getGetPaymentByBookingMethod) == null) {
          PaymentGrpcServiceGrpc.getGetPaymentByBookingMethod = getGetPaymentByBookingMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.GetPaymentByBookingRequest, com.example.bookingservice.grpc.PaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPaymentByBooking"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.GetPaymentByBookingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.PaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentGrpcServiceMethodDescriptorSupplier("GetPaymentByBooking"))
              .build();
        }
      }
    }
    return getGetPaymentByBookingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.RefundPaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getRefundPaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RefundPayment",
      requestType = com.example.bookingservice.grpc.RefundPaymentRequest.class,
      responseType = com.example.bookingservice.grpc.PaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.RefundPaymentRequest,
      com.example.bookingservice.grpc.PaymentResponse> getRefundPaymentMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.RefundPaymentRequest, com.example.bookingservice.grpc.PaymentResponse> getRefundPaymentMethod;
    if ((getRefundPaymentMethod = PaymentGrpcServiceGrpc.getRefundPaymentMethod) == null) {
      synchronized (PaymentGrpcServiceGrpc.class) {
        if ((getRefundPaymentMethod = PaymentGrpcServiceGrpc.getRefundPaymentMethod) == null) {
          PaymentGrpcServiceGrpc.getRefundPaymentMethod = getRefundPaymentMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.RefundPaymentRequest, com.example.bookingservice.grpc.PaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RefundPayment"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.RefundPaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.PaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentGrpcServiceMethodDescriptorSupplier("RefundPayment"))
              .build();
        }
      }
    }
    return getRefundPaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.example.bookingservice.grpc.VNPayCallbackRequest,
      com.example.bookingservice.grpc.PaymentResponse> getProcessVNPayCallbackMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ProcessVNPayCallback",
      requestType = com.example.bookingservice.grpc.VNPayCallbackRequest.class,
      responseType = com.example.bookingservice.grpc.PaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.example.bookingservice.grpc.VNPayCallbackRequest,
      com.example.bookingservice.grpc.PaymentResponse> getProcessVNPayCallbackMethod() {
    io.grpc.MethodDescriptor<com.example.bookingservice.grpc.VNPayCallbackRequest, com.example.bookingservice.grpc.PaymentResponse> getProcessVNPayCallbackMethod;
    if ((getProcessVNPayCallbackMethod = PaymentGrpcServiceGrpc.getProcessVNPayCallbackMethod) == null) {
      synchronized (PaymentGrpcServiceGrpc.class) {
        if ((getProcessVNPayCallbackMethod = PaymentGrpcServiceGrpc.getProcessVNPayCallbackMethod) == null) {
          PaymentGrpcServiceGrpc.getProcessVNPayCallbackMethod = getProcessVNPayCallbackMethod =
              io.grpc.MethodDescriptor.<com.example.bookingservice.grpc.VNPayCallbackRequest, com.example.bookingservice.grpc.PaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ProcessVNPayCallback"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.VNPayCallbackRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.example.bookingservice.grpc.PaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentGrpcServiceMethodDescriptorSupplier("ProcessVNPayCallback"))
              .build();
        }
      }
    }
    return getProcessVNPayCallbackMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PaymentGrpcServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceStub>() {
        @java.lang.Override
        public PaymentGrpcServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentGrpcServiceStub(channel, callOptions);
        }
      };
    return PaymentGrpcServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PaymentGrpcServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceBlockingStub>() {
        @java.lang.Override
        public PaymentGrpcServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentGrpcServiceBlockingStub(channel, callOptions);
        }
      };
    return PaymentGrpcServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PaymentGrpcServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentGrpcServiceFutureStub>() {
        @java.lang.Override
        public PaymentGrpcServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentGrpcServiceFutureStub(channel, callOptions);
        }
      };
    return PaymentGrpcServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void createPayment(com.example.bookingservice.grpc.CreatePaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreatePaymentMethod(), responseObserver);
    }

    /**
     */
    default void getPayment(com.example.bookingservice.grpc.GetPaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPaymentMethod(), responseObserver);
    }

    /**
     */
    default void getPaymentByBooking(com.example.bookingservice.grpc.GetPaymentByBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPaymentByBookingMethod(), responseObserver);
    }

    /**
     */
    default void refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRefundPaymentMethod(), responseObserver);
    }

    /**
     */
    default void processVNPayCallback(com.example.bookingservice.grpc.VNPayCallbackRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getProcessVNPayCallbackMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PaymentGrpcService.
   */
  public static abstract class PaymentGrpcServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PaymentGrpcServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PaymentGrpcService.
   */
  public static final class PaymentGrpcServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PaymentGrpcServiceStub> {
    private PaymentGrpcServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentGrpcServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentGrpcServiceStub(channel, callOptions);
    }

    /**
     */
    public void createPayment(com.example.bookingservice.grpc.CreatePaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreatePaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getPayment(com.example.bookingservice.grpc.GetPaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getPaymentByBooking(com.example.bookingservice.grpc.GetPaymentByBookingRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPaymentByBookingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRefundPaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void processVNPayCallback(com.example.bookingservice.grpc.VNPayCallbackRequest request,
        io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getProcessVNPayCallbackMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PaymentGrpcService.
   */
  public static final class PaymentGrpcServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PaymentGrpcServiceBlockingStub> {
    private PaymentGrpcServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentGrpcServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentGrpcServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.example.bookingservice.grpc.PaymentResponse createPayment(com.example.bookingservice.grpc.CreatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreatePaymentMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.example.bookingservice.grpc.PaymentResponse getPayment(com.example.bookingservice.grpc.GetPaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPaymentMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.example.bookingservice.grpc.PaymentResponse getPaymentByBooking(com.example.bookingservice.grpc.GetPaymentByBookingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPaymentByBookingMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.example.bookingservice.grpc.PaymentResponse refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRefundPaymentMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.example.bookingservice.grpc.PaymentResponse processVNPayCallback(com.example.bookingservice.grpc.VNPayCallbackRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getProcessVNPayCallbackMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PaymentGrpcService.
   */
  public static final class PaymentGrpcServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PaymentGrpcServiceFutureStub> {
    private PaymentGrpcServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentGrpcServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentGrpcServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.PaymentResponse> createPayment(
        com.example.bookingservice.grpc.CreatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreatePaymentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.PaymentResponse> getPayment(
        com.example.bookingservice.grpc.GetPaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPaymentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.PaymentResponse> getPaymentByBooking(
        com.example.bookingservice.grpc.GetPaymentByBookingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPaymentByBookingMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.PaymentResponse> refundPayment(
        com.example.bookingservice.grpc.RefundPaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRefundPaymentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.example.bookingservice.grpc.PaymentResponse> processVNPayCallback(
        com.example.bookingservice.grpc.VNPayCallbackRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getProcessVNPayCallbackMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_PAYMENT = 0;
  private static final int METHODID_GET_PAYMENT = 1;
  private static final int METHODID_GET_PAYMENT_BY_BOOKING = 2;
  private static final int METHODID_REFUND_PAYMENT = 3;
  private static final int METHODID_PROCESS_VNPAY_CALLBACK = 4;

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
        case METHODID_CREATE_PAYMENT:
          serviceImpl.createPayment((com.example.bookingservice.grpc.CreatePaymentRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse>) responseObserver);
          break;
        case METHODID_GET_PAYMENT:
          serviceImpl.getPayment((com.example.bookingservice.grpc.GetPaymentRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse>) responseObserver);
          break;
        case METHODID_GET_PAYMENT_BY_BOOKING:
          serviceImpl.getPaymentByBooking((com.example.bookingservice.grpc.GetPaymentByBookingRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse>) responseObserver);
          break;
        case METHODID_REFUND_PAYMENT:
          serviceImpl.refundPayment((com.example.bookingservice.grpc.RefundPaymentRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse>) responseObserver);
          break;
        case METHODID_PROCESS_VNPAY_CALLBACK:
          serviceImpl.processVNPayCallback((com.example.bookingservice.grpc.VNPayCallbackRequest) request,
              (io.grpc.stub.StreamObserver<com.example.bookingservice.grpc.PaymentResponse>) responseObserver);
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
          getCreatePaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.CreatePaymentRequest,
              com.example.bookingservice.grpc.PaymentResponse>(
                service, METHODID_CREATE_PAYMENT)))
        .addMethod(
          getGetPaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.GetPaymentRequest,
              com.example.bookingservice.grpc.PaymentResponse>(
                service, METHODID_GET_PAYMENT)))
        .addMethod(
          getGetPaymentByBookingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.GetPaymentByBookingRequest,
              com.example.bookingservice.grpc.PaymentResponse>(
                service, METHODID_GET_PAYMENT_BY_BOOKING)))
        .addMethod(
          getRefundPaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.RefundPaymentRequest,
              com.example.bookingservice.grpc.PaymentResponse>(
                service, METHODID_REFUND_PAYMENT)))
        .addMethod(
          getProcessVNPayCallbackMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.example.bookingservice.grpc.VNPayCallbackRequest,
              com.example.bookingservice.grpc.PaymentResponse>(
                service, METHODID_PROCESS_VNPAY_CALLBACK)))
        .build();
  }

  private static abstract class PaymentGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PaymentGrpcServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.example.bookingservice.grpc.PaymentProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PaymentGrpcService");
    }
  }

  private static final class PaymentGrpcServiceFileDescriptorSupplier
      extends PaymentGrpcServiceBaseDescriptorSupplier {
    PaymentGrpcServiceFileDescriptorSupplier() {}
  }

  private static final class PaymentGrpcServiceMethodDescriptorSupplier
      extends PaymentGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PaymentGrpcServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PaymentGrpcServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PaymentGrpcServiceFileDescriptorSupplier())
              .addMethod(getCreatePaymentMethod())
              .addMethod(getGetPaymentMethod())
              .addMethod(getGetPaymentByBookingMethod())
              .addMethod(getRefundPaymentMethod())
              .addMethod(getProcessVNPayCallbackMethod())
              .build();
        }
      }
    }
    return result;
  }
}
