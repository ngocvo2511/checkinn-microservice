package com.example.userservice.controller;

import com.example.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final JwtService jwtService;

    @GetMapping("/generate-token/{userId}")
    public Map<String, String> generateToken(@PathVariable Long userId) {
        String token = jwtService.generateToken(userId);
        Map<String, String> response = new HashMap<>();
        response.put("userId", userId.toString());
        response.put("token", token);
        response.put("instruction", "Copy this token to localStorage in browser console: localStorage.setItem('token', '" + token + "')");
        return response;
    }
}
