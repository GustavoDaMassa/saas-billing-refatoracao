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
import java.math.BigDecimal;
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

    public SubscriptionBillingService(CustomerRepository customerRepository, InvoiceRepository invoiceRepository,
                                      PaymentGateway paymentGateway, NotificationService notificationService,
                                      Clock clock) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * @deprecated use {@link #processMonthlyBilling(BillingCommand)}. Mantido para chamadores existentes;
     * data inválida continua lançando {@link java.time.format.DateTimeParseException}.
     */
    @Deprecated
    public List<String> processMonthlyBilling(String customerId, String planType, double basePrice,
                                             String signupDateStr, int overdueDays, String promoCode,
                                             boolean isNonProfit, String paymentCardToken) {
        BillingCommand command = new BillingCommand(customerId, planType, Money.of(basePrice),
                LocalDate.parse(signupDateStr), overdueDays, promoCode, isNonProfit, paymentCardToken);
        return processMonthlyBilling(command);
    }

    public List<String> processMonthlyBilling(BillingCommand command) {

        List<String> logs = new ArrayList<>();

        String customerId = command.customerId();
        LocalDate signupDate = command.signupDate();
        LocalDate today = LocalDate.now(clock);

        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.15"))); // 15% após 2 anos
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.10"))); // 10% após 1 ano
        }

        if (command.nonProfit()) {
            calculatedPrice = calculatedPrice.times(new BigDecimal("0.70")); // 30% off para ONGs
        }

        String promoCode = command.promoCode();
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.minus(Money.of("30.00"));
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.times(new BigDecimal("0.90"));
            }
        }

        int overdueDays = command.overdueDays();
        if (overdueDays > 0) {
            Money penalty = calculatedPrice.times(new BigDecimal("0.02"));
            Money dailyInterest = calculatedPrice.times(new BigDecimal("0.001")).times(BigDecimal.valueOf(overdueDays));
            calculatedPrice = calculatedPrice.plus(penalty).plus(dailyInterest);
        }

        calculatedPrice = calculatedPrice.floorAtZero().rounded();

        try {
            Optional<Customer> foundCustomer = customerRepository.findById(customerId);

            if (foundCustomer.isPresent()) {
                Customer customer = foundCustomer.get();

                if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
                    logs.add("Cliente inativo. Cobrança cancelada: " + customerId);
                    return logs;
                }

                boolean paymentSuccess = paymentGateway.charge(command.paymentCardToken(), calculatedPrice);
                if (paymentSuccess) {
                    logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
                } else {
                    logs.add("Falha no pagamento do cartão para: " + customerId);
                }

                invoiceRepository.save(customerId, calculatedPrice, paymentSuccess ? InvoiceStatus.PAID : InvoiceStatus.FAILED);

                notificationService.sendInvoice(customer.email(), calculatedPrice);

                logs.add("E-mail disparado para: " + customer.email());
            }
        } catch (BillingInfrastructureException e) {
            logs.add("Erro fatal no processamento: " + e.getMessage());
        }

        return logs;
    }
}
