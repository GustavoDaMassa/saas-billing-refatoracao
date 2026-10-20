package com.saas.billing;

import com.saas.billing.adapter.JdbcCustomerRepository;
import com.saas.billing.adapter.JdbcInvoiceRepository;
import com.saas.billing.adapter.SimulatedCardPaymentGateway;
import com.saas.billing.adapter.SmtpNotificationService;
import java.time.Clock;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Composição de produção: monta o serviço com os adaptadores reais e o relógio do sistema.
 * Banco: BILLING_DB_URL, BILLING_DB_USER e BILLING_DB_PASSWORD (obrigatórias).
 * SMTP: BILLING_SMTP_HOST, BILLING_SMTP_PORT e BILLING_SMTP_FROM (opcionais; padrão = valores do legado).
 */
public final class BillingServiceFactory {

    private BillingServiceFactory() {
    }

    public static SubscriptionBillingService create() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(requiredEnv("BILLING_DB_URL"));
        dataSource.setUser(requiredEnv("BILLING_DB_USER"));
        dataSource.setPassword(requiredEnv("BILLING_DB_PASSWORD"));

        SmtpNotificationService notificationService = new SmtpNotificationService(
                envOrDefault("BILLING_SMTP_HOST", "smtp.empresa.com"),
                Integer.parseInt(envOrDefault("BILLING_SMTP_PORT", "587")),
                envOrDefault("BILLING_SMTP_FROM", "billing@empresa.com"));

        return new SubscriptionBillingService(
                new JdbcCustomerRepository(dataSource),
                new JdbcInvoiceRepository(dataSource),
                new SimulatedCardPaymentGateway(),
                notificationService,
                Clock.systemDefaultZone());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
