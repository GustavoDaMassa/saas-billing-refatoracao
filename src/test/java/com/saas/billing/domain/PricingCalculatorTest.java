package com.saas.billing.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("pricingCases")
    void shouldMatchExpectedPrice(
            String scenario,
            String basePrice,
            String signupDate,
            String today,
            int overdueDays,
            String promoCode,
            boolean nonProfit,
            String expectedPrice
    ) {
        BillingCommand command = new BillingCommand(
                "c1",
                "STANDARD",
                Money.of(basePrice),
                LocalDate.parse(signupDate),
                overdueDays,
                promoCode,
                nonProfit,
                "tok_valid"
        );

        Money actual = calculator.calculate(command, LocalDate.parse(today));

        assertEquals(Money.of(expectedPrice), actual, scenario);
    }

    static Stream<Arguments> pricingCases() {
        return Stream.of(
                Arguments.of(
                        "C01 - Cliente novo sem descontos",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, null, false, "100.00"
                ),
                Arguments.of(
                        "C02 - Fidelidade de 13 meses",
                        "100.00", "2025-10-15", "2026-11-15",
                        0, null, false, "90.00"
                ),
                Arguments.of(
                        "C03 - Exatamente 12 meses sem desconto",
                        "100.00", "2025-11-15", "2026-11-15",
                        0, null, false, "100.00"
                ),
                Arguments.of(
                        "C04 - Exatamente 24 meses com desconto de 10%",
                        "100.00", "2024-11-15", "2026-11-15",
                        0, null, false, "90.00"
                ),
                Arguments.of(
                        "C05 - Fidelidade de 25 meses",
                        "100.00", "2024-10-15", "2026-11-15",
                        0, null, false, "85.00"
                ),
                Arguments.of(
                        "C06 - Fidelidade com base de 11,40",
                        "11.40", "2024-10-15", "2026-11-15",
                        0, null, false, "9.69"
                ),
                Arguments.of(
                        "C07 - Desconto para ONG",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, null, true, "70.00"
                ),
                Arguments.of(
                        "C08 - WELCOME10 no mes de adesao",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "WELCOME10", false, "90.00"
                ),
                Arguments.of(
                        "C09 - WELCOME10 com um mes ignorando o dia",
                        "100.00", "2026-10-31", "2026-11-15",
                        0, "WELCOME10", false, "90.00"
                ),
                Arguments.of(
                        "C10 - WELCOME10 nao aplica com dois meses",
                        "100.00", "2026-09-15", "2026-11-15",
                        0, "WELCOME10", false, "100.00"
                ),
                Arguments.of(
                        "C11 - BLACKFRIDAY em novembro",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "BLACKFRIDAY", false, "70.00"
                ),
                Arguments.of(
                        "C12 - BLACKFRIDAY fora de novembro",
                        "100.00", "2026-10-01", "2026-10-15",
                        0, "BLACKFRIDAY", false, "100.00"
                ),
                Arguments.of(
                        "C13 - BLACKFRIDAY em letras minusculas",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "blackfriday", false, "70.00"
                ),
                Arguments.of(
                        "C14 - WELCOME10 em letras minusculas",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "welcome10", false, "90.00"
                ),
                Arguments.of(
                        "C15 - Espaco inicial impede aplicar o cupom",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, " BLACKFRIDAY", false, "100.00"
                ),
                Arguments.of(
                        "C16 - Cupom apenas com espacos",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "   ", false, "100.00"
                ),
                Arguments.of(
                        "C17 - Cupom desconhecido",
                        "100.00", "2026-11-01", "2026-11-15",
                        0, "XPTO", false, "100.00"
                ),
                Arguments.of(
                        "C18 - Multa e juros por dez dias",
                        "100.00", "2026-11-01", "2026-11-15",
                        10, null, false, "103.00"
                ),
                Arguments.of(
                        "C19 - Piso zero depois do BLACKFRIDAY",
                        "20.00", "2026-11-01", "2026-11-15",
                        0, "BLACKFRIDAY", false, "0.00"
                ),
                Arguments.of(
                        "C20 - Piso zero com BLACKFRIDAY e atraso",
                        "20.00", "2026-11-01", "2026-11-15",
                        5, "BLACKFRIDAY", false, "0.00"
                ),
                Arguments.of(
                        "C21 - ONG com fidelidade e atraso",
                        "99.90", "2024-10-15", "2026-11-15",
                        7, null, true, "61.05"
                ),
                Arguments.of(
                        "C22 - ONG com fidelidade e BLACKFRIDAY",
                        "49.90", "2025-10-01", "2026-11-15",
                        0, "BLACKFRIDAY", true, "1.44"
                ),
                Arguments.of(
                        "C23 - Fidelidade sem direito ao WELCOME10",
                        "100.00", "2025-10-15", "2026-11-15",
                        0, "WELCOME10", false, "90.00"
                ),
                Arguments.of(
                        "C24 - ONG com WELCOME10 e atraso",
                        "59.90", "2026-11-10", "2026-11-15",
                        3, "WELCOME10", true, "38.60"
                ),
                Arguments.of(
                        "C25 - Meio centavo arredondado com HALF_UP",
                        "10.10", "2024-10-15", "2026-11-15",
                        0, null, false, "8.59"
                )
        );
    }
}