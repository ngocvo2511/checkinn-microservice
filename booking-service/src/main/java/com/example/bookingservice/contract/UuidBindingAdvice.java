package com.example.bookingservice.contract;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/** Prevent Spring's permissive default UUID property editor from accepting short groups. */
@ControllerAdvice
public class UuidBindingAdvice {
    @InitBinder public void uuidEditor(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
            @Override public void setAsText(String value) { setValue(UuidContract.required(value)); }
        });
    }
}
