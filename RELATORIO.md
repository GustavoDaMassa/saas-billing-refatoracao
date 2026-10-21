# Relatório Técnico — Refatoração do SaaS Billing Co.

**Disciplina:** Implantação e Entrega de Software — UFG/INF 2026/2 (Profª Sofia Costa Paiva)

**Dupla:**

- Gustavo Henrique — [@GustavoDaMassa](https://github.com/GustavoDaMassa)
- Caio Barbosa Dias — [@CaioxDias](https://github.com/CaioxDias)

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
| 2 | L59–63, L82–87, L89–95 | Dependência direta de I/O; violação do DIP | `DriverManager` e `new SimpleEmail()` são instanciados dentro da regra de negócio. O fluxo principal acessa PostgreSQL e instancia diretamente a biblioteca de e-mail, dificultando testes isolados. O envio SMTP já estava desativado no legado. Trocar o banco ou o provedor de e-mail exige editar a regra de preço. | Commit 3 (portas + adaptadores) |
| 3 | L25 | Acoplamento temporal (`LocalDate.now()` estático) | O resultado depende do dia da execução: BLACKFRIDAY só funciona em novembro e a fidelidade muda com o calendário. Os testes ficam não determinísticos ("flaky" na esteira) e bugs de borda não se reproduzem. | Commit 3 (`Clock` injetado) |
| 4 | L100–102 | Erro mascarado (`catch (Exception e)`) | Falhas de infraestrutura e bugs de programação (NPE) viram uma linha de log, sem exceção nem alerta. Se o INSERT falhar, o log registra "Pagamento processado com sucesso" seguido do erro: pagamento sem fatura, em silêncio. | Commit 3 |
| 5 | L18–20 | Long Parameter List + Primitive Obsession | 8 parâmetros, 5 deles `String` (id, plano, data, cupom, token): trocar a ordem compila sem erro. Dinheiro como `double`, data como `String`, ONG como flag. Cada novo parâmetro quebra todos os chamadores. | Commit 2 (`BillingCommand`, `Money`) |
| 6 | L29–57, L77, L85, L94 | Imprecisão financeira (`double` para dinheiro, `setDouble`) | Log e e-mail exibem `R$ 9.690000000000001`. Meio centavo pode arredondar para o lado errado (caso C25: o valor exato 8,585 vira `8.584999999999999`). Diferenças de centavo na conciliação contábil. | Commit 2 (`Money` com `BigDecimal`) |
| 7 | L14–16, L90–92 | Credenciais e configuração hardcoded | A senha do banco fica no histórico do git. Não há dev/homologação/produção sem recompilar (viola o fator III do 12-factor). Trocar a senha exige um novo deploy. | Commit 3 (variáveis de ambiente) |
| 8 | L96–98 | Código morto + log enganoso | `send()` está comentado, mas o log afirma "E-mail disparado". A observabilidade mente: o suporte acredita que o cliente foi notificado. | Documentado; comportamento preservado (o commit 3 move para o adaptador com TODO) |
| 9 | L74–80 | Gateway de pagamento simulado dentro do serviço | A regra `token.startsWith("tok_")` faz papel de cobrança real. Integrar um gateway de verdade exige reescrever o método principal, e não há como simular recusa, timeout ou estorno. | Commit 3 (porta `PaymentGateway`) |
| 10 | L31–51 | Magic Numbers | 0.15, 0.10, 0.70, 30.0, 0.90, 0.02, 0.001, 12, 24 sem nome. Mudar um percentual exige caçar literais, com risco de alterar o número errado. A regra de negócio não fica documentada no código. | Commit 4 (constantes nomeadas no `PricingCalculator`) |
| 11 | L61–63, L83–87 | Vazamento de recursos | `PreparedStatement` e `ResultSet` ficam fora de try-with-resources. Sob carga, esgotam cursores e conexões, com falhas intermitentes difíceis de reproduzir. | Commit 3 (try-with-resources nos adaptadores) |
| 12 | L18 (`planType`), L65 (sem `else`) | Speculative Generality + falha silenciosa | `planType` nunca é usado: os chamadores enviam um dado ignorado. Cliente inexistente devolve lista vazia, sem log, indistinguível de "nada a fazer"; erro de cadastro passa despercebido. | Documentado; comportamento preservado |

---

## 2. Métricas antes vs. depois

Análise estática: **Lizard 1.24.1**, comando `lizard -l java src/main/java`.
Os valores anteriores são os registrados na análise do commit `87ef839`.
Os valores posteriores foram medidos na implementação local de Caio em 21/10/2026,
preparada para o commit 4. A execução local registrou 55 testes aprovados.

A comparação considera o método original de oito parâmetros e o novo orquestrador
`processMonthlyBilling(BillingCommand)`. A assinatura antiga permanece como adaptador
marcado com `@Deprecated`.

| Métrica | Antes | Depois |
|---|---|---|
| Complexidade ciclomática (CCN) do método principal | 18 | 4 |
| NLOC do método principal, sem brancos e comentários | 70 | 19 |
| Linhas físicas do método principal | 88 | 19 |
| Tokens do método principal | 545 | 145 |
| Parâmetros do método principal | 8 | 1 |
| Dependências concretas de infraestrutura no serviço | JDBC, biblioteca de e-mail e relógio estático | Nenhuma implementação concreta de I/O; quatro portas e Clock recebidos pelo construtor |
| Configuração de infraestrutura | 6 valores fixos no código | Banco por variáveis de ambiente; SMTP configurável com 3 valores padrão na fábrica |
| Tipos de produção declarados | 1 classe | 16 tipos: 8 classes, 3 records, 1 enum e 4 interfaces |
| Testes automatizados | 0 | 55 aprovados, sem falhas, erros ou ignorados |
| Cobertura de linhas do projeto | Sem suíte automatizada; não medida com JaCoCo | 61,49% (99/161) |
| Cobertura de ramificações do projeto | Sem suíte automatizada; não medida com JaCoCo | 68,97% (40/58) |
| Testes sem banco e servidor SMTP | Sem suíte; fluxo acoplado ao banco | Sim, com substitutos em memória e gateway simulado |

### 2.1 Cobertura por componente

Medição com **JaCoCo 0.8.15**, conforme CSV gerado localmente em 21/10/2026.
Os totais incluem a fábrica e os adaptadores de produção, sem exclusões.

| Componente | Linhas cobertas/total | Cobertura de linhas | Ramificações cobertas/total | Cobertura de ramificações |
|---|---|---|---|---|
| BillingCommand | 1/1 | 100% | 0/0 | Não se aplica |
| Customer | 1/1 | 100% | 0/0 | Não se aplica |
| Money | 16/16 | 100% | 8/8 | 100% |
| PricingCalculator | 36/36 | 100% | 20/20 | 100% |
| InvoiceStatus | 3/3 | 100% | 0/0 | Não se aplica |
| BillingServiceFactory | 0/16 | 0% | 0/8 | 0% |
| SubscriptionBillingService | 38/40 | 95% | 8/8 | 100% |
| SmtpNotificationService | 0/18 | 0% | 0/2 | 0% |
| JdbcInvoiceRepository | 0/13 | 0% | 0/0 | Não se aplica |
| SimulatedCardPaymentGateway | 2/2 | 100% | 4/4 | 100% |
| JdbcCustomerRepository | 0/13 | 0% | 0/8 | 0% |
| BillingInfrastructureException | 2/2 | 100% | 0/0 | Não se aplica |
| **Total** | **99/161** | **61,49%** | **40/58** | **68,97%** |

A suíte verifica o domínio e a orquestração por meio de substitutos das portas.
Não valida a integração real com PostgreSQL nem o adaptador SMTP. Cobertura indica
código exercitado; não prova ausência de defeitos nem equivalência absoluta com o legado.

### 2.2 Critérios e limitações

- As linhas físicas incluem assinatura e chave final. O orquestrador possui 19 linhas,
  atendendo ao limite de 25 da atividade.
- O Lizard analisou 16 arquivos, mas não contabilizou os métodos internos do record Money.
  Suas médias gerais de funções não representam integralmente todos os métodos do projeto.
- A contagem de tipos distingue classes, records, enum e interfaces; não equivale a
  declarar que existem 16 classes comuns.
- A ausência de alertas do Lizard não é, por si só, comprovação do limite de 25 linhas.
- As métricas do JaCoCo e o NLOC do Lizard têm critérios distintos e não são intercambiáveis.

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
  no valor usado pelo fluxo, em `Money.rounded()` (2 casas, `RoundingMode.HALF_UP`), depois
  do piso zero e antes de cobrar e gravar. `toString()` também formata com arredondamento para duas
  casas, sem modificar o objeto. HALF_UP foi a política escolhida pela dupla; a atividade não
  determina explicitamente esse modo. A comparação exploratória com HALF_EVEN está na seção 4.
- **`BillingCommand`** (`com.saas.billing.domain`): `record` com os 8 campos da entrada. `planType` foi mantido por
  compatibilidade, mas não é usado em nenhuma regra (smell #12).
- **Assinatura antiga** `processMonthlyBilling(String, String, double, String, int, String, boolean, String)`:
  mantida como `@Deprecated`. Converte os argumentos e delega para `processMonthlyBilling(BillingCommand)`. O
  `LocalDate.parse` ficou nesse adaptador, então uma data inválida continua lançando `DateTimeParseException` como
  antes.
- **`percentOf` e `max` não foram criados no `Money`:** `times(BigDecimal)` cobre todos os usos, e criar API sem
  uso seria Speculative Generality (smell #12).
- **INSERT:** `setDouble` virou `setBigDecimal` com o valor já arredondado.

**Mudanças observáveis de saída e ressalva de compatibilidade:**

1. O formato do valor no log e no e-mail passa a ter sempre 2 casas: `R$ 100.0` → `R$ 100.00` e
   `R$ 9.690000000000001` → `R$ 9.69`.
2. Em empates exatos de meio centavo, o valor cobrado pode subir 1 centavo em relação ao `double` arredondado
   (caso C25: 8,58 → **8,59**). O valor matematicamente correto é 8,585; o `double` chega a `8.584999999999999`
   por erro de ponto flutuante. Uma varredura de 2,16 milhões de combinações (seção 4) encontrou essa divergência
   em 0,29% delas, sempre de 1 centavo e sempre nesse tipo de empate.

**Verificação histórica relatada na Parte 1 (script não versionado):** o legado (`87ef839`) e o commit 2 foram executados lado a lado contra um driver JDBC falso (script
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
  (autocommit), a separação preserva o fluxo nominal descrito. Entretanto, abrir uma segunda conexão
  introduz um ponto adicional de falha e não equivale a garantir identidade de comportamento em todas as falhas de infraestrutura.
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
  `BillingServiceFactory.create()`. A composição de produção foi centralizada na fábrica para manter o serviço desacoplado
  dos adaptadores. Essa alteração exige migrar os chamadores que usavam o construtor vazio. A assinatura de 8 parâmetros do método continua disponível, como `@Deprecated`.
- **Try-with-resources:** `Connection`, `PreparedStatement` e `ResultSet` são fechados em todos os adaptadores,
  e o INSERT usa `setBigDecimal`.
- **Fora do escopo deste commit:** o cálculo de preço continua dentro do serviço, e o método principal ainda passa
  de 25 linhas. A extração do `PricingCalculator` e a redução do orquestrador são o commit 4.

**Verificação histórica relatada na Parte 1 (script não versionado):** o legado e o commit 3 foram executados contra o mesmo JDBC falso, com os adaptadores reais e o
relógio fixado no mesmo dia, em 35 cenários de preço e de fluxo (incluindo falha de conexão, falha no INSERT,
e-mail inválido e e-mail nulo). Os resultados são idênticos, com os valores normalizados para 2 casas, exceto o
C25 (diferença de arredondamento, commit 2) e o texto da mensagem do e-mail nulo. Com `Clock.fixed(2026-11-15)`, os
23 cenários de preço conferem com a coluna **Money HALF_UP** da seção 4.1, incluindo BLACKFRIDAY, o piso zero e o
C22.

### Commit 4 — Extração da precificação e validação automatizada

**Responsável:** Caio Barbosa Dias. Implementação registrada no commit
`628477d80dd2ba98efc240baa5a2489ffda2299f`, publicada na branch
`refactor/caio-pricing-tests` e submetida pelo Pull Request #1.
A CI do pull request foi concluída com sucesso em 21/10/2026.

Operações aplicadas: **Extract Class**, **Extract Method** e **Replace Magic Literal**.

- **Extract Class:** as regras matemáticas saíram do serviço e passaram a compor
  `PricingCalculator`, classe pura que recebe `BillingCommand` e `LocalDate` e devolve `Money`.
  Não consulta banco, rede, e-mail ou relógio do sistema.
- **Extract Method:** cálculo dos meses, fidelidade, ONG, cupom e encargos por atraso foram
  separados em métodos. O serviço também separa a cobrança e o registro do resultado do pagamento.
- **Replace Magic Literal:** percentuais, limites de meses, cupons e desconto fixo receberam
  constantes nomeadas.
- O orquestrador `processMonthlyBilling(BillingCommand)` possui 19 linhas físicas e CCN 4.
- A ordem permanece: fidelidade → ONG → cupom → atraso → piso zero → arredondamento.
- O cálculo continua antes da consulta ao cliente e fora do bloco de captura de infraestrutura.
- O construtor com quatro portas e Clock e o método antigo foram preservados nesta etapa.
- Pagamentos recusados continuam gerando fatura FAILED e chamada à porta de notificação.
- Falhas de infraestrutura mantêm os logs anteriores; erros de programação não são capturados genericamente.

#### Suíte automatizada

| Classe de teste | Casos executados |
|---|---:|
| MoneyTest | 10 |
| PricingCalculatorTest | 25 |
| SubscriptionBillingServiceTest | 6 |
| SubscriptionBillingServiceEdgeCasesTest | 14 |
| **Total** | **55** |

Resultado local informado pelo Maven Surefire: **55 testes, zero falhas, zero erros e
zero ignorados**, com BUILD SUCCESS.

Os testes cobrem operações monetárias, imutabilidade, igualdade numérica, HALF_UP,
os 25 cenários de preço, cliente inexistente/inativo, tokens, aprovação/recusa,
faturas PAID/FAILED, sequência de I/O, falhas nas portas, propagação de erros de
programação e data inválida na assinatura antiga. Um caso verifica que um erro no
cálculo ocorre antes de qualquer consulta ao cliente.

Os testes do serviço usam `Clock.fixed` e implementações falsas das portas, com
registros em memória. Os testes de tokens exercitam `SimulatedCardPaymentGateway`,
que apenas verifica o prefixo e não efetua cobrança externa.

#### Integração contínua

O arquivo `.github/workflows/ci.yml` foi preparado para executar em pushes e pull
requests, com Java 17, sem serviços PostgreSQL ou SMTP. O comando configurado é:

```bash
mvn --batch-mode --no-transfer-progress -DfailIfNoTests=true clean verify
```

A preparação do ambiente pode baixar ferramentas e dependências. A independência
exigida refere-se à ausência de banco e SMTP necessários à execução dos testes.
A execução remota da CI foi concluída com sucesso no GitHub Actions, com Java 17.
Evidência: https://github.com/GustavoDaMassa/saas-billing-refatoracao/actions/runs/39672870045

#### Limites da preservação de comportamento

O commit 4 preserva a política monetária introduzida no commit 2. Os 25 casos usam
como referência a coluna **Money HALF_UP**, não o valor bruto em double do legado.

O arredondamento antes da cobrança e persistência representa uma alteração observável:
valores com frações de centavo passam a ter duas casas. O C25 evidencia diferença
de um centavo em relação ao double legado já normalizado para duas casas. Portanto,
não se afirma equivalência absoluta com o legado. A escolha deve ser considerada
na avaliação da restrição de preservação de comportamento.

A assinatura antiga mantém a chamada do método, mas a remoção anterior do construtor
vazio impede afirmar compatibilidade completa da API. O envio real de e-mail segue
desativado; o texto de log herdado ainda afirma envio, limitação documentada na matriz.

As comparações históricas com JDBC falso descritas nos commits 2 e 3 foram relatadas
na Parte 1 e utilizaram scripts descartáveis. Não integram a suíte JUnit versionável
nem foram reexecutadas na revisão atual.

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

**Varredura exploratória registrada na Parte 1 (não reexecutada nesta etapa):** bases de R$ 0,01 a R$ 300,00 × 3 tempos de casa (0, 13 e 25 meses) × 3 cupons (nenhum,
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


---

## 5. Reprodução e evidências

### Compilação e testes

Requisitos: JDK 17 ou superior e Maven disponível no PATH. A validação local foi
realizada com JDK 21.0.7 e Maven 3.10.0, compilando com release 17.

```bash
mvn clean test
```

### Cobertura

```bash
mvn clean org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.15:report
```

Saídas: `target/site/jacoco/index.html`, `target/site/jacoco/jacoco.csv` e
`target/site/jacoco/jacoco.xml`. Os resultados podem ser regenerados e não precisam
ser versionados. O comando de cobertura é separado do comando padrão da CI.

### Métricas estáticas

Instalar Lizard 1.24.1 em ambiente Python isolado fora do repositório e executar:

```bash
lizard -l java src/main/java
```

### Origem e histórico

O arquivo original da professora, `SubscriptionBillingService-pratica.java`, foi
comparado com `SubscriptionBillingService.java` do commit `87ef839`: ambos possuem
4.430 bytes e são idênticos byte a byte. SHA-256:

```text
d5d88d96f565fbf08894b3c785fff25029214f29ccb983aa90859c708bb58c47
```

| Etapa | Identificação | Responsável |
|---|---|---|
| 1 — Código legado | `87ef839` | Gustavo |
| 2 — Value Objects | `713e59d` | Gustavo |
| 3 — Portas e injeção de dependências | `45bd102` | Gustavo |
| 4 — Precificação, testes e fechamento do relatório | `628477d` | Caio |

Mensagem do commit 4:

```text
refactor: extrai motor de precificação e adiciona suíte verde
```

**Situação em 20/10/2026:** implementação, testes, métricas e cobertura registrados.
Commit de Caio publicado e CI do Pull Request #1 concluída com sucesso.
Resta integrar o pull request à main e entregar o link do repositório no SIGAA.
