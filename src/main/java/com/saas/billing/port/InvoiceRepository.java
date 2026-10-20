package com.saas.billing.port;

import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;

public interface InvoiceRepository {

    /** @throws BillingInfrastructureException se a gravação falhar */
    void save(String customerId, Money amount, InvoiceStatus status);
}
