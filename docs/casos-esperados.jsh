// Casos de caracterização do legado (commit 87ef839).
// Reproduz a fórmula de preço do legado linha a linha em double, com "hoje" fixo,
// e a compara com o cálculo exato em BigDecimal arredondado (HALF_UP e HALF_EVEN).
// Uso: jshell -q docs/casos-esperados.jsh   (a varredura final leva alguns minutos)
import java.time.*;
import java.math.*;

// Réplica literal da fórmula do legado (linhas 24-57), com "hoje" parametrizado
double legado(double basePrice, String signupDateStr, int overdueDays, String promoCode, boolean isNonProfit, LocalDate today) {
    LocalDate signupDate = LocalDate.parse(signupDateStr);
    int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());
    double calculatedPrice = basePrice;
    if (monthsActive > 24) {
        calculatedPrice = calculatedPrice - (basePrice * 0.15);
    } else if (monthsActive > 12) {
        calculatedPrice = calculatedPrice - (basePrice * 0.10);
    }
    if (isNonProfit) {
        calculatedPrice = calculatedPrice * 0.70;
    }
    if (promoCode != null && !promoCode.trim().isEmpty()) {
        if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
            calculatedPrice = calculatedPrice - 30.0;
        } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
            calculatedPrice = calculatedPrice * 0.90;
        }
    }
    if (overdueDays > 0) {
        double penalty = calculatedPrice * 0.02;
        double dailyInterest = (calculatedPrice * 0.001) * overdueDays;
        calculatedPrice = calculatedPrice + penalty + dailyInterest;
    }
    if (calculatedPrice < 0) {
        calculatedPrice = 0.0;
    }
    return calculatedPrice;
}

// Mesma fórmula em BigDecimal com precisão total (como o Money fará), sem arredondar
BigDecimal exato(double basePrice, String signupDateStr, int overdueDays, String promoCode, boolean isNonProfit, LocalDate today) {
    LocalDate signupDate = LocalDate.parse(signupDateStr);
    int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());
    BigDecimal base = BigDecimal.valueOf(basePrice);
    BigDecimal p = base;
    if (monthsActive > 24) p = p.subtract(base.multiply(new BigDecimal("0.15")));
    else if (monthsActive > 12) p = p.subtract(base.multiply(new BigDecimal("0.10")));
    if (isNonProfit) p = p.multiply(new BigDecimal("0.70"));
    if (promoCode != null && !promoCode.trim().isEmpty()) {
        if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) p = p.subtract(new BigDecimal("30.0"));
        else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) p = p.multiply(new BigDecimal("0.90"));
    }
    if (overdueDays > 0) {
        BigDecimal penalty = p.multiply(new BigDecimal("0.02"));
        BigDecimal interest = p.multiply(new BigDecimal("0.001")).multiply(BigDecimal.valueOf(overdueDays));
        p = p.add(penalty).add(interest);
    }
    if (p.signum() < 0) p = BigDecimal.ZERO;
    return p;
}

LocalDate NOV = LocalDate.parse("2026-11-15");
LocalDate OUT = LocalDate.parse("2026-10-15");

void caso(String id, String desc, double base, String signup, int overdue, String promo, boolean ong, LocalDate today) {
    double d = legado(base, signup, overdue, promo, ong, today);
    BigDecimal legRound = BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    BigDecimal ex = exato(base, signup, overdue, promo, ong, today);
    BigDecimal up = ex.setScale(2, RoundingMode.HALF_UP);
    BigDecimal even = ex.setScale(2, RoundingMode.HALF_EVEN);
    String promoStr = promo == null ? "null" : "\"" + promo + "\"";
    System.out.println(String.join(" | ", "", id, desc, String.valueOf(base), signup, today.toString(), String.valueOf(overdue), promoStr, String.valueOf(ong),
        "`" + d + "`", ex.stripTrailingZeros().toPlainString(), legRound.toPlainString(), up.toPlainString(), even.toPlainString(),
        (legRound.equals(up) ? "" : "DIVERGE-UP ") + (legRound.equals(even) ? "" : "DIVERGE-EVEN"), ""));
}

System.out.println("| ID | Cenário | base | signup | hoje | atraso | cupom | ONG | double legado | exato | legado arred. HALF_UP | Money HALF_UP | Money HALF_EVEN | obs |");
caso("C01", "cliente novo, sem nada", 100.0, "2026-11-01", 0, null, false, NOV);
caso("C02", "fidelidade 13 meses (-10%)", 100.0, "2025-10-15", 0, null, false, NOV);
caso("C03", "borda: 12 meses exatos (sem desconto)", 100.0, "2025-11-15", 0, null, false, NOV);
caso("C04", "borda: 24 meses exatos (-10%)", 100.0, "2024-11-15", 0, null, false, NOV);
caso("C05", "fidelidade 25 meses (-15%)", 100.0, "2024-10-15", 0, null, false, NOV);
caso("C06", "fidelidade 25 meses, base 11,40 (imprecisão do double)", 11.4, "2024-10-15", 0, null, false, NOV);
caso("C07", "ONG (-30%)", 100.0, "2026-11-01", 0, null, true, NOV);
caso("C08", "WELCOME10 válido (0 meses)", 100.0, "2026-11-01", 0, "WELCOME10", false, NOV);
caso("C09", "borda: WELCOME10 com 1 mês (válido)", 100.0, "2026-10-31", 0, "WELCOME10", false, NOV);
caso("C10", "WELCOME10 inválido (2 meses)", 100.0, "2026-09-15", 0, "WELCOME10", false, NOV);
caso("C11", "BLACKFRIDAY em novembro (-R$30)", 100.0, "2026-11-01", 0, "BLACKFRIDAY", false, NOV);
caso("C12", "BLACKFRIDAY fora de novembro", 100.0, "2026-10-01", 0, "BLACKFRIDAY", false, OUT);
caso("C13", "cupom minúsculo blackfriday (nov)", 100.0, "2026-11-01", 0, "blackfriday", false, NOV);
caso("C14", "cupom minúsculo welcome10", 100.0, "2026-11-01", 0, "welcome10", false, NOV);
caso("C15", "cupom com espaço ' BLACKFRIDAY' (não aplica)", 100.0, "2026-11-01", 0, " BLACKFRIDAY", false, NOV);
caso("C16", "cupom só com espaços (ignorado)", 100.0, "2026-11-01", 0, "   ", false, NOV);
caso("C17", "cupom desconhecido", 100.0, "2026-11-01", 0, "XPTO", false, NOV);
caso("C18", "atraso 10 dias (+2% +0,1%/dia)", 100.0, "2026-11-01", 10, null, false, NOV);
caso("C19", "BLACKFRIDAY deixa negativo -> piso 0", 20.0, "2026-11-01", 0, "BLACKFRIDAY", false, NOV);
caso("C20", "BLACKFRIDAY negativo + atraso 5 (multa sobre negativo) -> 0", 20.0, "2026-11-01", 5, "BLACKFRIDAY", false, NOV);
caso("C21", "ONG + fidelidade 25m + atraso 7 dias", 99.9, "2024-10-15", 7, null, true, NOV);
caso("C22", "ONG + fidelidade 13m + BLACKFRIDAY (nov)", 49.9, "2025-10-01", 0, "BLACKFRIDAY", true, NOV);
caso("C23", "fidelidade 13m + WELCOME10 (não aplica: >1 mês)", 100.0, "2025-10-15", 0, "WELCOME10", false, NOV);
caso("C24", "ONG + WELCOME10 + atraso 3 dias", 59.9, "2026-11-10", 3, "WELCOME10", true, NOV);
caso("C25", "meio centavo exato: fidelidade 25m, base 10,10", 10.1, "2024-10-15", 0, null, false, NOV);

// Varredura: procura divergência entre legado arredondado e BigDecimal exato arredondado
int divUp = 0, divEven = 0, total = 0;
String[] promos = {null, "BLACKFRIDAY", "WELCOME10"};
String[] signups = {"2026-11-01", "2025-10-15", "2024-10-15"};
for (int cents = 1; cents <= 30000; cents++) {
  double base = cents / 100.0;
  for (String s : signups) for (String pr : promos) for (boolean ong : new boolean[]{false, true}) for (int od : new int[]{0, 1, 7, 30}) {
    total++;
    BigDecimal legR = BigDecimal.valueOf(legado(base, s, od, pr, ong, NOV)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal ex = exato(base, s, od, pr, ong, NOV);
    if (!legR.equals(ex.setScale(2, RoundingMode.HALF_UP))) { if (divUp < 5) System.out.println("divUP base=" + base + " s=" + s + " pr=" + pr + " ong=" + ong + " od=" + od + " leg=" + legado(base, s, od, pr, ong, NOV) + " ex=" + ex.toPlainString()); divUp++; }
    if (!legR.equals(ex.setScale(2, RoundingMode.HALF_EVEN))) divEven++;
  }
}
System.out.println("varredura: total=" + total + " divergências HALF_UP=" + divUp + " HALF_EVEN=" + divEven);
/exit
