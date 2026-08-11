"""
Extrai PDFs do webhook do banco a partir do journal do WireMock.

Usa o ID do ultimo webhook salvo pelo simulate-bank-flow-full.py (.last_webhook_id).

Uso:
  python extract-webhook-pdf.py              # usa .last_webhook_id automaticamente
  python extract-webhook-pdf.py <id>         # busca pelo ID especifico do wiremock

Exemplos:
  python extract-webhook-pdf.py
  python extract-webhook-pdf.py a890ef8c-6077-45b3-b884-ffd51ac520db
"""
import json, base64, re, os, sys, urllib.request, ssl

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

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
load_env(os.path.join(SCRIPTS_DIR, ".env"))

WIREMOCK_URL = os.environ.get("WIREMOCK_URL", "")
WIREMOCK_KEY = os.environ.get("WIREMOCK_KEY", "")
def _default_output_dir():
    home = os.path.expanduser("~")
    for name in ("Desktop", "Área de Trabalho"):
        candidate = os.path.join(home, name)
        if os.path.isdir(candidate):
            return candidate
    return home

OUTPUT_DIR = os.environ.get("OUTPUT_DIR") or _default_output_dir()
MARKER_FILE  = os.path.join(SCRIPTS_DIR, ".last_webhook_id")

if not WIREMOCK_URL:
    print("Erro: WIREMOCK_URL nao definido no .env")
    sys.exit(1)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def fetch_by_id(webhook_id):
    """Busca uma request especifica pelo ID no wiremock."""
    req = urllib.request.Request(
        f"{WIREMOCK_URL}/__admin/requests/{webhook_id}",
        headers={"x-wiremock-key": WIREMOCK_KEY, "Connection": "close"},
    )
    with urllib.request.urlopen(req, context=ctx, timeout=60) as r:
        chunks = []
        while True:
            chunk = r.read(65536)
            if not chunk:
                break
            chunks.append(chunk)
        return json.loads(b"".join(chunks))

def extract_pdfs(request_data, webhook_id):
    """Extrai e salva PDFs e JSON do body multipart."""
    body_b64 = request_data.get("bodyAsBase64", "")
    ct = request_data.get("headers", {}).get("Content-Type", "")
    if not body_b64:
        print("Body vazio.")
        return 0
    body = base64.b64decode(body_b64)

    m = re.search(r"boundary=([^\s;\"]+)", ct)
    if not m:
        print("Body nao e multipart.")
        return 0
    boundary = ("--" + m.group(1)).encode()
    saved = 0
    part_idx = 0
    for i, part in enumerate(body.split(boundary)):
        idx_body = part.find(b"\r\n\r\n")
        if idx_body < 0:
            continue
        content = re.sub(rb"--$", b"", part[idx_body+4:].rstrip(b"\r\n-"))
        if b"filename" in part:
            fm = re.search(rb'filename="([^"]+)"', part)
            fname = fm.group(1).decode() if fm else f"parte_{i}.bin"
            out = os.path.join(OUTPUT_DIR, fname)
            with open(out, "wb") as f:
                f.write(content)
            print(f"Salvo PDF: {out} ({len(content)} bytes)")
            saved += 1
        elif content.strip():
            try:
                data = json.loads(content)
                fname = f"webhook_result_{webhook_id[:8]}_{part_idx}.json"
                out = os.path.join(OUTPUT_DIR, fname)
                with open(out, "w", encoding="utf-8") as f:
                    json.dump(data, f, indent=2, ensure_ascii=False)
                print(f"Salvo JSON: {out}")
            except Exception:
                fname = f"webhook_part_{webhook_id[:8]}_{part_idx}.txt"
                out = os.path.join(OUTPUT_DIR, fname)
                with open(out, "wb") as f:
                    f.write(content)
                print(f"Salvo parte raw: {out} ({len(content)} bytes)")
            part_idx += 1
    if saved == 0:
        print("Nenhum PDF encontrado.")
    return saved

# --- Determina o ID a buscar ---
if len(sys.argv) > 1:
    webhook_id = sys.argv[1]
elif os.path.exists(MARKER_FILE):
    webhook_id = open(MARKER_FILE).read().strip()
    if not webhook_id:
        print("Erro: .last_webhook_id esta vazio. Rode simulate-bank-flow-full.py primeiro.")
        sys.exit(1)
    print(f"Usando ultimo webhook: {webhook_id}")
else:
    print("Nenhum webhook salvo. Rode simulate-bank-flow-full.py ou passe o ID como argumento.")
    print(f"Uso: python extract-webhook-pdf.py <id>")
    sys.exit(1)

# --- Busca e extrai ---
print(f"Buscando webhook {webhook_id[:8]}...", end=" ", flush=True)
try:
    full = fetch_by_id(webhook_id)
except Exception as e:
    print(f"Erro: {e}")
    sys.exit(1)

req_data = full.get("request", full)
print(f"ok ({req_data.get('loggedDateString', '')})")
extract_pdfs(req_data, webhook_id)
