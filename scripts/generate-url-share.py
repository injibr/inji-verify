"""
Gera deeplinks openid4vp:// para usar no wallet.

Fluxos disponíveis:
  normal  - Cria VP request padrão (/vp-request), response_uri aponta para /vp-submission/direct-post
  banco   - Cria VP request como banco (/vp-credential-request), response_uri aponta para /vp-submission/vp-process

Uso:
  python generate-url-share.py [--fluxo normal|banco|ambos] [credencial1] [credencial2]
  python generate-url-share.py ALL

Exemplos:
  python generate-url-share.py CAFCredential
  python generate-url-share.py --fluxo banco CAFCredential
  python generate-url-share.py --fluxo ambos CARDocument CAFCredential
  python generate-url-share.py ALL
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
VERIFY_URL  = os.environ.get("VERIFY_URL", "")
BANK_ID     = os.environ.get("BANK_ID", "")
BANK_SECRET = os.environ.get("BANK_SECRET", "")

if not VERIFY_URL:
    print("Erro: VERIFY_URL não definida no .env")
    sys.exit(1)

CREDENTIALS = {
    "ECACredential":  "Comprovante de Maioridade",
    "CAFCredential":  "Cadastro Nacional da Agricultura Familiar",
    "CARDocument":    "CAR - Cadastro Ambiental Rural",
    "CARReceipt":     "Recibo de Inscrição do Imóvel Rural no CAR",
    "CCIRCredential": "CCIR - Certificado de Cadastro de Imóvel Rural",
}

# --- Parse args ---
args = sys.argv[1:]
fluxo = "ambos"

if "--fluxo" in args:
    idx = args.index("--fluxo")
    if idx + 1 >= len(args):
        print("Erro: --fluxo requer um valor: normal, banco ou ambos")
        sys.exit(1)
    fluxo = args[idx + 1]
    args = args[:idx] + args[idx + 2:]

if fluxo not in ("normal", "banco", "ambos"):
    print(f"Erro: --fluxo deve ser normal, banco ou ambos")
    sys.exit(1)

if not args:
    print(__doc__)
    print("Credenciais disponíveis:", ", ".join(CREDENTIALS.keys()))
    sys.exit(1)

if args[0].upper() == "ALL":
    credential_types = list(CREDENTIALS.keys())
else:
    credential_types = args
    for ct in credential_types:
        if ct not in CREDENTIALS:
            print(f"Credencial desconhecida: {ct}")
            print("Disponíveis:", ", ".join(CREDENTIALS.keys()))
            sys.exit(1)

if fluxo in ("banco", "ambos") and (not BANK_ID or not BANK_SECRET):
    print("Erro: BANK_ID e BANK_SECRET são necessários para o fluxo banco")
    sys.exit(1)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# --- Montar presentation definition ---
input_descriptors = [{
    "id": "id card credential",
    "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
    "constraints": {"fields": [{"path": ["$.type"], "filter": {"type": "object", "pattern": ct}}]}
} for ct in credential_types]

presentation_definition = {
    "id": "-".join(ct.lower() for ct in credential_types) + "-check",
    "purpose": "Relying party is requesting your digital ID",
    "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
    "input_descriptors": input_descriptors
}

def build_deeplink(auth, request_id, response_uri):
    client_id = auth["clientId"]
    params = urllib.parse.urlencode({
        "client_id": client_id,
        "response_type": auth.get("responseType", "vp_token"),
        "response_mode": auth.get("responseMode", "direct_post"),
        "nonce": auth.get("nonce", ""),
        "state": request_id,
        "response_uri": response_uri,
        "presentation_definition": json.dumps(auth["presentationDefinition"], separators=(',', ':')),
        "client_metadata": json.dumps({
            "client_name": client_id,
            "vp_formats": {"ldp_vp": {"proof_type": ["Ed25519Signature2018", "Ed25519Signature2020", "RsaSignature2018"]}}
        }, separators=(',', ':')),
    })
    return f"openid4vp://authorize?{params}"

def post_json(url, body, headers):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers=headers, method="POST")
    try:
        res = urllib.request.urlopen(req, context=ctx)
        return json.loads(res.read())
    except urllib.error.HTTPError as e:
        print(f"ERRO {e.code}: {e.read().decode()}")
        sys.exit(1)

def print_deeplink(label, transaction_id, request_id, deeplink):
    print(f"""
{'='*80}
  DEEPLINK GERADO — {label}
{'='*80}

  transactionId: {transaction_id}
  requestId:     {request_id}
  Credenciais:   {', '.join(credential_types)}

  Deeplink:
  {deeplink}

{'='*80}
  Use o deeplink no wallet para compartilhar as credenciais.
{'='*80}
""")

def resolve_response_uri(auth, data):
    uri = data.get("responseUri") or auth.get("responseUri", "")
    if not uri.startswith("http"):
        uri = f"{auth['clientId']}{uri}"
    return uri

# --- Fluxo normal ---
if fluxo in ("normal", "ambos"):
    data = post_json(
        f"{VERIFY_URL}/v1/verify/vp-request",
        {"clientId": VERIFY_URL, "presentationDefinition": presentation_definition},
        {"Content-Type": "application/json"}
    )
    auth = data["authorizationDetails"]
    deeplink = build_deeplink(auth, data["requestId"], resolve_response_uri(auth, data))
    print_deeplink("FLUXO NORMAL", data["transactionId"], data["requestId"], deeplink)

# --- Fluxo banco ---
if fluxo in ("banco", "ambos"):
    data = post_json(
        f"{VERIFY_URL}/v1/verify/vp-credential-request",
        {"clientId": VERIFY_URL, "presentationDefinition": presentation_definition},
        {"Content-Type": "application/json", "x-bank-id": BANK_ID, "x-bank-secret": BANK_SECRET}
    )
    auth = data["authorizationDetails"]
    deeplink = build_deeplink(auth, data["requestId"], resolve_response_uri(auth, data))
    print_deeplink("FLUXO BANCO", data["transactionId"], data["requestId"], deeplink)
