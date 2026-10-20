package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.NotificationService;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class SmtpNotificationService implements NotificationService {

    private final String hostName;
    private final int smtpPort;
    private final String from;

    public SmtpNotificationService(String hostName, int smtpPort, String from) {
        this.hostName = hostName;
        this.smtpPort = smtpPort;
        this.from = from;
    }

    @Override
    public void sendInvoice(String email, Money amount) {
        if (email == null) {
            throw new BillingInfrastructureException(new EmailException("e-mail do destinatário ausente"));
        }
        try {
            SimpleEmail emailSender = new SimpleEmail();
            emailSender.setHostName(hostName);
            emailSender.setSmtpPort(smtpPort);
            emailSender.setFrom(from);
            emailSender.setSubject("Sua Fatura Mensal");
            emailSender.setMsg("Olá! Sua fatura foi processada no valor de R$ " + amount);
            emailSender.addTo(email);
            // TODO: o envio já estava desativado no legado. Ativar send() muda comportamento
            // (passa a exigir SMTP real) e precisa de aprovação do negócio. Ver RELATORIO.md, smell #8.
            // emailSender.send();
        } catch (EmailException e) {
            throw new BillingInfrastructureException(e);
        }
    }
}
