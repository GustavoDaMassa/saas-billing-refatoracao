package com.saas.billing.domain;

import java.time.LocalDate;

/**
 * Entrada da cobrança mensal (Introduce Parameter Object).
 * {@code planType} é mantido por compatibilidade, mas não é usado em nenhuma regra.
 */
public record BillingCommand(
        String customerId,
        String planType,
        Money basePrice,
        LocalDate signupDate,
        int overdueDays,
        String promoCode,
        boolean nonProfit,
        String paymentCardToken) {
}
