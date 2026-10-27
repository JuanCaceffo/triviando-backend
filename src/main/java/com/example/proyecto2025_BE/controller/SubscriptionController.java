package com.example.proyecto2025_BE.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyecto2025_BE.model.mp.WebhookNotification;
import com.example.proyecto2025_BE.service.MercadoPagoSubscriptionService;
import com.example.proyecto2025_BE.views.subscriptions.SubscriptionPlanResponse;

import lombok.RequiredArgsConstructor;

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
        String initPoint = mpService.createSubscriptionPreference(email);
        return ResponseEntity.ok().body(initPoint);
    }

    @PostMapping("/payment")
    public ResponseEntity<String> payment(
            @RequestBody(required = false) String payload,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "type", required = false) String type,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {
        WebhookNotification notification = new WebhookNotification(
                payload,
                dataId != null ? dataId : id,
                type,
                signature,
                requestId);

        return ResponseEntity.ok(mpService.notifyPayment(notification));
    }
} 