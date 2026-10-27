package com.example.proyecto2025_BE.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyecto2025_BE.service.MercadoPagoSubscriptionService;
import com.example.proyecto2025_BE.views.subscriptions.SubscriptionPlanResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/suscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final MercadoPagoSubscriptionService mpService;

    @GetMapping("/plan")
    public ResponseEntity<SubscriptionPlanResponse> getPlan() {
        return ResponseEntity.ok(mpService.getPlan());
    }

    @PostMapping
    public ResponseEntity<?> startSubscription(@RequestParam String email) {
    	log.error("Entrando por startSubscription");
        String initPoint = mpService.createSubscriptionPreference(email);
        log.error("initPoint devuelto");
        return ResponseEntity.ok().body(initPoint);
    }

    @PostMapping("/payment")
    public ResponseEntity<String> payment(@RequestBody String payload) {
    	String response = mpService.notifyPayment(payload);
    	
    	return ResponseEntity.ok(response);
    }
} 