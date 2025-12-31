package com.wealthmanagement.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> portfolioFallback() {
        log.warn("Portfolio service fallback triggered");
        return buildFallbackResponse("Portfolio service is currently unavailable. Please try again later.");
    }

    @GetMapping("/transaction")
    public ResponseEntity<Map<String, Object>> transactionFallback() {
        log.warn("Transaction service fallback triggered");
        return buildFallbackResponse("Transaction service is currently unavailable. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", message);

        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
