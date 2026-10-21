package com.saas.billing;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.domain.PricingCalculator;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final PricingCalculator pricingCalculator = new PricingCalculator();

    public SubscriptionBillingService(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            PaymentGateway paymentGateway,
            NotificationService notificationService,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * @deprecated use {@link #processMonthlyBilling(BillingCommand)}.
     * Mantido para chamadores existentes; data inválida continua lançando
     * {@link java.time.format.DateTimeParseException}.
     */
    @Deprecated
    public List<String> processMonthlyBilling(
            String customerId,
            String planType,
            double basePrice,
            String signupDateStr,
            int overdueDays,
            String promoCode,
            boolean isNonProfit,
            String paymentCardToken
    ) {
        BillingCommand command = new BillingCommand(
                customerId,
                planType,
                Money.of(basePrice),
                LocalDate.parse(signupDateStr),
                overdueDays,
                promoCode,
                isNonProfit,
                paymentCardToken
        );
        return processMonthlyBilling(command);
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        List<String> logs = new ArrayList<>();
        Money price = pricingCalculator.calculate(command, LocalDate.now(clock));
        try {
            Optional<Customer> found = customerRepository.findById(command.customerId());
            if (found.isEmpty()) {
                return logs;
            }
            Customer customer = found.get();
            if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
                logs.add("Cliente inativo. Cobrança cancelada: " + command.customerId());
                return logs;
            }
            billCustomer(command, customer, price, logs);
        } catch (BillingInfrastructureException e) {
            logs.add("Erro fatal no processamento: " + e.getMessage());
        }
        return logs;
    }

    private void billCustomer(
            BillingCommand command,
            Customer customer,
            Money price,
            List<String> logs
    ) {
        boolean paymentSuccess = paymentGateway.charge(
                command.paymentCardToken(), price
        );
        logPaymentResult(paymentSuccess, command.customerId(), price, logs);

        InvoiceStatus status = paymentSuccess
                ? InvoiceStatus.PAID
                : InvoiceStatus.FAILED;

        invoiceRepository.save(command.customerId(), price, status);
        notificationService.sendInvoice(customer.email(), price);
        logs.add("E-mail disparado para: " + customer.email());
    }

    private void logPaymentResult(
            boolean paymentSuccess,
            String customerId,
            Money price,
            List<String> logs
    ) {
        if (paymentSuccess) {
            logs.add("Pagamento processado com sucesso: R$ " + price);
        } else {
            logs.add("Falha no pagamento do cartão para: " + customerId);
        }
    }
}