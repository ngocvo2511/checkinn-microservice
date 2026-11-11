package com.example.hotelservice.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.TreeMap;

/** Validate direct service/RPC callers as well as HTTP, before any external effect. */
public final class InputValidation {
    private static final Validator VALIDATOR=Validation.buildDefaultValidatorFactory().getValidator();
    private InputValidation() {}
    public static void require(Object request) {
        Map<String,String> errors=new TreeMap<>();
        if (request==null) errors.put("request","must not be null");
        else VALIDATOR.validate(request).forEach(v -> errors.merge(v.getPropertyPath().toString(),v.getMessage(),(a,b)->a.compareTo(b)<=0?a:b));
        if (!errors.isEmpty()) throw new InvalidInput(errors);
    }
    public static void requireItems(java.util.List<?> items, String field) {
        Map<String,String> errors=new TreeMap<>();
        if(items==null) errors.put(field,"must not be null");
        else for(int i=0;i<items.size();i++) {
            Object item=items.get(i);String path=field+"["+i+"]";
            if(item==null)errors.put(path,"must not be null");
            else VALIDATOR.validate(item).forEach(v->errors.merge(path+"."+v.getPropertyPath(),v.getMessage(),(a,b)->a.compareTo(b)<=0?a:b));
        }
        if(!errors.isEmpty())throw new InvalidInput(errors);
    }
    public static final class InvalidInput extends IllegalArgumentException {
        private final Map<String,String> fieldErrors;
        public InvalidInput(Map<String,String> errors) { super("Invalid request fields: " + String.join("; ",new TreeMap<>(errors).values())); fieldErrors=Map.copyOf(errors); }
        public Map<String,String> getFieldErrors() { return fieldErrors; }
    }
}
