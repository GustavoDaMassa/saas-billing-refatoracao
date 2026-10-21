package com.saas.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

public final class PricingCalculator {

    private static final int MONTHS_PER_YEAR = 12;
    private static final int FIRST_LOYALTY_THRESHOLD = 12;
    private static final int SECOND_LOYALTY_THRESHOLD = 24;
    private static final int WELCOME_MAX_MONTHS = 1;

    private static final BigDecimal FIRST_LOYALTY_DISCOUNT =
            new BigDecimal("0.10");
    private static final BigDecimal SECOND_LOYALTY_DISCOUNT =
            new BigDecimal("0.15");
    private static final BigDecimal NON_PROFIT_FACTOR =
            new BigDecimal("0.70");
    private static final BigDecimal WELCOME_FACTOR =
            new BigDecimal("0.90");
    private static final BigDecimal LATE_PAYMENT_PENALTY =
            new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE =
            new BigDecimal("0.001");

    private static final Money BLACK_FRIDAY_DISCOUNT = Money.of("30.00");
    private static final String BLACK_FRIDAY_COUPON = "BLACKFRIDAY";
    private static final String WELCOME_COUPON = "WELCOME10";

    public Money calculate(BillingCommand command, LocalDate today) {
        int monthsActive = monthsActive(command.signupDate(), today);
        Money price = applyLoyaltyDiscount(command.basePrice(), monthsActive);
        price = applyNonProfitDiscount(price, command.nonProfit());
        price = applyCoupon(price, command.promoCode(), monthsActive, today);
        price = applyLatePaymentCharges(price, command.overdueDays());
        return price.floorAtZero().rounded();
    }

    private int monthsActive(LocalDate signupDate, LocalDate today) {
        return (today.getYear() - signupDate.getYear()) * MONTHS_PER_YEAR
                + today.getMonthValue() - signupDate.getMonthValue();
    }

    private Money applyLoyaltyDiscount(Money basePrice, int monthsActive) {
        if (monthsActive > SECOND_LOYALTY_THRESHOLD) {
            return basePrice.minus(basePrice.times(SECOND_LOYALTY_DISCOUNT));
        }

        if (monthsActive > FIRST_LOYALTY_THRESHOLD) {
            return basePrice.minus(basePrice.times(FIRST_LOYALTY_DISCOUNT));
        }

        return basePrice;
    }

    private Money applyNonProfitDiscount(Money price, boolean nonProfit) {
        return nonProfit ? price.times(NON_PROFIT_FACTOR) : price;
    }

    private Money applyCoupon(
            Money price,
            String promoCode,
            int monthsActive,
            LocalDate today
    ) {
        if (promoCode == null || promoCode.trim().isEmpty()) {
            return price;
        }

        if (promoCode.equalsIgnoreCase(BLACK_FRIDAY_COUPON)
                && today.getMonth() == Month.NOVEMBER) {
            return price.minus(BLACK_FRIDAY_DISCOUNT);
        }

        if (promoCode.equalsIgnoreCase(WELCOME_COUPON)
                && monthsActive <= WELCOME_MAX_MONTHS) {
            return price.times(WELCOME_FACTOR);
        }

        return price;
    }

    private Money applyLatePaymentCharges(Money price, int overdueDays) {
        if (overdueDays <= 0) {
            return price;
        }

        Money penalty = price.times(LATE_PAYMENT_PENALTY);
        Money interest = price.times(DAILY_INTEREST_RATE)
                .times(BigDecimal.valueOf(overdueDays));

        return price.plus(penalty).plus(interest);
    }
}