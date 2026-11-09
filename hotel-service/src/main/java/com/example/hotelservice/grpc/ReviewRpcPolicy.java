package com.example.hotelservice.grpc;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ReviewRpcPolicy {
    private final Validator validator;
    private final int maxPageSize;
    public ReviewRpcPolicy(Validator validator,@Value("${spring.data.web.pageable.max-page-size:2000}") int maxPageSize) {
        if(maxPageSize<=0)throw new IllegalArgumentException("Positive review page limit required");
        this.validator=validator;this.maxPageSize=maxPageSize;
    }
    public void validate(Object request) {
        if(!validator.validate(request).isEmpty())throw new IllegalArgumentException("Invalid review request");
    }
    public PageRequest page(int page,int size) {
        if(page<0 || size<0)throw new IllegalArgumentException("Invalid review page");
        return PageRequest.of(page,Math.min(size==0?10:size,maxPageSize));
    }
}
