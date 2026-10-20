package com.saas.billing.port;

import com.saas.billing.domain.Money;

public interface PaymentGateway {

    /** @return {@code true} se a cobrança foi aprovada */
    boolean charge(String cardToken, Money amount);
}
