# INJIBR — Customizações do inji-verify

Este documento descreve todas as customizações feitas pelo time INJIBR sobre o upstream
`inji-verify` da MOSIP/INJI. Serve como referência para futuras atualizações de versão e como
contexto para ferramentas de IA assistindo no processo.

---

## Visão Geral

O inji-verify upstream (v0.17.0) foi adaptado para o fluxo de verificação de credenciais
com integração bancária. As principais diferenças em relação ao upstream são:

- Webhook do Banco do Brasil para notificação de resultado de VP
- Geração de PDF a partir de templates HTML por tipo de credencial (CAR, CCIR, CAF)
- Auditoria de processamento de VP (tabela `vp_process_audit`)
- Endpoint `/vp-credential-request` para fluxo de requisição de credenciais pelo banco
- Endpoint `/vp-process` para processamento do resultado e notificação via webhook
- mTLS para comunicação com APIs bancárias
- Dockerfile com path de ADD ajustado para build a partir da raiz do projeto
- Pipeline CI Dataprev (Jenkinsfile)

---

## Base Upstream

- **Versão upstream:** v0.17.0 (commit `3b6f90fd` — `[DSD-9870] inji-verify 0.17.0 release`)
- **Versão INJIBR:** 5.0.1

---

## Commits INJIBR sobre o upstream

| Commit | Descrição |
|---|---|
| `56802415` | sync v0.17.0 + diff patch |
| `edd79cc8` | feat: INJIBR - migração v0.17.0 com webhook Banco do Brasil, geração de PDF e fix VPRequestNotFoundException |
| `165c5b01` | revert: dockerfile usando alpine |
| `aa8560c5` | feat: atualizacao jenksfile |
| `44438be8` | feat: Versao 5.0 Atualiza CORE INJI |
| `e9b3e433` | fix: dockerfile |
| `36ae1c15` | fix: pom verify service |
| `6b30f7a0` | Update README.adoc |
| `c186f478` | fix: normalize line endings (CRLF to LF) per .gitattributes |
| `111925d1` | fix: fluxo do endpoint para vp-process |

---

## Arquivos Modificados (upstream)

### `pom.xml` (raiz)

**Customizações:**

1. **Versão INJIBR:** `5.0.1` (upstream era `0.17.0`)
2. **GPG skip:** `<skip>true</skip>` no `maven-gpg-plugin` — pipeline Dataprev não usa GPG signing

```xml
<!-- INJIBR-CUSTOM: skip GPG signing for internal Dataprev CI pipeline -->
<skip>true</skip>
```

---

### `verify-service/pom.xml`

**Customizações:**

1. **Versão INJIBR:** `5.0.1`
2. **Properties iText:** `itextcore.version`, `itexthtml2pdf.version`, `itext.version`
3. **Dependências novas:**
   - `httpclient5` — cliente HTTP para webhook do banco
   - `spring-webflux` — WebClient reativo para webhook
   - `velocity` (1.7) — engine de templates para geração de HTML
   - `itext7-core`, `html2pdf`, `itextpdf`, `xmlworker` — geração de PDF
   - `reactor-netty-http`, `netty-handler` — suporte SSL para WebClient

---

### `verify-service/Dockerfile`

**Customizações:**

1. **Path do ADD corrigido** para build a partir da raiz do projeto:
```dockerfile
ADD verify-service/target/verify-service-*.jar ./verify-service.jar
```
(Upstream usa `./target/verify-service-*.jar` — assume build dentro de `verify-service/`)

---

### `verify-service/src/main/java/io/inji/verify/controller/VPSubmissionController.java`

**Customizações:**

1. **Logs de debugging** no início do método `submitVP()`:
```java
// INJIBR-CUSTOM: log VP submission parameters for debugging
log.info("vp_token: {}", vpToken);
log.info("presentation_submission: {}", presentationSubmission);
log.info("state: {}", state);
```

---

### `verify-service/src/main/java/io/inji/verify/enums/ErrorCode.java`

**Customizações:**

1. **5 novos valores** no enum para fluxos de banco e PDF:
```java
// INJIBR-CUSTOM: error codes for bank webhook and PDF generation flows
NO_VP_REQUEST("NO_VP_REQUEST","No VP request found for given transaction ID."),
BANK_WEBHOOK_ERROR("BANK_WEBHOOK_ERROR","Error occurred while processing bank webhook."),
BANK_CREDENTIAL_ERROR("BANK_CREDENTIAL_ERROR","Bank credentials are invalid."),
PDF_PARSE_FAILED("PDF_PARSE_FAILED","Error occurred while parsing PDF document."),
PDF_GENERATION_FAILED("PDF_GENERATION_FAILED","Error occurred while generating PDF document."),
```

---

### `verify-service/src/main/java/io/inji/verify/shared/Constants.java`

**Customizações:**

1. **2 novas constantes:**
```java
// INJIBR-CUSTOM: VP process endpoint root for bank webhook flow
public static final String RESPONSE_PROCESS_URI_ROOT = "/vp-process";
// INJIBR-CUSTOM: status constant for failed VP processing
public static final String FAILED = "FAILED";
```

---

### `verify-service/src/main/java/io/inji/verify/exception/VPRequestNotFoundException.java`

**Customizações:**

1. **Mudou de `Exception` para `RuntimeException`** — necessário porque o código do webhook lança sem declarar `throws`
2. **Construtor `(String, ErrorCode)` adicionado** — permite customizar mensagem e código de erro
3. **Anotação `@Getter`** do Lombok adicionada

```java
// INJIBR-CUSTOM: extended VPRequestNotFoundException to support ErrorCode for bank webhook flow
@Getter
public class VPRequestNotFoundException extends RuntimeException {
    private ErrorCode errorCode;
    public VPRequestNotFoundException() { super(defaultMessage); }
    public VPRequestNotFoundException(String message, ErrorCode errorCode) { ... }
}
```

---

### `verify-service/src/main/resources/application.properties`

**Customizações:**

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

### `verify-service/src/main/resources/application-local.properties`

**Customizações:**

1. **Trocado HSQLDB por PostgreSQL** com datasource parametrizado via variáveis de ambiente
2. **Adicionado** `mosip.openid.htmlTemplate=credential-template.html`

---

## Arquivos Novos — Webhook Banco do Brasil

| Arquivo | Descrição |
|---|---|
| `controller/VPCredentialRequestController.java` | Endpoint `/vp-credential-request` — banco solicita verificação de credencial, cria VP request e retorna QR code com `responseUri` apontando para `/vp-process` |
| `controller/VPProcessController.java` | Endpoint `/vp-process` — processa resultado do VP e notifica o banco via webhook |
| `dto/authorizationrequest/BankAuthorizationRequestResponseDto.java` | Wrapper sobre `AuthorizationRequestResponseDto` que substitui o `responseUri` pelo endpoint do banco |
| `dto/authorizationrequest/BankVPRequestResponseDto.java` | Wrapper sobre `VPRequestResponseDto` usando `BankAuthorizationRequestResponseDto` |
| `config/WebClientConfig.java` | Bean `WebClient` Spring para chamadas reativas |
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
| `exception/BankCredentialException.java` | Exceção para credenciais de banco |
| `exception/BankWebHookException.java` | Exceção para webhook de banco |

---

## Arquivos Novos — Auditoria de VP

| Arquivo | Descrição |
|---|---|
| `aspect/VpAuditAspect.java` | AOP — intercepta chamadas e registra em `vp_process_audit` |
| `config/AuditConfig.java` | `@Value("${audit.enabled:false}")` |
| `models/VpProcessAudit.java` | Entidade JPA para `vp_process_audit` |
| `repository/VpProcessAuditRepository.java` | Repository JPA |
| `services/VpProcessAuditService.java` | Interface |
| `services/impl/VpProcessAuditServiceImpl.java` | Implementação |

---

## Arquivos Novos — Geração de PDF

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
| `exception/PdfGenerationException.java` | Exceção para geração de PDF |
| `exception/PdfParseException.java` | Exceção para parse de PDF |

**Templates HTML:**
- `resources/templates/INCRA-CCIRCredential-template.html`
- `resources/templates/MDA-CAFCredential-template.html`
- `resources/templates/MGI-CARDocument-template.html`
- `resources/templates/MGI-CARReceipt-template.html`
- `resources/templates/MGI-CARReceiptAST-template.html`
- `resources/templates/MGI-CARReceiptPCT-template.html`
- `resources/templates/credential-template.html`
- `resources/templates/logo.png`

---

## Arquivos Novos — Testes

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

## Arquivos Novos — Infraestrutura

| Arquivo | Descrição |
|---|---|
| `.gitattributes` | Normalização de line endings |
| `Jenkinsfile` | Pipeline CI Dataprev |
| `README.adoc` | Documentação interna Dataprev |
| `update_script.sh` | Script de atualização de versão upstream |

---

## Banco de Dados

Tabelas INJIBR adicionadas ao `docker-compose/db-init/init.sql`:

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

## Docker Compose — config.json

Tipos de credencial substituídos dos exemplos MOSIP pelos tipos INJIBR:
- `CARDocument` (MGI)
- `CARReceipt` (MGI)
- `CCIRCredential` (INCRA)
- `CAFCredential` (MDA)

Todos usando `Ed25519Signature2020` como proof_type.

---

## Fluxo de Verificação com Webhook Bancário

```
Banco → POST /vp-credential-request (bankId, bankSecret, presentationDefinition)
     ← 201: { transactionId, requestId, authorizationDetails { responseUri: .../vp-process } }

Wallet ← QR code com responseUri apontando para /vp-submission/direct-post/vp-process

Wallet → POST /vp-submission/direct-post/vp-process (vp_token, presentation_submission, state)
     Verify Backend:
       1. Armazena VP submission
       2. Verifica VP usando vc-verifier
       3. Gera PDF com template do tipo de VC
       4. Notifica banco via webhook (mTLS) com resultado + PDF
     ← 200: { redirect_uri }

Banco recebe webhook com resultado da verificação
```

---

## Configurações Relevantes

```properties
# Upstream (já existentes)
inji.vp-submission.base-url=${INJI_VP_SUBMISSION_BASE_URL}
inji.did.verify.uri=${INJI_DID_VERIFY_URI}

# INJIBR (adicionadas)
mosip.openid.htmlTemplate=credential-template.html
govbr.bb.token.base.url=https://oauth.bb.com.br
govbr.bb.token.auth=${GOVBR_BB_TOKEN_AUTH}
govbr.bb.webhook.uri.path=/v1/response/files
mtls.client.keystore-path=${KEYSTORE_PATH}
mtls.client.keystore-password=${KEYSTORE_PASSWORD}
mtls.client.truststore-path=${TRUSTSTORE_PATH}
mtls.client.truststore-password=${TRUSTSTORE_PASSWORD}
audit.enabled=${AUDIT_ENABLED:false}
```

---

## Checklist de Migração (após rebase para nova versão upstream)

Arquivos que **sempre** precisam revisão manual após rebase:

- [ ] `pom.xml` — versão INJIBR + skip GPG
- [ ] `verify-service/pom.xml` — versão + dependências iText/WebFlux/Velocity
- [ ] `verify-service/Dockerfile` — path do ADD
- [ ] `VPSubmissionController.java` — logs de debugging
- [ ] `ErrorCode.java` — novos valores (verificar duplicatas com upstream)
- [ ] `Constants.java` — `RESPONSE_PROCESS_URI_ROOT` e `FAILED`
- [ ] `VPRequestNotFoundException.java` — `RuntimeException` + construtor com `ErrorCode`
- [ ] `application.properties` — propriedades govbr/banco/audit
- [ ] `application-local.properties` — PostgreSQL
- [ ] `VPCredentialRequestController.java` — endpoint do banco + `BankVPRequestResponseDto`
- [ ] `VPProcessController.java` — processamento + webhook
- [ ] Tabelas `bank_credentials`, `vp_requests`, `vp_process_audit` no DDL
- [ ] Templates HTML presentes em `resources/templates/`

---

## Regras para IA (Amazon Q / Copilot)

Ao assistir em tarefas neste repositório, seguir estas regras:

### Regras gerais

1. **Nunca remover customizações INJIBR** marcadas com comentário `// INJIBR-CUSTOM`
2. **Nunca alterar o `responseUri`** retornado pelo `VPCredentialRequestController` — ele DEVE apontar para `/vp-process`
3. **Não alterar** `VPRequestNotFoundException` de volta para `Exception` — é `RuntimeException` por design

### Regras de rebase

4. Ao fazer rebase, aplicar o checklist acima em ordem
5. Verificar se `ErrorCode` upstream não adicionou valores com nomes conflitantes
6. Verificar se `Constants` upstream não adicionou `FAILED` ou `RESPONSE_PROCESS_URI_ROOT`
7. Verificar se `VPSubmissionController` mudou de assinatura antes de reaplicar os logs

### Regras de código

8. Manter comentários `// INJIBR-CUSTOM` em todas as customizações
9. Ao adicionar novo gerador de HTML, seguir padrão: `{Tipo}HtmlGeneratorServiceImpl`
10. Ao adicionar novo template, seguir padrão: `{Emissor}-{Tipo}-template.html`
11. Ao adicionar nova exceção, criar em `io.inji.verify.exception`

---

## Versionamento

O INJIBR usa versionamento semântico próprio, independente do upstream.

- Upstream v0.17.0 → INJIBR 5.0.1
- `MAJOR` — incrementado a cada rebase sobre uma nova versão upstream
- `MINOR` — incrementado para novas funcionalidades INJIBR dentro da mesma base upstream
- `PATCH` — incrementado para correções de bugs
