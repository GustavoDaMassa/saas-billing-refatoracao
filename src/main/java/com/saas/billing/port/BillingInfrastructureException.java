package com.saas.billing.port;

/**
 * Falha de infraestrutura (banco, SMTP etc.) traduzida pelos adaptadores.
 * Mantém a mensagem da causa original, que é a usada no log de erro do serviço.
 */
public class BillingInfrastructureException extends RuntimeException {

    public BillingInfrastructureException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
