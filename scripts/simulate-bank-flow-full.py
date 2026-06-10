"""
Simula o fluxo completo do banco SEM aplicativo.

Fluxo:
  0. Emite VCs reais via Gov.br SSO + Inji Certify
  1. Cria VP request como banco (/vp-credential-request)
  2. Monta VP com as VCs reais emitidas (simulando o wallet)
  3. Envia VP diretamente para /vp-process (sem precisar do app)
  4. Aguarda resultado (polling de status)

Uso:
  python simulate-bank-flow-full.py [credencial1] [credencial2]
  python simulate-bank-flow-full.py ALL

Credenciais disponíveis: CAFCredential, CARDocument, CCIRCredential, ECACredential, CARReceipt
"""
import sys, json, base64, hashlib, secrets, time, os
import urllib.parse, urllib.request, urllib.error
import http.server, webbrowser, ssl

# ---------------------------------------------------------------------------
# .env loader
# ---------------------------------------------------------------------------
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

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
required_vars = ["VERIFY_URL", "WIREMOCK_URL", "WIREMOCK_KEY", "BANK_ID", "BANK_SECRET",
                 "SSO_CLIENT_ID", "SSO_CLIENT_SECRET"]
missing = [v for v in required_vars if not os.environ.get(v)]
if missing:
    print(f"Erro: variáveis não definidas no .env: {', '.join(missing)}")
    sys.exit(1)

VERIFY_URL         = os.environ["VERIFY_URL"]
WIREMOCK_URL       = os.environ["WIREMOCK_URL"]
WIREMOCK_KEY       = os.environ["WIREMOCK_KEY"]
BANK_ID            = os.environ["BANK_ID"]
BANK_SECRET        = os.environ["BANK_SECRET"]
SSO_CLIENT_ID      = os.environ["SSO_CLIENT_ID"]
SSO_CLIENT_SECRET  = os.environ["SSO_CLIENT_SECRET"]
CERTIFY_URL        = os.environ.get("CERTIFY_URL")
CERTIFY_IDENTIFIER = os.environ.get("CERTIFY_IDENTIFIER")
SSO_URL            = "https://sso.staging.acesso.gov.br"
REDIRECT_PORT      = 3004
REDIRECT_URI       = f"http://localhost:{REDIRECT_PORT}/redirect"
TOKEN_CACHE_FILE   = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".token_cache.json")

CREDENTIAL_MAP = {
    "CAFCredential":  ("MDA",   "CAFCredential",  "https://www.w3.org/ns/credentials/v2"),
    "CARDocument":    ("MGI",   "CARDocument",    "https://www.w3.org/ns/credentials/v2"),
    "CCIRCredential": ("INCRA", "CCIRCredential", "https://www.w3.org/ns/credentials/v2"),
    "ECACredential":  ("MGI",   "ECACredential",  "https://www.w3.org/ns/credentials/v2"),
    "CARReceipt":  ("MGI",   "CARReceipt",  "https://www.w3.org/ns/credentials/v2"),
}

if len(sys.argv) < 2:
    print(__doc__)
    print("Credenciais disponíveis:", ", ".join(CREDENTIAL_MAP.keys()))
    sys.exit(1)

if sys.argv[1].upper() == "ALL":
    credential_types = list(CREDENTIAL_MAP.keys())
else:
    credential_types = sys.argv[1:]
    for ct in credential_types:
        if ct not in CREDENTIAL_MAP:
            print(f"Credencial desconhecida: {ct}")
            print("Disponíveis:", ", ".join(CREDENTIAL_MAP.keys()))
            sys.exit(1)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def http_get(url):
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, context=ctx, timeout=10) as r:
        return r.status, json.loads(r.read())

def b64url(data):
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

def generate_pkce():
    verifier = secrets.token_urlsafe(32)[:43]
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
    return verifier, challenge

def build_authorize_url(code_challenge):
    params = {
        "response_type": "code",
        "client_id": SSO_CLIENT_ID,
        "scope": "openid email profile",
        "redirect_uri": REDIRECT_URI,
        "nonce": secrets.token_hex(16),
        "state": secrets.token_hex(16),
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    return f"{SSO_URL}/authorize?{urllib.parse.urlencode(params)}"

def exchange_code(code, code_verifier):
    params = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": code_verifier,
    })
    credentials = base64.b64encode(f"{SSO_CLIENT_ID}:{SSO_CLIENT_SECRET}".encode()).decode()
    req = urllib.request.Request(
        f"{SSO_URL}/token?{params}", method="POST",
        headers={"Accept": "application/json", "Authorization": f"Basic {credentials}"}
    )
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        print(f"    ERRO na troca de código: {e.code} - {e.read().decode()}")
        sys.exit(1)

def decode_jwt_payload(token):
    part = token.split(".")[1]
    part += "=" * (4 - len(part) % 4)
    return json.loads(base64.urlsafe_b64decode(part))

def make_proof_jwt(client_id, c_nonce=None):
    try:
        from cryptography.hazmat.primitives.asymmetric import rsa, padding
        from cryptography.hazmat.primitives import hashes
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        pub = private_key.public_key().public_numbers()
        def i2b(n):
            b = n.to_bytes((n.bit_length() + 7) // 8, "big")
            return b64url(b)
        jwk = {"kty": "RSA", "n": i2b(pub.n), "e": i2b(pub.e)}
        header = {"typ": "openid4vci-proof+jwt", "alg": "RS256", "jwk": jwk}
        now = int(time.time())
        payload = {"iss": client_id, "sub": client_id, "aud": CERTIFY_IDENTIFIER,
                   "iat": now, "exp": now + 300}
        if c_nonce:
            payload["nonce"] = c_nonce
        signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
        sig = private_key.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
        return f"{signing_input}.{b64url(sig)}"
    except ImportError:
        import subprocess, tempfile
        with tempfile.NamedTemporaryFile(suffix=".pem", delete=False) as f:
            key_file = f.name
        subprocess.run(["openssl", "genrsa", "-out", key_file, "2048"], capture_output=True, check=True)
        result = subprocess.run(["openssl", "rsa", "-in", key_file, "-text", "-noout"],
                                capture_output=True, text=True, check=True)
        lines = result.stdout.split("\n")
        in_mod, mod_hex = False, ""
        for line in lines:
            if "modulus:" in line.lower(): in_mod = True; continue
            if "publicexponent" in line.lower().replace(" ", ""): in_mod = False; continue
            if in_mod:
                s = line.strip()
                if s and all(c in "0123456789abcdef:" for c in s):
                    mod_hex += s.replace(":", "")
                else:
                    in_mod = False
        mod_bytes = bytes.fromhex(mod_hex)
        if mod_bytes[0] == 0: mod_bytes = mod_bytes[1:]
        jwk = {"kty": "RSA", "n": b64url(mod_bytes), "e": b64url((65537).to_bytes(3, "big"))}
        header = {"typ": "openid4vci-proof+jwt", "alg": "RS256", "jwk": jwk}
        now = int(time.time())
        payload = {"iss": client_id, "sub": client_id, "aud": CERTIFY_IDENTIFIER,
                   "iat": now, "exp": now + 300}
        if c_nonce: payload["nonce"] = c_nonce
        signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
        with tempfile.NamedTemporaryFile(suffix=".bin", delete=False) as f:
            inp = f.name; f.write(signing_input.encode())
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key_file, inp],
                             capture_output=True, check=True).stdout
        os.unlink(key_file); os.unlink(inp)
        return f"{signing_input}.{b64url(sig)}"

def load_token_cache():
    try:
        with open(TOKEN_CACHE_FILE) as f: return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError): return {}

def save_token_cache(cache):
    with open(TOKEN_CACHE_FILE, "w") as f: json.dump(cache, f, indent=2)

def get_cached_token(cache, issuer_id):
    entry = cache.get(issuer_id, {})
    token, exp = entry.get("access_token"), entry.get("exp", 0)
    return token if token and time.time() < exp else None

def cache_token(cache, issuer_id, access_token):
    claims = decode_jwt_payload(access_token)
    cache[issuer_id] = {"access_token": access_token, "exp": claims.get("exp", 0)}
    save_token_cache(cache)

def do_sso_login(issuer_id):
    code_verifier, code_challenge = generate_pkce()
    authorize_url = build_authorize_url(code_challenge)
    captured_code = None

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            nonlocal captured_code
            params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if "code" not in params:
                self.send_response(204); self.end_headers()
                return
            captured_code = params["code"][0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"<h1>Login OK!</h1><p>Pode fechar esta aba e voltar ao terminal.</p>")
        def log_message(self, *a): pass

    server = http.server.HTTPServer(("localhost", REDIRECT_PORT), Handler)
    server.timeout = 300
    print(f"    Abrindo browser para login Gov.br ({issuer_id})...")
    webbrowser.open(authorize_url)
    print(f"    Aguardando redirect com code... (Ctrl+C para cancelar)")
    while not captured_code:
        server.handle_request()
    server.server_close()
    print("    Code recebido! Trocando por token...")
    tokens = exchange_code(captured_code, code_verifier)
    return tokens["access_token"]

def issue_credential(access_token, doc_type, issuer_id, context_url):
    at_claims = decode_jwt_payload(access_token)
    proof_client_id = at_claims.get("aud", "")
    c_nonce = at_claims.get("c_nonce")
    proof_jwt = make_proof_jwt(proof_client_id, c_nonce)
    url = f"{CERTIFY_URL}/issuance/credential"
    body = json.dumps({
        "format": "ldp_vc",
        "issuerId": issuer_id,
        "doctype": doc_type,
        "credential_definition": {
            "@context": [context_url],
            "type": ["VerifiableCredential", doc_type],
        },
        "proof": {"proof_type": "jwt", "jwt": proof_jwt},
    }).encode()
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
    })
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            return json.loads(r.read()), r.status
    except urllib.error.HTTPError as e:
        body_err = e.read().decode()
        try: return json.loads(body_err), e.code
        except: return {"error": body_err}, e.code

def call_vp_process(payload):
    req = urllib.request.Request(
        f"{VERIFY_URL}/v1/verify/vp-submission/vp-process",
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, context=ctx) as r:
            return r.status, r.read().decode(), None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(), e

# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------
print(f"""
{'='*80}
  FLUXO COMPLETO SEM APP: CERTIFY -> BANCO -> VERIFY -> WEBHOOK (WIREMOCK)
{'='*80}

  Certify:       {CERTIFY_URL}
  Verify:        {VERIFY_URL}
  Wiremock:      {WIREMOCK_URL}
  Credenciais:   {', '.join(credential_types)}
  Bank ID:       {BANK_ID}
""")

# ==========================================================================
# [0/4] Emitir VCs via Certify
# ==========================================================================
print("[0/4] Emitindo credenciais via Gov.br SSO + Certify...\n")
token_cache = load_token_cache()
issued_credentials = {}

for ct in credential_types:
    issuer_id, doc_type, context_url = CREDENTIAL_MAP[ct]
    print(f"  -> {ct} (issuer: {issuer_id})")
    access_token = get_cached_token(token_cache, issuer_id)
    if access_token:
        claims = decode_jwt_payload(access_token)
        print(f"     Usando token em cache (CPF: {claims.get('sub', '?')})")
    else:
        print(f"     Login necessário para {issuer_id}...")
        access_token = do_sso_login(issuer_id)
        cache_token(token_cache, issuer_id, access_token)
        claims = decode_jwt_payload(access_token)
        print(f"     Login OK (CPF: {claims.get('sub', '?')})")
    result, status = issue_credential(access_token, doc_type, issuer_id, context_url)
    if "credential" not in result:
        print(f"     ERRO {status}: {json.dumps(result, ensure_ascii=False)}")
        sys.exit(1)
    issued_credentials[ct] = result["credential"]
    print(f"     ✅ Credencial emitida (HTTP {status})")

print(f"\n  {len(issued_credentials)} credencial(is) emitida(s) com sucesso.\n")

# ==========================================================================
# [1/4] Criar VP request como banco
# ==========================================================================
print("[1/4] Verifique se bank_credentials existe no dev:")
print(f"""
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
    input("      Pressione ENTER quando pronto...")
except KeyboardInterrupt:
    print("\n      Cancelado."); sys.exit(0)

# ==========================================================================
# [2/4] Criar VP request
# ==========================================================================
print("\n[2/4] Criando VP request como banco...")

input_descriptors = [{
    "id": "id card credential",
    "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
    "constraints": {"fields": [{"path": ["$.type"], "filter": {"type": "object", "pattern": ct}}]}
} for ct in credential_types]

body = json.dumps({
    "clientId": VERIFY_URL,
    "acceptVPWithoutHolderProof": True,
    "presentationDefinition": {
        "id": "-".join(ct.lower() for ct in credential_types) + "-check",
        "purpose": "Relying party is requesting your digital ID",
        "format": {"ldp_vc": {"proof_type": ["Ed25519Signature2020"]}},
        "input_descriptors": input_descriptors,
    }
}).encode()

req = urllib.request.Request(
    f"{VERIFY_URL}/v1/verify/vp-credential-request",
    data=body,
    headers={"Content-Type": "application/json", "x-bank-id": BANK_ID, "x-bank-secret": BANK_SECRET},
    method="POST"
)
try:
    with urllib.request.urlopen(req, context=ctx) as r:
        vp_req = json.loads(r.read())
    print(f"      transactionId: {vp_req['transactionId']}")
    print(f"      requestId:     {vp_req['requestId']}")
    auth = vp_req.get("authorizationDetails", {})
    print(f"      PD id:         {auth.get('presentationDefinition', {}).get('id', '(ausente)')}")
    print(f"      acceptVPWithoutHolderProof: {auth.get('acceptVPWithoutHolderProof', '(ausente)')}")
except urllib.error.HTTPError as e:
    print(f"      ERRO {e.code}: {e.read().decode()}"); sys.exit(1)

# ==========================================================================
# [3/4] Simular wallet: montar e enviar VP
# ==========================================================================
print("\n[3/4] Simulando wallet: montando VP com VCs reais e enviando para /vp-process...")

verifiable_credentials = []
descriptor_map = []

for i, ct in enumerate(credential_types):
    issuer_id, doc_type, context_url = CREDENTIAL_MAP[ct]
    raw_vc = issued_credentials[ct]
    vc_element = dict(raw_vc)
    vc_element["vcMetadata"] = {"issuer": issuer_id, "credentialType": doc_type}
    vc_element["verifiableCredential"] = {"credential": raw_vc}
    verifiable_credentials.append(json.dumps(vc_element))
    descriptor_map.append({
        "id": "id card credential",
        "format": "ldp_vp",
        "path": f"$.verifiableCredential[{i}]",
    })

vp_token = json.dumps({
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": verifiable_credentials,
})

presentation_submission = json.dumps({
    "id": secrets.token_hex(8),
    "definition_id": vp_req["authorizationDetails"]["presentationDefinition"]["id"],
    "descriptor_map": descriptor_map,
})

payload = urllib.parse.urlencode({
    "vp_token": vp_token,
    "presentation_submission": presentation_submission,
    "state": vp_req["requestId"],
}).encode()

status1, body1, err1 = call_vp_process(payload)

if err1 is None:
    print(f"      ✅ VP aceita (HTTP {status1})")
    try:
        print(f"      vpResultStatus: {json.loads(body1).get('vpResultStatus', '?')}")
    except:
        print(f"      Resposta: {body1[:200]}")
elif status1 == 404 and "NO_VP_SUBMISSION" in body1:
    print(f"      [retry] Submission salva, aguardando commit e reenviando...")
    time.sleep(2)
    status2, body2, err2 = call_vp_process(payload)
    if err2 is None:
        print(f"      ✅ VP aceita no retry (HTTP {status2})")
        try:
            print(f"      vpResultStatus: {json.loads(body2).get('vpResultStatus', '?')}")
        except:
            print(f"      Resposta: {body2[:200]}")
    else:
        print(f"      ERRO no retry {status2}: {body2[:300]}")
        if status2 == 503:
            print("      ⚠️  Webhook falhou — verifique stubs do WireMock")
        sys.exit(1)
elif status1 == 503:
    print(f"      ERRO {status1}: {body1[:200]}")
    print("      [retry] Erro intermitente no webhook, aguardando e reenviando...")
    time.sleep(3)
    status2, body2, err2 = call_vp_process(payload)
    if err2 is None:
        print(f"      ✅ VP aceita no retry (HTTP {status2})")
        try:
            print(f"      vpResultStatus: {json.loads(body2).get('vpResultStatus', '?')}")
        except:
            print(f"      Resposta: {body2[:200]}")
    else:
        print(f"      ERRO no retry {status2}: {body2[:300]}")
        print("      ⚠️  Webhook falhou — verifique stubs do WireMock (/bank/oauth/token e /bank/webhook)")
        sys.exit(1)
else:
    print(f"      ERRO {status1}: {body1[:300]}")
    sys.exit(1)

# ==========================================================================
# [4/4] Polling de status
# ==========================================================================
print(f"\n[4/4] Aguardando confirmação de status (polling)...")
print(f"      {VERIFY_URL}/v1/verify/vp-request/{vp_req['requestId']}/status")
print("      Ctrl+C para cancelar\n")

try:
    for i in range(30):
        try:
            _, status_data = http_get(f"{VERIFY_URL}/v1/verify/vp-request/{vp_req['requestId']}/status")
            status = status_data.get("status", "UNKNOWN")
            if status == "VP_SUBMITTED":
                print(f"\n      ✅ VP_SUBMITTED confirmado!")
                _run_marker = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".last_webhook_id")
                try:
                    print(f"      Aguardando webhook no wiremock...", end=" ", flush=True)
                    time.sleep(3)
                    req_wm = urllib.request.Request(
                        f"{WIREMOCK_URL}/__admin/requests?limit=20",
                        headers={"x-wiremock-key": WIREMOCK_KEY},
                    )
                    with urllib.request.urlopen(req_wm, context=ctx, timeout=15) as r_wm:
                        wm_data = json.load(r_wm)
                    wm_reqs = [
                        e for e in wm_data.get("requests", [])
                        if "/bank/webhook" in e.get("request", {}).get("url", "")
                    ]
                    if wm_reqs:
                        latest = sorted(wm_reqs, key=lambda x: x.get("request", {}).get("loggedDate", 0), reverse=True)[0]
                        latest_id = latest.get("id", "")
                        with open(_run_marker, "w") as f:
                            f.write(latest_id)
                        print(f"✅ ID salvo ({latest_id[:8]}...)")
                    else:
                        print("⚠️  Webhook nao encontrado no journal")
                except Exception as e:
                    print(f"⚠️  Erro ao salvar ID: {e}")
                print(f"\n      Verifique o webhook no WireMock:")
                print(f"      curl -sk \"{WIREMOCK_URL}/__admin/requests?limit=5\" -H \"x-wiremock-key: {WIREMOCK_KEY}\"")
                break
            elif status == "EXPIRED":
                print(f"\n      ⚠️  Request expirou.")
                break
            else:
                print(f"      [{i*2}s] Status: {status}...", end="\r")
        except (urllib.error.HTTPError, urllib.error.URLError, OSError):
            print(f"      [{i*2}s] aguardando...", end="\r")
        time.sleep(2)
    else:
        print("\n      Timeout — sem confirmação de status.")
except KeyboardInterrupt:
    print("\n      Interrompido pelo usuário.")

print(f"""
{'='*80}
  RESUMO
{'='*80}
  transactionId:  {vp_req['transactionId']}
  requestId:      {vp_req['requestId']}
  Credenciais:    {', '.join(issued_credentials.keys())}

  WireMock (verificar resultado do webhook):
  curl -sk "{WIREMOCK_URL}/__admin/requests?limit=5" -H "x-wiremock-key: {WIREMOCK_KEY}" | python -m json.tool
{'='*80}
""")
