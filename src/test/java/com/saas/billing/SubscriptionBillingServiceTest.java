package com.saas.billing;

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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionBillingServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-15T12:00:00Z"),
            ZoneOffset.UTC
    );

    private final List<String> events = new ArrayList<>();

    private Customer customer;
    private boolean paymentApproved;
    private boolean invoiceFailure;

    private final List<Money> chargedAmounts = new ArrayList<>();
    private final List<RecordedInvoice> invoices = new ArrayList<>();
    private final List<RecordedNotification> notifications = new ArrayList<>();

    private SubscriptionBillingService service;

    @BeforeEach
    void setUp() {
        customer = new Customer("c1", "a@x.com", "ACTIVE");
        paymentApproved = true;
        invoiceFailure = false;

        CustomerRepository customers = id -> {
            events.add("find");
            return Optional.ofNullable(customer);
        };

        PaymentGateway payments = (token, amount) -> {
            events.add("charge");
            chargedAmounts.add(amount);
            return paymentApproved;
        };

        InvoiceRepository invoiceRepository = (customerId, amount, status) -> {
            events.add("save");

            if (invoiceFailure) {
                throw new BillingInfrastructureException(
                        new IllegalStateException("Falha simulada no INSERT")
                );
            }

            invoices.add(new RecordedInvoice(customerId, amount, status));
        };

        NotificationService notificationService = (email, amount) -> {
            events.add("notify");
            notifications.add(new RecordedNotification(email, amount));
        };

        service = new SubscriptionBillingService(
                customers,
                invoiceRepository,
                payments,
                notificationService,
                FIXED_CLOCK
        );
    }

    @Test
    void shouldChargeSaveAndNotifyActiveCustomerInOrder() {
        List<String> logs = service.processMonthlyBilling(standardCommand());

        assertAll(
                () -> assertEquals(List.of("find", "charge", "save", "notify"), events),
                () -> assertEquals(List.of(Money.of("100.00")), chargedAmounts),
                () -> assertEquals(
                        List.of(new RecordedInvoice(
                                "c1", Money.of("100.00"), InvoiceStatus.PAID
                        )),
                        invoices
                ),
                () -> assertEquals(
                        List.of(new RecordedNotification("a@x.com", Money.of("100.00"))),
                        notifications
                ),
                () -> assertEquals(
                        List.of(
                                "Pagamento processado com sucesso: R$ 100.00",
                                "E-mail disparado para: a@x.com"
                        ),
                        logs
                )
        );
    }

    @Test
    void shouldReturnEmptyLogsWhenCustomerDoesNotExist() {
        customer = null;

        List<String> logs = service.processMonthlyBilling(standardCommand());

        assertAll(
                () -> assertTrue(logs.isEmpty()),
                () -> assertEquals(List.of("find"), events),
                () -> assertTrue(chargedAmounts.isEmpty()),
                () -> assertTrue(invoices.isEmpty()),
                () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldCancelBillingForInactiveCustomer() {
        customer = new Customer("c1", "a@x.com", "INACTIVE");

        List<String> logs = service.processMonthlyBilling(standardCommand());

        assertAll(
                () -> assertEquals(
                        List.of("Cliente inativo. Cobrança cancelada: c1"),
                        logs
                ),
                () -> assertEquals(List.of("find"), events),
                () -> assertTrue(chargedAmounts.isEmpty()),
                () -> assertTrue(invoices.isEmpty()),
                () -> assertTrue(notifications.isEmpty())
        );
    }

    @Test
    void shouldSaveFailedInvoiceAndNotifyWhenPaymentIsDeclined() {
        paymentApproved = false;

        List<String> logs = service.processMonthlyBilling(standardCommand());

        assertAll(
                () -> assertEquals(List.of("find", "charge", "save", "notify"), events),
                () -> assertEquals(
                        List.of(new RecordedInvoice(
                                "c1", Money.of("100.00"), InvoiceStatus.FAILED
                        )),
                        invoices
                ),
                () -> assertEquals(
                        List.of(new RecordedNotification("a@x.com", Money.of("100.00"))),
                        notifications
                ),
                () -> assertEquals(
                        List.of(
                                "Falha no pagamento do cartão para: c1",
                                "E-mail disparado para: a@x.com"
                        ),
                        logs
                )
        );
    }

    @Test
    void shouldKeepPaymentLogAndStopWhenSavingInvoiceFails() {
        invoiceFailure = true;

        List<String> logs = service.processMonthlyBilling(standardCommand());

        assertAll(
                () -> assertEquals(List.of("find", "charge", "save"), events),
                () -> assertEquals(List.of(Money.of("100.00")), chargedAmounts),
                () -> assertTrue(invoices.isEmpty()),
                () -> assertTrue(notifications.isEmpty()),
                () -> assertEquals(
                        List.of(
                                "Pagamento processado com sucesso: R$ 100.00",
                                "Erro fatal no processamento: Falha simulada no INSERT"
                        ),
                        logs
                )
        );
    }

    @Test
    void shouldApplyBlackFridayUsingInjectedClock() {
        BillingCommand command = new BillingCommand(
                "c1",
                "STANDARD",
                Money.of("100.00"),
                LocalDate.of(2026, 11, 1),
                0,
                "BLACKFRIDAY",
                false,
                "tok_valid"
        );

        List<String> logs = service.processMonthlyBilling(command);

        assertAll(
                () -> assertEquals(List.of(Money.of("70.00")), chargedAmounts),
                () -> assertEquals(
                        List.of(new RecordedInvoice(
                                "c1", Money.of("70.00"), InvoiceStatus.PAID
                        )),
                        invoices
                ),
                () -> assertEquals(
                        List.of(new RecordedNotification("a@x.com", Money.of("70.00"))),
                        notifications
                ),
                () -> assertEquals(
                        List.of(
                                "Pagamento processado com sucesso: R$ 70.00",
                                "E-mail disparado para: a@x.com"
                        ),
                        logs
                )
        );
    }

    private BillingCommand standardCommand() {
        return new BillingCommand(
                "c1",
                "STANDARD",
                Money.of("100.00"),
                LocalDate.of(2026, 11, 1),
                0,
                null,
                false,
                "tok_valid"
        );
    }

    private record RecordedInvoice(
            String customerId,
            Money amount,
            InvoiceStatus status
    ) {
    }

    private record RecordedNotification(String email, Money amount) {
    }
}