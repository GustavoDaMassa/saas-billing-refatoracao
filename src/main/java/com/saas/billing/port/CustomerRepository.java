package com.saas.billing.port;

import com.saas.billing.domain.Customer;
import java.util.Optional;

public interface CustomerRepository {

    /** @throws BillingInfrastructureException se a consulta falhar */
    Optional<Customer> findById(String id);
}
