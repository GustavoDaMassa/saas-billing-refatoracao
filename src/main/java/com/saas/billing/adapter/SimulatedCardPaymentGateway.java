package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.PaymentGateway;

/**
 * Regra de pagamento simulada herdada do legado: aprova se, e somente se, o token
 * não é nulo e começa com "tok_". Nenhuma cobrança real é feita.
 */
public class SimulatedCardPaymentGateway implements PaymentGateway {

    private static final String VALID_TOKEN_PREFIX = "tok_";

    @Override
    public boolean charge(String cardToken, Money amount) {
        return cardToken != null && cardToken.startsWith(VALID_TOKEN_PREFIX);
    }
}
