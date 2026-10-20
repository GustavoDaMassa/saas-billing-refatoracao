# Relatório Técnico — Refatoração do SaaS Billing Co.

**Disciplina:** Implantação e Entrega de Software — UFG/INF 2026/2 (Profª Sofia Costa Paiva)

**Dupla:**

- Gustavo Henrique — [@GustavoDaMassa](https://github.com/GustavoDaMassa)
- _a preencher_

**Repositório:** https://github.com/GustavoDaMassa/saas-billing-refatoracao

**Restrição da diretoria:** nenhum comportamento de negócio existente pode ser quebrado, e a CI precisa rodar os
testes unitários sem banco de dados nem servidor SMTP.

---

## 1. Diagnóstico do débito técnico

As linhas referem-se ao código legado do commit `87ef839` (`SubscriptionBillingService.java`). Os itens estão
ordenados do mais grave para o menos grave.

| # | Trecho/linhas | Smell/princípio | "Juros" (manutenibilidade + CI/CD) | Tratado em |
|---|---|---|---|---|
| 1 | L18–105 `processMonthlyBilling` | Long Method / God Method; violação do SRP | Cálculo, I/O, pagamento, e-mail e log no mesmo método (CCN 18, NLOC 70). Mudar um desconto exige mexer no método que grava no banco. Não dá para testar uma regra isolada, só o fluxo inteiro. Mudanças paralelas geram conflitos de merge. | Commit 3 (I/O sai do método) e commit 4 (`PricingCalculator` + orquestrador ≤ 25 linhas) |
| 2 | L59–63, L82–87, L89–95 | Dependência direta de I/O; violação do DIP | `DriverManager` e `new SimpleEmail()` são instanciados dentro da regra de negócio. A CI não executa nenhum teste sem Postgres e SMTP. Trocar o banco ou o provedor de e-mail exige editar a regra de preço. | Commit 3 (portas + adaptadores) |
| 3 | L25 | Acoplamento temporal (`LocalDate.now()` estático) | O resultado depende do dia da execução: BLACKFRIDAY só funciona em novembro e a fidelidade muda com o calendário. Os testes ficam não determinísticos ("flaky" na esteira) e bugs de borda não se reproduzem. | Commit 3 (`Clock` injetado) |
| 4 | L100–102 | Erro mascarado (`catch (Exception e)`) | Falhas de infraestrutura e bugs de programação (NPE) viram uma linha de log, sem exceção nem alerta. Se o INSERT falhar, o log registra "Pagamento processado com sucesso" seguido do erro: pagamento sem fatura, em silêncio. | Commit 3 |
| 5 | L18–20 | Long Parameter List + Primitive Obsession | 8 parâmetros, 4 deles `String` (id, plano, cupom, token): trocar a ordem compila sem erro. Dinheiro como `double`, data como `String`, ONG como flag. Cada novo parâmetro quebra todos os chamadores. | Commit 2 (`BillingCommand`, `Money`) |
| 6 | L29–57, L77, L85, L94 | Imprecisão financeira (`double` para dinheiro, `setDouble`) | Log e e-mail exibem `R$ 9.690000000000001`. Meio centavo pode arredondar para o lado errado (caso C25: o valor exato 8,585 vira `8.584999999999999`). Diferenças de centavo na conciliação contábil. | Commit 2 (`Money` com `BigDecimal`) |
| 7 | L14–16, L90–92 | Credenciais e configuração hardcoded | A senha do banco fica no histórico do git. Não há dev/homologação/produção sem recompilar (viola o fator III do 12-factor). Trocar a senha exige um novo deploy. | Commit 3 (variáveis de ambiente) |
| 8 | L96–98 | Código morto + log enganoso | `send()` está comentado, mas o log afirma "E-mail disparado". A observabilidade mente: o suporte acredita que o cliente foi notificado. | Documentado; comportamento preservado (o commit 3 move para o adaptador com TODO) |
| 9 | L74–80 | Gateway de pagamento simulado dentro do serviço | A regra `token.startsWith("tok_")` faz papel de cobrança real. Integrar um gateway de verdade exige reescrever o método principal, e não há como simular recusa, timeout ou estorno. | Commit 3 (porta `PaymentGateway`) |
| 10 | L31–51 | Magic Numbers | 0.15, 0.10, 0.70, 30.0, 0.90, 0.02, 0.001, 12, 24 sem nome. Mudar um percentual exige caçar literais, com risco de alterar o número errado. A regra de negócio não fica documentada no código. | Commit 4 (constantes nomeadas no `PricingCalculator`) |
| 11 | L61–63, L83–87 | Vazamento de recursos | `PreparedStatement` e `ResultSet` ficam fora de try-with-resources. Sob carga, esgotam cursores e conexões, com falhas intermitentes difíceis de reproduzir. | Commit 3 (try-with-resources nos adaptadores) |
| 12 | L18 (`planType`), L65 (sem `else`) | Speculative Generality + falha silenciosa | `planType` nunca é usado: os chamadores enviam um dado ignorado. Cliente inexistente devolve lista vazia, sem log, indistinguível de "nada a fazer"; erro de cadastro passa despercebido. | Documentado; comportamento preservado |

---

## 2. Métricas antes vs. depois

Ferramenta: [`lizard`](https://github.com/terryyin/lizard) 1.24.1, comando `lizard -l java src/main/java`.
A coluna "antes" foi medida no commit `87ef839`; a coluna "depois" deve ser medida com a mesma ferramenta, versão e
critérios ao final do commit 4.

| Métrica | Antes | Depois |
|---|---|---|
| CCN (complexidade ciclomática) do método principal | 18 (acima do limite de alerta do lizard, 15) | _a preencher_ |
| NLOC do método principal (linhas de código sem brancos/comentários) | 70 | _a preencher_ |
| Linhas físicas do método principal | 88 (L18–105) | _a preencher_ |
| Tokens do método principal | 545 | _a preencher_ |
| Nº de parâmetros do método principal | 8 | _a preencher_ |
| Dependências concretas de infraestrutura no serviço | 3 (JDBC/Postgres, SMTP, relógio do sistema) | _a preencher_ |
| Configuração hardcoded no código | 6 valores (URL/usuário/senha do banco; host/porta/remetente SMTP) | _a preencher_ |
| Nº de classes | 1 | _a preencher_ |
| Nº de testes | 0 | _a preencher_ |
| Cobertura de testes | 0% | _a preencher_ |
| Testes rodam sem infraestrutura externa? | Não (o cálculo só é alcançável pelo método que abre a conexão) | _a preencher_ |

---

## 3. Decisões de refatoração

### Commit 2 — Value Objects e remoção da Primitive Obsession

Operações do catálogo: **Replace Primitive with Object** (`double` → `Money`), **Introduce Parameter Object**
(8 parâmetros → `BillingCommand`) e **Change Function Declaration** pela mecânica de migração (a assinatura antiga
continua existindo e delega para a nova).

- **`Money`** (`com.saas.billing.domain`): `record` imutável sobre `BigDecimal`. Fábricas `of(String)`,
  `of(double)` (via `BigDecimal.valueOf`) e `zero()`; operações `plus`, `minus`, `times(BigDecimal)`, `isNegative`,
  `floorAtZero`. A igualdade é por valor numérico (`100` == `100.00`).
- **Arredondamento:** os cálculos intermediários mantêm precisão total. O arredondamento acontece **uma única vez**,
  em `Money.rounded()` (2 casas, `RoundingMode.HALF_UP`, padrão comercial no Brasil), sobre o valor final, depois
  do piso zero e antes de cobrar, logar e gravar. HALF_EVEN foi descartado: diverge mais do legado e não é o padrão
  comercial.
- **`BillingCommand`** (`com.saas.billing.domain`): `record` com os 8 campos da entrada. `planType` foi mantido por
  compatibilidade, mas não é usado em nenhuma regra (smell #12).
- **Assinatura antiga** `processMonthlyBilling(String, String, double, String, int, String, boolean, String)`:
  mantida como `@Deprecated`. Converte os argumentos e delega para `processMonthlyBilling(BillingCommand)`. O
  `LocalDate.parse` ficou nesse adaptador, então uma data inválida continua lançando `DateTimeParseException` como
  antes.
- **`percentOf` e `max` não foram criados no `Money`:** `times(BigDecimal)` cobre todos os usos, e criar API sem
  uso seria Speculative Generality (smell #12).
- **INSERT:** `setDouble` virou `setBigDecimal` com o valor já arredondado.

**Mudanças de saída (correção de imprecisão, não de regra):**

1. O formato do valor no log e no e-mail passa a ter sempre 2 casas: `R$ 100.0` → `R$ 100.00` e
   `R$ 9.690000000000001` → `R$ 9.69`.
2. Em empates exatos de meio centavo, o valor cobrado pode subir 1 centavo em relação ao `double` arredondado
   (caso C25: 8,58 → **8,59**). O valor matematicamente correto é 8,585; o `double` chega a `8.584999999999999`
   por erro de ponto flutuante. Uma varredura de 2,16 milhões de combinações (seção 4) encontrou essa divergência
   em 0,29% delas, sempre de 1 centavo e sempre nesse tipo de empate.

**Verificação:** o legado (`87ef839`) e o commit 2 foram executados lado a lado contra um driver JDBC falso (script
descartável, fora do repositório), em 27 cenários de preço e de fluxo. Logs, status das faturas e valores com
2 casas ficaram idênticos, exceto o C25, como previsto. Os cenários de BLACKFRIDAY em novembro só passam a ser
verificáveis com o `Clock` injetado (commit 3).

### Commit 3 — Portas de infraestrutura e injeção de dependências

Operações do catálogo: **Extract Interface** (as 4 portas), **Move Function** (SQL, regra do token e montagem do
e-mail para os adaptadores), **Replace Constructor with Factory Function** (`BillingServiceFactory`),
**Parameterize Function** (`Clock` em vez de `LocalDate.now()`) e **Replace Primitive with Object** (status da
fatura vira o enum `InvoiceStatus`).

Arquitetura hexagonal (portas e adaptadores):

| Pacote | Classes | Responsabilidade |
|---|---|---|
| `com.saas.billing.port` | `CustomerRepository`, `InvoiceRepository`, `PaymentGateway`, `NotificationService`, `BillingInfrastructureException` | Contratos que o serviço usa; não conhecem JDBC nem SMTP |
| `com.saas.billing.adapter` | `JdbcCustomerRepository`, `JdbcInvoiceRepository`, `SimulatedCardPaymentGateway`, `SmtpNotificationService` | Implementações de produção das portas |
| `com.saas.billing` | `SubscriptionBillingService`, `BillingServiceFactory` | Serviço (recebe as portas e o `Clock` pelo construtor) e composição de produção |

- **Porta extra `InvoiceRepository`:** a especificação cita três portas, mas o legado também grava em `invoices`.
  Colocar esse INSERT no `CustomerRepository` misturaria duas tabelas e duas responsabilidades na mesma porta
  (SRP). Cada porta corresponde a uma tabela ou serviço externo. Como o legado não usava transação explícita
  (autocommit), usar conexões separadas para o SELECT e o INSERT não muda o resultado.
- **`PaymentGateway.charge` devolve `boolean`:** é a única informação que o fluxo usa (PAID/FAILED). A regra do
  legado (`token != null && token.startsWith("tok_")`) foi para o `SimulatedCardPaymentGateway`.
- **`Clock` injetado:** o serviço usa `LocalDate.now(clock)`. Em produção, a fábrica passa
  `Clock.systemDefaultZone()`; nos testes, `Clock.fixed(...)` torna BLACKFRIDAY e fidelidade determinísticos.
- **Credenciais fora do código:** a `BillingServiceFactory` lê `BILLING_DB_URL`, `BILLING_DB_USER` e
  `BILLING_DB_PASSWORD` (obrigatórias; se faltar alguma, falha logo na criação) e `BILLING_SMTP_HOST`,
  `BILLING_SMTP_PORT` e `BILLING_SMTP_FROM` (opcionais, com os valores do legado como padrão, pois não são
  segredos).
- **Remoção do `catch (Exception e)`:** os adaptadores traduzem as exceções técnicas (`SQLException`,
  `EmailException`) em `BillingInfrastructureException` (não checada), que copia a mensagem da causa e a mantém
  encadeada. O serviço captura **apenas** essa exceção e registra o mesmo log de antes
  (`Erro fatal no processamento: <msg>`). Bugs de programação (NPE etc.) deixam de ser engolidos. A alternativa,
  propagar tudo, foi descartada porque mudaria a saída de negócio em falhas de infraestrutura.
- **E-mail nulo:** no legado, um cliente sem e-mail gerava uma NPE dentro do `commons-email`, engolida pelo
  `catch` genérico. Para manter o mesmo fluxo de negócio (pagamento, fatura gravada, linha de erro), o
  `SmtpNotificationService` passou a verificar o nulo e lançar `BillingInfrastructureException`. Só o texto técnico
  da mensagem muda: `e-mail do destinatário ausente`.
- **`send()` continua desativado:** foi movido para o `SmtpNotificationService` com um TODO. Ativá-lo mudaria o
  comportamento, porque passaria a exigir um SMTP real (smell #8, preservado e documentado).
- **Construtor sem argumentos removido:** `new SubscriptionBillingService()` dá lugar a
  `BillingServiceFactory.create()`. Manter o construtor vazio obrigaria o serviço a conhecer os adaptadores, o que
  viola o DIP. A assinatura de 8 parâmetros do método continua disponível, como `@Deprecated`.
- **Try-with-resources:** `Connection`, `PreparedStatement` e `ResultSet` são fechados em todos os adaptadores,
  e o INSERT usa `setBigDecimal`.
- **Fora do escopo deste commit:** o cálculo de preço continua dentro do serviço, e o método principal ainda passa
  de 25 linhas. A extração do `PricingCalculator` e a redução do orquestrador são o commit 4.

**Verificação:** o legado e o commit 3 foram executados contra o mesmo JDBC falso, com os adaptadores reais e o
relógio fixado no mesmo dia, em 35 cenários de preço e de fluxo (incluindo falha de conexão, falha no INSERT,
e-mail inválido e e-mail nulo). Os resultados são idênticos, com os valores normalizados para 2 casas, exceto o
C25 (correção de imprecisão, commit 2) e o texto da mensagem do e-mail nulo. Com `Clock.fixed(2026-11-15)`, os
23 cenários de preço conferem com a coluna **Money HALF_UP** da seção 4.1, incluindo BLACKFRIDAY, o piso zero e o
C22.

---

## 4. Casos de caracterização

### 4.1 Preço

Gerados por [`docs/casos-esperados.jsh`](docs/casos-esperados.jsh) (`jshell -q docs/casos-esperados.jsh`), que
reproduz a fórmula do legado linha a linha em `double`, com "hoje" fixo. A coluna **Money HALF_UP** é o valor
esperado a partir do commit 2.

- `double legado`: valor bruto que o legado cobra, loga e grava.
- `exato`: a mesma fórmula em `BigDecimal`, sem arredondar.
- `legado arred.`: o `double` arredondado a 2 casas com HALF_UP.

| ID | Cenário | base | signup | hoje | atraso | cupom | ONG | double legado | exato | legado arred. | **Money HALF_UP** | Money HALF_EVEN | obs |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| C01 | cliente novo, sem nada | 100.0 | 2026-11-01 | 2026-11-15 | 0 | null | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C02 | fidelidade 13 meses (-10%) | 100.0 | 2025-10-15 | 2026-11-15 | 0 | null | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C03 | borda: 12 meses exatos (sem desconto) | 100.0 | 2025-11-15 | 2026-11-15 | 0 | null | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C04 | borda: 24 meses exatos (-10%) | 100.0 | 2024-11-15 | 2026-11-15 | 0 | null | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C05 | fidelidade 25 meses (-15%) | 100.0 | 2024-10-15 | 2026-11-15 | 0 | null | false | `85.0` | 85 | 85.00 | 85.00 | 85.00 |  |
| C06 | fidelidade 25 meses, base 11,40 (imprecisão do double) | 11.4 | 2024-10-15 | 2026-11-15 | 0 | null | false | `9.690000000000001` | 9.69 | 9.69 | 9.69 | 9.69 |  |
| C07 | ONG (-30%) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | null | true | `70.0` | 70 | 70.00 | 70.00 | 70.00 |  |
| C08 | WELCOME10 válido (0 meses) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "WELCOME10" | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C09 | borda: WELCOME10 com 1 mês (válido) | 100.0 | 2026-10-31 | 2026-11-15 | 0 | "WELCOME10" | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C10 | WELCOME10 inválido (2 meses) | 100.0 | 2026-09-15 | 2026-11-15 | 0 | "WELCOME10" | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C11 | BLACKFRIDAY em novembro (-R$30) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "BLACKFRIDAY" | false | `70.0` | 70 | 70.00 | 70.00 | 70.00 |  |
| C12 | BLACKFRIDAY fora de novembro | 100.0 | 2026-10-01 | 2026-10-15 | 0 | "BLACKFRIDAY" | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C13 | cupom minúsculo blackfriday (nov) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "blackfriday" | false | `70.0` | 70 | 70.00 | 70.00 | 70.00 |  |
| C14 | cupom minúsculo welcome10 | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "welcome10" | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C15 | cupom com espaço ' BLACKFRIDAY' (não aplica) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | " BLACKFRIDAY" | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C16 | cupom só com espaços (ignorado) | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "   " | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C17 | cupom desconhecido | 100.0 | 2026-11-01 | 2026-11-15 | 0 | "XPTO" | false | `100.0` | 100 | 100.00 | 100.00 | 100.00 |  |
| C18 | atraso 10 dias (+2% +0,1%/dia) | 100.0 | 2026-11-01 | 2026-11-15 | 10 | null | false | `103.0` | 103 | 103.00 | 103.00 | 103.00 |  |
| C19 | BLACKFRIDAY deixa negativo -> piso 0 | 20.0 | 2026-11-01 | 2026-11-15 | 0 | "BLACKFRIDAY" | false | `0.0` | 0 | 0.00 | 0.00 | 0.00 |  |
| C20 | BLACKFRIDAY negativo + atraso 5 (multa sobre negativo) -> 0 | 20.0 | 2026-11-01 | 2026-11-15 | 5 | "BLACKFRIDAY" | false | `0.0` | 0 | 0.00 | 0.00 | 0.00 |  |
| C21 | ONG + fidelidade 25m + atraso 7 dias | 99.9 | 2024-10-15 | 2026-11-15 | 7 | null | true | `61.045393499999996` | 61.0453935 | 61.05 | 61.05 | 61.05 |  |
| C22 | ONG + fidelidade 13m + BLACKFRIDAY (nov) | 49.9 | 2025-10-01 | 2026-11-15 | 0 | "BLACKFRIDAY" | true | `1.436999999999994` | 1.437 | 1.44 | 1.44 | 1.44 |  |
| C23 | fidelidade 13m + WELCOME10 (não aplica: >1 mês) | 100.0 | 2025-10-15 | 2026-11-15 | 0 | "WELCOME10" | false | `90.0` | 90 | 90.00 | 90.00 | 90.00 |  |
| C24 | ONG + WELCOME10 + atraso 3 dias | 59.9 | 2026-11-10 | 2026-11-15 | 3 | "WELCOME10" | true | `38.604951` | 38.604951 | 38.60 | 38.60 | 38.60 |  |
| C25 | meio centavo exato: fidelidade 25m, base 10,10 | 10.1 | 2024-10-15 | 2026-11-15 | 0 | null | false | `8.584999999999999` | 8.585 | 8.58 | 8.59 | 8.58 | **+1 centavo vs. legado arred. (empate de meio centavo)** |

**Varredura:** bases de R$ 0,01 a R$ 300,00 × 3 tempos de casa (0, 13 e 25 meses) × 3 cupons (nenhum,
BLACKFRIDAY, WELCOME10) × ONG sim/não × atraso de 0/1/7/30 dias, com hoje = 2026-11-15, totalizando 2.160.000
combinações. Diferença de 1 centavo entre o `double` arredondado e o exato arredondado: HALF_UP em **6.249 (0,29%)**
e HALF_EVEN em 13.668 (0,63%).

Bordas e regras confirmadas:

- **Meses de casa:** calculados só por ano e mês, ignorando o dia. Por isso, de 31/10 a 15/11 conta 1 mês (C09).
- **Desconto de fidelidade:** com 12 meses exatos não há desconto (C03); com 24 meses exatos o desconto é de 10%,
  não 15% (C04).
- **Cupom:** a comparação ignora maiúsculas/minúsculas (C13, C14). Já `" BLACKFRIDAY"` com espaço **não** aplica
  (C15), porque o `trim()` só é usado para testar se o cupom está vazio. Comportamento preservado.
- **Atraso com valor negativo:** a multa e os juros são calculados sobre o valor já negativo, antes do piso (C20).

### 4.2 Fluxos (logs e fatura)

Exemplo com o cliente `c1`, e-mail `a@x.com` e base de R$ 100,00:

| Fluxo | Logs no legado | Logs a partir do commit 2 | Fatura |
|---|---|---|---|
| Cliente inexistente | `[]` | `[]` | não grava |
| Status diferente de `ACTIVE` (sem diferenciar maiúsculas) | `Cliente inativo. Cobrança cancelada: c1` | idem | não grava |
| Token `tok_...` | `Pagamento processado com sucesso: R$ 100.0`, `E-mail disparado para: a@x.com` | `... R$ 100.00`, `E-mail disparado para: a@x.com` | `PAID` |
| Token inválido ou nulo | `Falha no pagamento do cartão para: c1`, `E-mail disparado para: a@x.com` | idem | `FAILED` (gravada mesmo com falha) |
| Falha no INSERT | `Pagamento processado com sucesso: R$ 100.0`, `Erro fatal no processamento: <msg>` | `... R$ 100.00`, `Erro fatal no processamento: <msg>` | não grava |
| Falha de conexão com o banco | `Erro fatal no processamento: <msg>` | idem | não grava |
| E-mail inválido no cadastro | `Pagamento processado com sucesso: R$ 100.0`, `Erro fatal no processamento: <msg do commons-email>` | `... R$ 100.00`, idem | `PAID` (gravada antes do e-mail) |
| E-mail nulo no cadastro | `Pagamento processado com sucesso: R$ 100.0`, `Erro fatal no processamento: Cannot invoke "String.length()"...` (NPE engolida) | `... R$ 100.00`, `Erro fatal no processamento: e-mail do destinatário ausente` (a partir do commit 3) | `PAID` (gravada antes do e-mail) |
| Data de adesão inválida | lança `DateTimeParseException`, sem logs | idem | não grava |

Observação: o e-mail é montado, mas `send()` está comentado, e o log afirma o envio mesmo assim (smell #8).
