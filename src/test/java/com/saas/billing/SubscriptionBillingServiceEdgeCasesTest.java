package com.saas.billing;

import com.saas.billing.adapter.SimulatedCardPaymentGateway;
import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionBillingServiceEdgeCasesTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-15T12:00:00Z"),
            ZoneOffset.UTC
    );

    private final List<String> events = new ArrayList<>();
    private final List<InvoiceStatus> invoiceStatuses = new ArrayList<>();

    private String customerStatus;
    private String failureAt;
    private boolean programmingFailure;
    private SubscriptionBillingService service;

    @BeforeEach
    void setUp() {
        customerStatus = "ACTIVE";
        failureAt = null;
        programmingFailure = false;

        CustomerRepository customers = id -> {
            recordStep("find");

            if (programmingFailure) {
                throw new IllegalStateException("Bug simulado");
            }

            return Optional.of(new Customer(id, "a@x.com", customerStatus));
        };

        SimulatedCardPaymentGateway simulatedGateway =
                new SimulatedCardPaymentGateway();

        PaymentGateway payments = (token, amount) -> {
            recordStep("charge");
            return simulatedGateway.charge(token, amount);
        };

        InvoiceRepository invoices = (id, amount, status) -> {
            recordStep("save");
            invoiceStatuses.add(status);
        };

        NotificationService notifications = (email, amount) ->
                recordStep("notify");

        service = new SubscriptionBillingService(
                customers,
                invoices,
                payments,
                notifications,
                FIXED_CLOCK
        );
    }

    @ParameterizedTest(name = "Token {0}: aprovado = {1}")
    @MethodSource("cardTokens")
    void shouldUseLegacyTokenRule(String token, boolean approved) {
        List<String> logs = service.processMonthlyBilling(command(token));

        String paymentLog = approved
                ? "Pagamento processado com sucesso: R$ 100.00"
                : "Falha no pagamento do cartão para: c1";

        InvoiceStatus expectedStatus = approved
                ? InvoiceStatus.PAID
                : InvoiceStatus.FAILED;

        assertAll(
                () -> assertEquals(
                        List.of("find", "charge", "save", "notify"),
                        events
                ),
                () -> assertEquals(List.of(expectedStatus), invoiceStatuses),
                () -> assertEquals(
                        List.of(paymentLog, "E-mail disparado para: a@x.com"),
                        logs
                )
        );
    }

    static Stream<Arguments> cardTokens() {
        return Stream.of(
                Arguments.of("tok_valid", true),
                Arguments.of("tok_", true),
                Arguments.of("invalid", false),
                Arguments.of("TOK_valid", false),
                Arguments.of((String) null, false)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "SUSPENDED"})
    void shouldCancelForNullEmptyOrInactiveStatus(String status) {
        customerStatus = status;

        List<String> logs = service.processMonthlyBilling(command("tok_valid"));

        assertAll(
                () -> assertEquals(List.of("find"), events),
                () -> assertTrue(invoiceStatuses.isEmpty()),
                () -> assertEquals(
                        List.of("Cliente inativo. Cobrança cancelada: c1"),
                        logs
                )
        );
    }

    @ParameterizedTest(name = "Falha de infraestrutura em {0}")
    @ValueSource(strings = {"find", "charge", "notify"})
    void shouldStopAtInfrastructureFailure(String step) {
        failureAt = step;

        List<String> logs = service.processMonthlyBilling(command("tok_valid"));

        List<String> expectedEvents = switch (step) {
            case "find" -> List.of("find");
            case "charge" -> List.of("find", "charge");
            default -> List.of("find", "charge", "save", "notify");
        };

        String errorLog = "Erro fatal no processamento: Falha em " + step;

        List<String> expectedLogs = step.equals("notify")
                ? List.of(
                        "Pagamento processado com sucesso: R$ 100.00",
                        errorLog
                )
                : List.of(errorLog);

        List<InvoiceStatus> expectedInvoices = step.equals("notify")
                ? List.of(InvoiceStatus.PAID)
                : List.of();

        assertAll(
                () -> assertEquals(expectedEvents, events),
                () -> assertEquals(expectedLogs, logs),
                () -> assertEquals(expectedInvoices, invoiceStatuses)
        );
    }

    @Test
    void shouldPropagateProgrammingErrors() {
        programmingFailure = true;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.processMonthlyBilling(command("tok_valid"))
        );

        assertAll(
                () -> assertEquals("Bug simulado", error.getMessage()),
                () -> assertEquals(List.of("find"), events),
                () -> assertTrue(invoiceStatuses.isEmpty())
        );
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldRejectInvalidDateInLegacySignatureBeforeIO() {
        assertThrows(
                DateTimeParseException.class,
                () -> service.processMonthlyBilling(
                        "c1",
                        "STANDARD",
                        100.00,
                        "data-invalida",
                        0,
                        null,
                        false,
                        "tok_valid"
                )
        );

        assertTrue(events.isEmpty());
    }

    @Test
    void shouldCalculateBeforeLookingUpCustomer() {
        BillingCommand invalidCommand = new BillingCommand(
                "c1",
                "STANDARD",
                Money.of("100.00"),
                null,
                0,
                null,
                false,
                "tok_valid"
        );

        assertThrows(
                NullPointerException.class,
                () -> service.processMonthlyBilling(invalidCommand)
        );

        assertTrue(events.isEmpty());
    }

    private void recordStep(String step) {
        events.add(step);

        if (step.equals(failureAt)) {
            throw new BillingInfrastructureException(
                    new IllegalStateException("Falha em " + step)
            );
        }
    }

    private BillingCommand command(String token) {
        return new BillingCommand(
                "c1",
                "STANDARD",
                Money.of("100.00"),
                LocalDate.of(2026, 11, 1),
                0,
                null,
                false,
                token
        );
    }
}