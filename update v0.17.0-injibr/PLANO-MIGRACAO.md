# Plano de Migração INJIBR — inji-verify v0.12.3 → v0.17.0

## Contexto

A branch `update/v0.17.0` foi criada a partir da upstream `v0.17.0`. Este documento
descreve todas as customizações INJIBR que existiam na `entrega` (baseada na upstream
`v0.12.3`) e como cada uma deve ser aplicada na v0.17.0.

Fonte do git diff: `entrega-vs-upstream-0.12.3.patch`
Gerado com: `git diff v0.12.3..scm/entrega`

---

## Convenção de Customização

Toda linha de código alterada ou desativada por motivo de customização INJIBR
**não deve ser deletada** — deve ser **comentada**, precedida de um comentário
explicativo com a tag `INJIBR-CUSTOM`.

**Padrão obrigatório:**
```java
// INJIBR-CUSTOM: <motivo da mudança>
// linha original comentada
nova linha ou ausência de linha
```

**Objetivo:** facilitar busca por `INJIBR-CUSTOM` para identificar todos os pontos
customizados ao fazer um novo upgrade de versão upstream.

---

## Mudanças de Arquitetura entre v0.12.3 e v0.17.0 (upstream)

| Aspecto | v0.12.3 | v0.17.0 |
|---|---|---|
| `VPSubmissionController` | aceita só `vp_token` | verificar se aceita `error`/`error_description` |
| `AuthorizationRequestResponseDto` | sem `requestUri` | verificar se tem `requestUri` (by reference flow) |
| `ErrorCode` | 4 valores | verificar se novos valores foram adicionados |
| `Constants` | sem `RESPONSE_PROCESS_URI_ROOT` | verificar se foi adicionado |
| `application.properties` | datasource URL hardcoded | verificar se parametrizado |
| `pom.xml` versão | `0.12.3` | `0.17.0` |

---

## Customizações INJIBR a Aplicar

---

### 1. `pom.xml` (raiz)

**O que muda no patch:**
- Versão de `0.12.3` → `4.1.2`
- Adicionado `<skip>true</skip>` no `maven-gpg-plugin`

**Como aplicar na v0.17.0:**
- Alterar versão de `0.17.0` para `4.1.2` (versão INJIBR padrão)
- Adicionar `<skip>true</skip>` no `maven-gpg-plugin`:

```xml
<!-- INJIBR-CUSTOM: skip GPG signing for internal Dataprev CI pipeline -->
<skip>true</skip>
```

---

### 2. `verify-service/pom.xml`

**O que muda no patch:**
- Alterar versão de `0.17.0` para `4.1.2` (versão INJIBR padrão)
- Adicionar as dependências que faltarem com `<!-- INJIBR-CUSTOM -->`.

---

### 3. `VPSubmissionController.java`

**O que muda no patch:** Adicionados 3 logs no início do método `submitVP()`:

```java
// INJIBR-CUSTOM: log VP submission parameters for debugging
log.info("vp_token: {}", vpToken);
log.info("presentation_submission: {}", presentationSubmission);
log.info("state: {}", state);
```

**Como aplicar na v0.17.0:** Verificar se a assinatura do método mudou (v0.17.0 pode aceitar `error`/`error_description`). Adicionar os logs no início do método.

---

### 4. `ErrorCode.java`

**O que muda no patch:** Adicionados 4 novos valores ao enum:

```java
// INJIBR-CUSTOM: error codes for bank webhook and PDF generation flows
NO_VP_REQUEST("NO_VP_REQUEST","No VP request found for given transaction ID."),
BANK_WEBHOOK_ERROR("BANK_WEBHOOK_ERROR","Error occurred while processing bank webhook."),
BANK_CREDENTIAL_ERROR("BANK_CREDENTIAL_ERROR","Bank credentials are invalid."),
PDF_PARSE_FAILED("PDF_PARSE_FAILED","Error occurred while parsing PDF document."),
PDF_GENERATION_FAILED("PDF_GENERATION_FAILED","Error occurred while generating PDF document."),
```

**Como aplicar na v0.17.0:** Verificar se algum desses valores já foi adicionado na upstream. Adicionar os que faltarem.

---

### 5. `Constants.java`

**O que muda no patch:** Adicionadas 2 constantes:

```java
// INJIBR-CUSTOM: VP process endpoint root for bank webhook flow
public static final String RESPONSE_PROCESS_URI_ROOT = "/vp-process";
// INJIBR-CUSTOM: status constant for failed VP processing
public static final String FAILED = "FAILED";
```

**Como aplicar na v0.17.0:** Verificar se já existem. Adicionar as que faltarem.

---

### 6. `application.properties`

**O que muda no patch:**
- URL do datasource parametrizada: `${DATABASE_NAME}` e `${DATABASE_SCHEMA}` em vez de valores hardcoded
- Adicionado `spring.profiles.active=local`
- Adicionado `mosip.openid.htmlTemplate=credential-template.html`
- Adicionadas propriedades do Banco do Brasil (`govbr.bb.*`, `mtls.*`)
- Adicionado `audit.enabled=${AUDIT_ENABLED:false}`

**Como aplicar na v0.17.0:** Verificar o estado atual do arquivo. Aplicar as propriedades novas com `# INJIBR-CUSTOM`:

```properties
# INJIBR-CUSTOM: HTML template for credential rendering
mosip.openid.htmlTemplate=credential-template.html

# INJIBR-CUSTOM: Bank of Brazil webhook integration
govbr.bb.token.base.url=https://oauth.bb.com.br
govbr.bb.token.uri=/oauth/token
govbr.bb.token.grant.type=client_credentials
govbr.bb.token.scope=webhook-mgi.requisicao
govbr.bb.token.auth=${GOVBR_BB_TOKEN_AUTH}
govbr.bb.api.key=gw-dev-app-key
govbr.bb.client.cert-path=/certs/client.crt
govbr.bb.client.key-path=/certs/client.key
govbr.bb.ca.cert-path=/certs/ca.crt
govbr.bb.webhook.uri.path=/v1/response/files

# INJIBR-CUSTOM: mTLS for bank webhook
mtls.client.keystore-path=${KEYSTORE_PATH}
mtls.client.keystore-password=${KEYSTORE_PASSWORD}
mtls.client.truststore-path=${TRUSTSTORE_PATH}
mtls.client.truststore-password=${TRUSTSTORE_PASSWORD}

# INJIBR-CUSTOM: audit toggle
audit.enabled=${AUDIT_ENABLED:false}
```

---

### 7. `application-local.properties`

**O que muda no patch:**
- Troca de H2 para PostgreSQL
- Datasource parametrizado com variáveis de ambiente
- Adicionado `mosip.openid.htmlTemplate=credential-template.html`

**Como aplicar na v0.17.0:** Verificar se a v0.17.0 já usa PostgreSQL no local. Aplicar as diferenças com `# INJIBR-CUSTOM`.

---

### 8. Novos arquivos — Auditoria de VP

**Arquivos novos — portar diretamente:**

| Arquivo | Descrição |
|---|---|
| `aspect/VpAuditAspect.java` | AOP — intercepta chamadas e registra em `vp_process_audit` |
| `config/AuditConfig.java` | `@Value("${audit.enabled:false}")` |
| `models/VpProcessAudit.java` | Entidade JPA para `vp_process_audit` |
| `repository/VpProcessAuditRepository.java` | Repository JPA |
| `services/VpProcessAuditService.java` | Interface |
| `services/impl/VpProcessAuditServiceImpl.java` | Implementação |

**DDL necessário:**
```sql
CREATE TABLE verify.vp_process_audit (
    id UUID NOT NULL,
    request_id VARCHAR(255),
    transaction_id VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_vp_process_audit PRIMARY KEY (id)
);
```

---

### 9. Novos arquivos — Webhook Banco do Brasil

**Arquivos novos — portar diretamente:**

| Arquivo | Descrição |
|---|---|
| `config/WebClientConfig.java` | Bean `WebClient` Spring |
| `controller/VPCredentialRequestController.java` | Endpoint para o banco solicitar verificação |
| `controller/VPProcessController.java` | Processa resultado e notifica o banco |
| `models/BankCredential.java` | Entidade JPA para `bank_credentials` |
| `models/VpRequest.java` | Entidade JPA para `vp_requests` |
| `repository/BankCredentialRepository.java` | Repository JPA |
| `repository/VpRequestRepository.java` | Repository JPA |
| `services/BankCredentialService.java` | Interface |
| `services/BankWebhookService.java` | Interface |
| `services/VPProcessService.java` | Interface |
| `services/VpRequestService.java` | Interface |
| `services/impl/BankCredentialServiceImpl.java` | Implementação |
| `services/impl/BankOfBrazilWebhookServiceImpl.java` | Webhook do Banco do Brasil com mTLS |
| `services/impl/VPProcessServiceImpl.java` | Processamento de VP |
| `services/impl/VpRequestServiceImpl.java` | VP requests |

**DDL necessário:**
```sql
CREATE TABLE verify.bank_credentials (
    bank_id VARCHAR(100) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    bank_secret VARCHAR(255) NOT NULL,
    bank_webhook_url VARCHAR(500),
    bank_webhook_uri VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT bank_credentials_pkey PRIMARY KEY (bank_id)
);

CREATE TABLE verify.vp_requests (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    bank_credential_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_bank_credential
        FOREIGN KEY (bank_credential_id)
        REFERENCES verify.bank_credentials (bank_id)
        ON DELETE CASCADE
);
```

---

### 10. Novos arquivos — Geração de PDF

**Arquivos novos — portar diretamente:**

| Arquivo | Descrição |
|---|---|
| `services/HtmlGeneratorService.java` | Interface para geração de HTML por tipo de VC |
| `services/PdfService.java` | Interface para geração de PDF |
| `services/VcParserService.java` | Interface para parse de VC (LDP, SD-JWT) |
| `services/impl/CAFCredentialHtmlGeneratorServiceImpl.java` | Gerador HTML para CAF |
| `services/impl/CARDocumentHtmlGeneratorServiceImpl.java` | Gerador HTML para CAR Document |
| `services/impl/CCIRCredentialHtmlGeneratorServiceImpl.java` | Gerador HTML para CCIR |
| `services/impl/CarReceiptAstHtmlGeneratorServiceImpl.java` | Gerador HTML para CAR Receipt AST/PCT |
| `services/impl/HtmlGeneratorFactory.java` | Factory — seleciona gerador por tipo de VC |
| `services/impl/HtmlGeneratorServiceImpl.java` | Implementação base |
| `services/impl/PdfServiceImpl.java` | Geração de PDF a partir de HTML (iText) |
| `services/impl/VcParserServiceImpl.java` | Parse de VC (LDP e SD-JWT) |

**Templates HTML novos — portar diretamente:**
- `resources/templates/INCRA-CCIRCredential-template.html`
- `resources/templates/MDA-CAFCredential-template.html`
- `resources/templates/MGI-CARDocument-template.html`
- `resources/templates/MGI-CARReceipt-template.html`
- `resources/templates/MGI-CARReceiptAST-template.html`
- `resources/templates/MGI-CARReceiptPCT-template.html`
- `resources/templates/credential-template.html`
- `resources/templates/logo.png`

---

### 11. Novos arquivos — Exceções

**Arquivos novos — portar diretamente:**

| Arquivo | Descrição |
|---|---|
| `exception/BankCredentialException.java` | Exceção para credenciais de banco |
| `exception/BankWebHookException.java` | Exceção para webhook de banco |
| `exception/PdfGenerationException.java` | Exceção para geração de PDF |
| `exception/PdfParseException.java` | Exceção para parse de PDF |
| `exception/VpRequestNotFoundException.java` | Exceção para VP request não encontrado |

---

### 12. Testes novos

**Arquivos novos — portar diretamente:**

| Arquivo | Descrição |
|---|---|
| `impl/CAFCredentialPdfGenerationTest.java` | Teste de geração de PDF CAF |
| `impl/CARDocumentPdfGenerationTest.java` | Teste de geração de PDF CAR Document |
| `impl/CARReceiptAstPdfGenerationTest.java` | Teste de geração de PDF CAR Receipt |
| `impl/CCIRCredentialPdfGenerationTest.java` | Teste de geração de PDF CCIR |
| `test/resources/caf-credential-sample.json` | Sample de VC CAF |
| `test/resources/car-document-credential-sample.json` | Sample de VC CAR Document |
| `test/resources/car-receipt-ast-credential-sample.json` | Sample de VC CAR Receipt |
| `test/resources/ccir-credential-sample.json` | Sample de VC CCIR |

---

### 13. Novos arquivos — Docker Compose

**Arquivos novos — portar diretamente:**
- `docker-compose/config/config.json` — configuração dos tipos de VC (CAR, CCIR, CAF) para o verify-ui
- `docker-compose/db-init/init.sql` — schema completo com tabelas customizadas

---

### 14. Arquivos de infraestrutura INJIBR

**Arquivos novos — portar diretamente:**
- `.gitattributes` — normalização de line endings
- `Jenkinsfile` — pipeline CI Dataprev
- `README.adoc` — documentação interna Dataprev
- `update_script.sh` — script de atualização de versão

---

### 15. `ui-test` — normalização CRLF→LF

Os arquivos de ui-test no patch são apenas normalização de line endings (CRLF→LF).
Verificar se a v0.17.0 já normalizou. Se sim, ignorar.

---

## Ordem de Aplicação Recomendada

1. **DB** — tabelas `bank_credentials`, `vp_requests`, `vp_process_audit`
2. **pom.xml** — skip GPG + dependências iText/WebFlux/Velocity
3. **Exceções** — `BankCredentialException`, `BankWebHookException`, `PdfGenerationException`, `PdfParseException`, `VpRequestNotFoundException`
4. **Enums e Constantes** — `ErrorCode` (novos valores) + `Constants` (novas constantes)
5. **Models e Repositories** — `BankCredential`, `VpRequest`, `VpProcessAudit`
6. **Services interfaces** — todas as interfaces novas
7. **Services impl** — todas as implementações novas
8. **Controllers** — `VPCredentialRequestController`, `VPProcessController`
9. **`VPSubmissionController`** — adicionar logs
10. **Audit** — `VpAuditAspect`, `AuditConfig`
11. **Config** — `WebClientConfig`
12. **Templates HTML** — todos os templates de VC + `logo.png`
13. **Testes** — arquivos de teste + samples JSON
14. **Properties** — `application.properties`, `application-local.properties`
15. **Docker Compose** — `config.json`, `db-init/init.sql`
17. **Infraestrutura** — `Jenkinsfile`, `README.adoc`, `.gitattributes`, `update_script.sh`
18. **ui-test** — normalização CRLF→LF se necessário

---

## O que NÃO portar da v0.12.3

| Item | Motivo |
|---|---|
| Versão `4.1.2` no `pom.xml` | **Aplicar** — é a versão INJIBR padrão |
| `.gitignore` completo | A v0.17.0 tem seu próprio `.gitignore` — verificar conflito |

---

## Pontos de Atenção

1. **`VPSubmissionController` na v0.17.0** pode ter nova assinatura aceitando `error` e
   `error_description` do wallet. Verificar antes de adicionar os logs.

2. **`ErrorCode` e `Constants`** na v0.17.0 podem já ter alguns dos valores adicionados
   pelo patch. Verificar antes de aplicar para evitar duplicatas.

3. **`application.properties`** na v0.17.0 pode já ter o datasource parametrizado.
   Verificar o estado atual antes de aplicar.

4. **`BankOfBrazilWebhookServiceImpl`** usa mTLS com certificados em `/certs/`.
   Os certificados precisam ser provisionados no ambiente antes de usar.

5. **`VcParserServiceImpl`** faz parse de VC no formato LDP e SD-JWT. A v0.17.0
   pode já ter suporte a SD-JWT — verificar sobreposição.

6. **Templates HTML** usam Velocity. Verificar se a v0.17.0 mudou o mecanismo
   de templating antes de portar os arquivos.
