package com.saas.billing.port;

import com.saas.billing.domain.Money;

public interface NotificationService {

    /** @throws BillingInfrastructureException se a montagem/envio do e-mail falhar */
    void sendInvoice(String email, Money amount);
}
