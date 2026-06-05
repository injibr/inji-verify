"""
Simula o fluxo completo do banco no ambiente DEV remoto.

Fluxo:
  1. Cria VP request como banco (/vp-credential-request) com headers [x-bank-id] e [x-bank-secret]
  2. Gera deeplink openid4vp:// para o wallet
  3. Aguarda resultado (polling de status)

Uso:
  python simulate-bank-flow.py [credencial1] [credencial2] [credencial3]
  python simulate-bank-flow.py ALL

Exemplo:
  python simulate-bank-flow.py CAFCredential
  python simulate-bank-flow.py CARReceipt CARDocument CAFCredential
  python simulate-bank-flow.py ALL
"""
import sys, json, urllib.parse, urllib.request, ssl, time, os

# --- Load .env ---
def load_env(path):
    if not os.path.exists(path):
        return
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())

load_env(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"))

# --- Config ---
required_vars = ["VERIFY_URL", "WIREMOCK_URL", "WIREMOCK_KEY", "BANK_ID", "BANK_SECRET"]
missing = [v for v in required_vars if not os.environ.get(v)]
if missing:
    print(f"Erro: variáveis não definidas no .env: {', '.join(missing)}")
    print(f"Crie o arquivo scripts/.env baseado no .env.example")
    sys.exit(1)

VERIFY_URL = os.environ["VERIFY_URL"]
WIREMOCK_URL = os.environ["WIREMOCK_URL"]
WIREMOCK_KEY = os.environ["WIREMOCK_KEY"]
BANK_ID = os.environ["BANK_ID"]
BANK_SECRET = os.environ["BANK_SECRET"]

CREDENTIALS = {
    "ECACredential": "Comprovante de Maioridade",
    "CAFCredential": "Cadastro Nacional da Agricultura Familiar",
    "CARDocument": "CAR - Cadastro Ambiental Rural",
    "CARReceipt": "Recibo de Inscrição do Imóvel Rural no CAR",
    "CCIRCredential": "CCIR - Certificado de Cadastro de Imóvel Rural",
}

if len(sys.argv) < 2:
    print(__doc__)
    print("Credenciais disponíveis:", ", ".join(CREDENTIALS.keys()))
    print("Use ALL para solicitar todas de uma vez.")
    sys.exit(1)

if sys.argv[1].upper() == "ALL":
    credential_types = list(CREDENTIALS.keys())
else:
    credential_types = sys.argv[1:]
    for ct in credential_types:
        if ct not in CREDENTIALS:
            print(f"Credencial desconhecida: {ct}")
            print("Credenciais disponíveis:", ", ".join(CREDENTIALS.keys()))
            sys.exit(1)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def api_call(url, data=None, headers=None, method="GET"):
    """Helper para chamadas HTTP."""
    if headers is None:
        headers = {}
    body = json.dumps(data).encode() if data else None
    if body and "Content-Type" not in headers:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        res = urllib.request.urlopen(req, context=ctx)
        return res.status, json.loads(res.read()) if res.read else {}
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


print(f"""
{'='*80}
  SIMULACAO DEV: BANCO -> VERIFY -> WALLET -> VERIFY -> WEBHOOK (WIREMOCK)
{'='*80}

  Verify:        {VERIFY_URL}
  Wiremock:      {WIREMOCK_URL}
  Credenciais:   {', '.join(credential_types)}
  Bank ID:       {BANK_ID}
  Bank SECRET:   {BANK_SECRET}
""")

# --- Step 1: Criar bank_credentials via SQL (instruções) ---
print(f"""
[1/4] Verifique se bank_credentials existe no dev:

      kubectl exec -n <ns> deploy/postgres -- psql -U inji_verify -d inji_verify -c "
        INSERT INTO verify.bank_credentials 
          (bank_id, bank_name, api_key, bank_secret, bank_webhook_url, bank_webhook_token_url, bank_webhook_uri, bank_webhook_token_uri)
        VALUES 
          ('{BANK_ID}', 'Bank Dev Test', 'test-key', '{BANK_SECRET}', '{WIREMOCK_URL}', '{WIREMOCK_URL}', '/bank/webhook', '/bank/oauth/token')
        ON CONFLICT (bank_id) DO UPDATE SET
          bank_webhook_url = '{WIREMOCK_URL}',
          bank_webhook_token_url = '{WIREMOCK_URL}',
          bank_webhook_uri = '/bank/webhook',
          bank_webhook_token_uri = '/bank/oauth/token';
      "
""")

try:
    input("      Pressione ENTER quando pronto (ou ENTER se já feito)...")
except KeyboardInterrupt:
    print("\n      Cancelado.")
    sys.exit(0)

# --- Step 2: Criar VP request como banco ---
print("\n[2/4] Criando VP request como banco...")

input_descriptors = []
for ct in credential_types:
    input_descriptors.append({
        "id": "id card credential",
        "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
        "constraints": {"fields": [{"path": ["$.type"], "filter": {"type": "object", "pattern": ct}}]}
    })

body = json.dumps({
    "clientId": VERIFY_URL,
    "presentationDefinition": {
        "id": "-".join([ct.lower() for ct in credential_types]) + "-check",
        "purpose": "Relying party is requesting your digital ID for the purpose of Self-Authentication",
        "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
        "input_descriptors": input_descriptors
    }
}).encode()

req = urllib.request.Request(
    f"{VERIFY_URL}/v1/verify/vp-credential-request",
    data=body,
    headers={
        "Content-Type": "application/json",
        "x-bank-id": BANK_ID,
        "x-bank-secret": BANK_SECRET,
    },
    method="POST"
)

try:
    res = urllib.request.urlopen(req, context=ctx)
    data = json.loads(res.read())
    print(f"      transactionId: {data['transactionId']}")
    print(f"      requestId:     {data['requestId']}")
except urllib.error.HTTPError as e:
    error_body = e.read().decode()
    print(f"      ERRO {e.code}: {error_body}")
    sys.exit(1)

# --- Step 3: Gerar deeplink ---
print("\n[3/4] Deeplink gerado:")

auth = data["authorizationDetails"]
client_id = auth["clientId"]
response_uri = auth.get("responseUri", "")
if not response_uri.startswith("http"):
    response_uri = f"{client_id}{response_uri}"

params = urllib.parse.urlencode({
    "client_id": client_id,
    "response_type": auth.get("responseType", "vp_token"),
    "response_mode": auth.get("responseMode", "direct_post"),
    "nonce": auth.get("nonce", ""),
    "state": data["requestId"],
    "response_uri": response_uri,
    "presentation_definition": json.dumps(auth["presentationDefinition"], separators=(',', ':')),
    "client_metadata": json.dumps({"client_name": client_id, "vp_formats": {"ldp_vp": {"proof_type": ["Ed25519Signature2018", "Ed25519Signature2020", "RsaSignature2018"]}}}, separators=(',', ':')),
})

deeplink = f"openid4vp://authorize?{params}"

print(f"\n      {deeplink}")
print(f"""
      response_uri:   {response_uri}
      transactionId:  {data['transactionId']}
      requestId:      {data['requestId']}

{'='*80}
  Use o deeplink no wallet. Após compartilhar, verifique o wiremock:

  curl -sk "{WIREMOCK_URL}/__admin/requests?limit=5" -H "x-wiremock-key: {WIREMOCK_KEY}" | python -m json.tool
{'='*80}
""")

# --- Step 4: Polling de status ---
print("[4/4] Aguardando VP submission (polling)...")
print(f"{VERIFY_URL}/v1/verify/vp-request/{data['requestId']}/status")
print("      Ctrl+C para cancelar\n")

try:
    for i in range(60):  # 5 minutos (60 x 5s)
        req = urllib.request.Request(
            f"{VERIFY_URL}/v1/verify/vp-request/{data['requestId']}/status",
            method="GET"
        )
        try:
            res = urllib.request.urlopen(req, context=ctx, timeout=10)
            status_data = json.loads(res.read())
            status = status_data.get("status", "UNKNOWN")

            if status == "VP_SUBMITTED":
                print(f"      ✅ VP submetida com sucesso!")
                print(f"      Status: {status}")
                print(f"\n      Verifique o webhook no wiremock:")
                print(f"      curl -sk \"{WIREMOCK_URL}/__admin/requests?limit=5\" -H \"x-wiremock-key: {WIREMOCK_KEY}\"")
                break
            elif status == "EXPIRED":
                print(f"      ⚠️ Request expirou (5 min). Gere um novo.")
                break
            else:
                print(f"      [{i*5}s] Status: {status} - aguardando...", end="\r")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                print(f"      ⚠️ Request não encontrado.")
                break
            # Long polling timeout - normal, continuar
            pass
        except (urllib.error.URLError, TimeoutError, OSError):
            # Timeout na request - normal, continuar polling
            print(f"      [{i*5}s] aguardando...", end="\r")

        time.sleep(2)
    else:
        print("\n      Timeout (5 min) - nenhuma submission recebida.")
except KeyboardInterrupt:
    print("\n      Cancelado.")

print("\nFim.")
