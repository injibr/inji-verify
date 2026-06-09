"""
Gera o deeplink openid4vp:// para usar no wallet.

Fluxo:
  1. Cria VP request como banco (/vp-credential-request)
  2. Gera e exibe o deeplink openid4vp:// para usar no wallet

Uso:
  python simulate-bank-flow.py [credencial1] [credencial2]
  python simulate-bank-flow.py ALL

Exemplo:
  python simulate-bank-flow.py CAFCredential
  python simulate-bank-flow.py CARDocument CAFCredential
  python simulate-bank-flow.py ALL
"""
import sys, json, urllib.parse, urllib.request, ssl, os

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
required_vars = ["VERIFY_URL", "BANK_ID", "BANK_SECRET"]
missing = [v for v in required_vars if not os.environ.get(v)]
if missing:
    print(f"Erro: variáveis não definidas no .env: {', '.join(missing)}")
    sys.exit(1)

VERIFY_URL  = os.environ["VERIFY_URL"]
BANK_ID     = os.environ["BANK_ID"]
BANK_SECRET = os.environ["BANK_SECRET"]

CREDENTIALS = {
    "ECACredential":  "Comprovante de Maioridade",
    "CAFCredential":  "Cadastro Nacional da Agricultura Familiar",
    "CARDocument":    "CAR - Cadastro Ambiental Rural",
    "CARReceipt":     "Recibo de Inscrição do Imóvel Rural no CAR",
    "CCIRCredential": "CCIR - Certificado de Cadastro de Imóvel Rural",
}

if len(sys.argv) < 2:
    print(__doc__)
    print("Credenciais disponíveis:", ", ".join(CREDENTIALS.keys()))
    sys.exit(1)

if sys.argv[1].upper() == "ALL":
    credential_types = list(CREDENTIALS.keys())
else:
    credential_types = sys.argv[1:]
    for ct in credential_types:
        if ct not in CREDENTIALS:
            print(f"Credencial desconhecida: {ct}")
            print("Disponíveis:", ", ".join(CREDENTIALS.keys()))
            sys.exit(1)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# --- Criar VP request ---
input_descriptors = [{
    "id": "id card credential",
    "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
    "constraints": {"fields": [{"path": ["$.type"], "filter": {"type": "object", "pattern": ct}}]}
} for ct in credential_types]

body = json.dumps({
    "clientId": VERIFY_URL,
    "presentationDefinition": {
        "id": "-".join(ct.lower() for ct in credential_types) + "-check",
        "purpose": "Relying party is requesting your digital ID",
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
except urllib.error.HTTPError as e:
    print(f"ERRO {e.code}: {e.read().decode()}")
    sys.exit(1)

# --- Gerar deeplink ---
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
    "client_metadata": json.dumps({
        "client_name": client_id,
        "vp_formats": {"ldp_vp": {"proof_type": ["Ed25519Signature2018", "Ed25519Signature2020", "RsaSignature2018"]}}
    }, separators=(',', ':')),
})

deeplink = f"openid4vp://authorize?{params}"

print(f"""
{'='*80}
  DEEPLINK GERADO
{'='*80}

  transactionId: {data['transactionId']}
  requestId:     {data['requestId']}
  Credenciais:   {', '.join(credential_types)}

  Deeplink:
  {deeplink}

{'='*80}
  Use o deeplink no wallet para compartilhar as credenciais.
{'='*80}
""")
