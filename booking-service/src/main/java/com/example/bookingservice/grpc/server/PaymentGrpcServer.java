package com.example.bookingservice.grpc.server;

import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.dto.PaymentResponse;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.grpc.PaymentGrpcServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

@GrpcService
public class PaymentGrpcServer extends PaymentGrpcServiceGrpc.PaymentGrpcServiceImplBase {

    private final PaymentService paymentService;

    public PaymentGrpcServer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void createPayment(com.example.bookingservice.grpc.CreatePaymentRequest request, StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
        try {
            CreatePaymentRequest create = CreatePaymentRequest.builder()
                    .bookingId(request.getBookingId())
                    .amount(BigDecimal.valueOf(request.getAmount()))
                    .method(PaymentMethod.valueOf(request.getMethod()))
                    .build();
            PaymentResponse payment = paymentService.createPayment(create);
            responseObserver.onNext(toProto(payment));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    @Override
    public void getPayment(com.example.bookingservice.grpc.GetPaymentRequest request, StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
        try {
            PaymentResponse payment = paymentService.getPayment(request.getId());
            responseObserver.onNext(toProto(payment));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    @Override
    public void getPaymentByBooking(com.example.bookingservice.grpc.GetPaymentByBookingRequest request, StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
        try {
            PaymentResponse payment = paymentService.getPaymentByBookingId(request.getBookingId());
            responseObserver.onNext(toProto(payment));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    @Override
    public void refundPayment(com.example.bookingservice.grpc.RefundPaymentRequest request, StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
        try {
            PaymentResponse payment = paymentService.refundPayment(request.getId());
            responseObserver.onNext(toProto(payment));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    @Override
    public void processVNPayCallback(com.example.bookingservice.grpc.VNPayCallbackRequest request, StreamObserver<com.example.bookingservice.grpc.PaymentResponse> responseObserver) {
        try {
            PaymentResponse payment = paymentService.processVNPayCallback(request.getOrderId(), request.getResponseCode(), request.getTransactionId());
            responseObserver.onNext(toProto(payment));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(ex);
        }
    }

    private com.example.bookingservice.grpc.PaymentResponse toProto(PaymentResponse payment) {
        return com.example.bookingservice.grpc.PaymentResponse.newBuilder()
                .setId(payment.getId())
                .setBookingId(payment.getBookingId())
                .setAmount(payment.getAmount().doubleValue())
                .setMethod(payment.getMethod().name())
                .setStatus(payment.getStatus().name())
                .setTransactionId(payment.getTransactionId() == null ? "" : payment.getTransactionId())
                .setVnpayOrderId(payment.getVnpayOrderId() == null ? "" : payment.getVnpayOrderId())
                .setVnpayResponseCode(payment.getVnpayResponseCode() == null ? "" : payment.getVnpayResponseCode())
                .setPaidAt(payment.getPaidAt() == null ? "" : String.valueOf(payment.getPaidAt()))
                .setCreatedAt(String.valueOf(payment.getCreatedAt()))
                .setUpdatedAt(String.valueOf(payment.getUpdatedAt()))
                .build();
    }
}
