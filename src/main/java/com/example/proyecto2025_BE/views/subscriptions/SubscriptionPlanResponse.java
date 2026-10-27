package com.example.proyecto2025_BE.views.subscriptions;

import java.math.BigDecimal;

public record SubscriptionPlanResponse(
        BigDecimal price,
        String currency,
        String billingType
) {
}
