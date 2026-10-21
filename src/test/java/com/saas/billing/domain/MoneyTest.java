package com.saas.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldAddDecimalAmountsExactly() {
        Money result = Money.of("0.10").plus(Money.of("0.20"));

        assertEquals(Money.of("0.30"), result);
    }

    @Test
    void shouldSubtractAmounts() {
        Money result = Money.of("100.00").minus(Money.of("30.00"));

        assertEquals(Money.of("70.00"), result);
    }

    @Test
    void shouldKeepPrecisionUntilExplicitRounding() {
        Money result = Money.of("10.10").times(new BigDecimal("0.85"));

        assertAll(
                () -> assertEquals(Money.of("8.585"), result),
                () -> assertEquals(Money.of("8.59"), result.rounded()),
                () -> assertEquals(Money.of("8.585"), result)
        );
    }

    @Test
    void shouldRoundHalfCentUp() {
        assertAll(
                () -> assertEquals(
                        Money.of("8.59"),
                        Money.of("8.585").rounded()
                ),
                () -> assertEquals(
                        Money.of("8.58"),
                        Money.of("8.584").rounded()
                ),
                () -> assertEquals(
                        Money.of("-8.59"),
                        Money.of("-8.585").rounded()
                )
        );
    }

    @Test
    void shouldApplyZeroFloorOnlyToNegativeAmounts() {
        assertAll(
                () -> assertEquals(
                        Money.zero(),
                        Money.of("-10.00").floorAtZero()
                ),
                () -> assertEquals(
                        Money.zero(),
                        Money.zero().floorAtZero()
                ),
                () -> assertEquals(
                        Money.of("10.00"),
                        Money.of("10.00").floorAtZero()
                )
        );
    }

    @Test
    void shouldCompareByValueRegardlessOfScale() {
        Money first = Money.of("100");
        Money second = Money.of("100.00");

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertNotEquals(first, Money.of("100.01")),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "100")
        );
    }

    @Test
    void shouldFormatWithTwoDecimalPlacesWithoutChangingValue() {
        Money amount = Money.of("8.585");

        assertAll(
                () -> assertEquals("100.00", Money.of("100").toString()),
                () -> assertEquals("0.00", Money.zero().toString()),
                () -> assertEquals("8.59", amount.toString()),
                () -> assertEquals(Money.of("8.585"), amount)
        );
    }

    @Test
    void shouldNotModifyOriginalAmountDuringOperations() {
        Money original = Money.of("100.00");

        Money added = original.plus(Money.of("10.00"));
        Money subtracted = original.minus(Money.of("10.00"));
        Money multiplied = original.times(new BigDecimal("0.70"));

        assertAll(
                () -> assertEquals(Money.of("100.00"), original),
                () -> assertEquals(Money.of("110.00"), added),
                () -> assertEquals(Money.of("90.00"), subtracted),
                () -> assertEquals(Money.of("70.00"), multiplied)
        );
    }

    @Test
    void shouldCreateMoneyFromDouble() {
        assertEquals(Money.of("10.10"), Money.of(10.10));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(
                NullPointerException.class,
                () -> new Money(null)
        );
    }
}