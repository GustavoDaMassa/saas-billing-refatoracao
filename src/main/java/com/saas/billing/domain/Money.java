package com.saas.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money floorAtZero() {
        return isNegative() ? zero() : this;
    }

    /** Único ponto de arredondamento: 2 casas, HALF_UP (padrão comercial). */
    public Money rounded() {
        return new Money(amount.setScale(SCALE, ROUNDING));
    }

    /** Igualdade por valor numérico: 100, 100.0 e 100.00 são o mesmo Money. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.setScale(SCALE, ROUNDING).toPlainString();
    }
}
