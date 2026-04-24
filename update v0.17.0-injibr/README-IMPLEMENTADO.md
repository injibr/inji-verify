# README — Alterações Implementadas (v0.17.0-injibr)

Este documento descreve todas as alterações efetivamente implementadas na migração
da branch `entrega` (baseada em v0.12.3) para a v0.17.0, nesta sessão de trabalho.

---

## 1. `pom.xml` (raiz)

- Versão alterada de `0.17.0` para `4.1.2` (versão INJIBR)
- Adicionado `<skip>true</skip>` no `maven-gpg-plugin`

```xml
<!-- INJIBR-CUSTOM: skip GPG signing for internal Dataprev CI pipeline -->
<skip>true</skip>
```

---

## 2. `verify-service/pom.xml`

- Versão alterada de `0.17.0` para `4.1.2` (versão INJIBR)
- Adicionadas propriedades de versão iText: `itextcore.version`, `itexthtml2pdf.version`, `itext.version`
- Novas dependências adicionadas com `<!-- INJIBR-CUSTOM -->`:
  - `httpclient5` — cliente HTTP para webhook do banco (versão gerenciada pelo Spring Boot BOM)
  - `spring-webflux` — WebClient reativo
  - `velocity` — engine de templates para geração de HTML
  - `itext7-core`, `html2pdf`, `itextpdf`, `xmlworker` — geração de PDF
  - `reactor-netty-http`, `netty-handler` — suporte SSL para WebClient
- Desabilitada geração de `sources` e `javadoc` JARs para evitar conflito no wildcard do Dockerfile

---

## 3. `verify-service/Dockerfile`

Imagem base alterada de `eclipse-temurin:25.0.1_8-jre-alpine` para `eclipse-temurin:25.0.1_8-jre`
e comandos `apk` substituídos por `apt-get` para evitar problemas de TLS com proxy corporativo.

```dockerfile
# INJIBR-CUSTOM: switched from alpine to jre base to avoid TLS issues with corporate proxy
FROM eclipse-temurin:25.0.1_8-jre
```

Path do ADD corrigido para contexto de build a partir da raiz do projeto:
```dockerfile
ADD ./verify-service/target/verify-service-*.jar ./verify-service.jar
```

---

## 4. `VPSubmissionController.java`

**Arquivo:** `verify-service/src/main/java/io/inji/verify/controller/VPSubmissionController.java`

Adicionados 3 logs no início do método `submitVP()`:

```java
// INJIBR-CUSTOM: log VP submission parameters for debugging
log.info("vp_token: {}", vpToken);
log.info("presentation_submission: {}", presentationSubmission);
log.info("state: {}", state);
```

**Nota:** a v0.17.0 já tem a nova assinatura com `error` e `error_description` — os logs foram adicionados antes da validação inicial.

---

## 5. `ErrorCode.java`

**Arquivo:** `verify-service/src/main/java/io/inji/verify/enums/ErrorCode.java`

Adicionados 5 novos valores ao enum:

```java
// INJIBR-CUSTOM: error codes for bank webhook and PDF generation flows
NO_VP_REQUEST("NO_VP_REQUEST","No VP request found for given transaction ID."),
BANK_WEBHOOK_ERROR("BANK_WEBHOOK_ERROR","Error occurred while processing bank webhook."),
BANK_CREDENTIAL_ERROR("BANK_CREDENTIAL_ERROR","Bank credentials are invalid."),
PDF_PARSE_FAILED("PDF_PARSE_FAILED","Error occurred while parsing PDF document."),
PDF_GENERATION_FAILED("PDF_GENERATION_FAILED","Error occurred while generating PDF document."),
```

---

## 6. `Constants.java`

**Arquivo:** `verify-service/src/main/java/io/inji/verify/shared/Constants.java`

Adicionadas 2 constantes:

```java
// INJIBR-CUSTOM: VP process endpoint root for bank webhook flow
public static final String RESPONSE_PROCESS_URI_ROOT = "/vp-process";
// INJIBR-CUSTOM: status constant for failed VP processing
public static final String FAILED = "FAILED";
```

---

## 7. `VPRequestNotFoundException.java`

**Arquivo:** `verify-service/src/main/java/io/inji/verify/exception/VPRequestNotFoundException.java`

A v0.17.0 tinha construtor sem argumentos. Adicionado construtor `(String, ErrorCode)` e
alterado para `RuntimeException` para compatibilidade com o código do scm/entrega que lança
sem declarar `throws`:

```java
// INJIBR-CUSTOM: extended VPRequestNotFoundException to support ErrorCode for bank webhook flow
@Getter
public class VPRequestNotFoundException extends RuntimeException {
    private ErrorCode errorCode;

    public VPRequestNotFoundException() { ... }
    public VPRequestNotFoundException(String message, ErrorCode errorCode) { ... }
}
```

---

## 8. `application.properties`

**Arquivo:** `verify-service/src/main/resources/application.properties`

Adicionadas propriedades INJIBR:

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

## 9. `application-local.properties`

**Arquivo:** `verify-service/src/main/resources/application-local.properties`

- Trocado HSQLDB por PostgreSQL
- Datasource parametrizado com variáveis de ambiente
- Adicionado `mosip.openid.htmlTemplate=credential-template.html`

---

## 10. Novos arquivos — Auditoria de VP

| Arquivo | Descrição |
|---|---|
| `aspect/VpAuditAspect.java` | AOP — intercepta chamadas e registra em `vp_process_audit` |
| `config/AuditConfig.java` | `@Value("${audit.enabled:false}")` |
| `models/VpProcessAudit.java` | Entidade JPA para `vp_process_audit` |
| `repository/VpProcessAuditRepository.java` | Repository JPA |
| `services/VpProcessAuditService.java` | Interface |
| `services/impl/VpProcessAuditServiceImpl.java` | Implementação |

**DDL:**
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

## 11. Novos arquivos — Webhook Banco do Brasil

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

**DDL:**
```sql
CREATE TABLE verify.bank_credentials (
    bank_id VARCHAR(100) NOT NULL,
    ...
    CONSTRAINT bank_credentials_pkey PRIMARY KEY (bank_id)
);

CREATE TABLE verify.vp_requests (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    bank_credential_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_bank_credential FOREIGN KEY (bank_credential_id)
        REFERENCES verify.bank_credentials (bank_id) ON DELETE CASCADE
);
```

---

## 12. Novos arquivos — Geração de PDF

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

**Templates HTML:**
- `INCRA-CCIRCredential-template.html`
- `MDA-CAFCredential-template.html`
- `MGI-CARDocument-template.html`
- `MGI-CARReceipt-template.html`
- `MGI-CARReceiptAST-template.html`
- `MGI-CARReceiptPCT-template.html`
- `credential-template.html`
- `logo.png`

---

## 13. Novos arquivos — Exceções

| Arquivo | Descrição |
|---|---|
| `exception/BankCredentialException.java` | Exceção para credenciais de banco |
| `exception/BankWebHookException.java` | Exceção para webhook de banco |
| `exception/PdfGenerationException.java` | Exceção para geração de PDF |
| `exception/PdfParseException.java` | Exceção para parse de PDF |

---

## 14. Testes novos

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

## 15. Docker Compose e infraestrutura

- `docker-compose/config/config.json` — substituídos tipos MOSIP pelos tipos INJIBR (CARDocument, CARReceipt, CCIRCredential, CAFCredential) com `Ed25519Signature2020`
- `docker-compose/db-init/init.sql` — adicionadas tabelas `bank_credentials`, `vp_requests`; mantidas colunas `error` e `error_description` na `vp_submission` (adicionadas pela v0.17.0)
- `Jenkinsfile` — pipeline CI Dataprev
- `README.adoc` — documentação interna Dataprev
- `update_script.sh` — script de atualização de versão
- `.gitattributes` — normalização de line endings

---

## Status geral

| Item | Status |
|---|---|
| `pom.xml` raiz — versão `4.1.2` + skip GPG | ✅ implementado |
| `verify-service/pom.xml` — versão + dependências iText/WebFlux/Velocity | ✅ implementado |
| `Dockerfile` — base image jre + path ADD | ✅ implementado |
| `VPSubmissionController` — logs | ✅ implementado |
| `ErrorCode` — novos valores | ✅ implementado |
| `Constants` — novas constantes | ✅ implementado |
| `VPRequestNotFoundException` — construtor `(String, ErrorCode)` + RuntimeException | ✅ implementado |
| `application.properties` — propriedades govbr/banco/audit | ✅ implementado |
| `application-local.properties` — PostgreSQL + htmlTemplate | ✅ implementado |
| Auditoria de VP | ✅ implementado |
| Webhook Banco do Brasil | ✅ implementado |
| Geração de PDF | ✅ implementado |
| Exceções | ✅ implementado |
| Testes | ✅ implementado |
| Docker Compose + infraestrutura | ✅ implementado |
