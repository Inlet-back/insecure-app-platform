#!/usr/bin/env python3
"""第3章 (ネットワーク境界) 用のモックサーバ。

エミュレータから 10.0.2.2 として見えるホスト側で動かす。
  HTTP  : 8080
  HTTPS : 8443

証明書は tools/mockserver/make-teaching-certs.sh で作った教材専用のものを使う。
  ca-cert.pem             教材用 CA。アプリが trust anchor として同梱している
  server-cert.pem         有効なサーバ証明書 (既定)
  server-cert-expired.pem 同じ鍵で作った «期限切れ» のサーバ証明書

  --expired を付けると後者で待ち受ける。鍵が同じなので公開鍵ピンは «一致したまま»
  期限だけが切れる。ピン留めが通常の TLS 検証の置き換えではないことを、
  1つだけ条件を変えて確かめるための実験用。

ここに置いてある鍵と証明書は «使い捨ての教材用» であり、秘密鍵ごと公開されている。
本番はもちろん、他の検証環境にも流用しないこと。

受け取ったリクエストをそのまま標準出力に出すので、Burp を立てなくても
「何が経路に流れたか」は確認できる。起動時に公開鍵ピンも表示する。

外部には一切接続しない。待ち受けるのは localhost のみ。
"""
import argparse
import http.server
import os
import ssl
import subprocess
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
CA = os.path.join(HERE, "ca-cert.pem")
CERT = os.path.join(HERE, "server-cert.pem")
CERT_EXPIRED = os.path.join(HERE, "server-cert-expired.pem")
KEY = os.path.join(HERE, "server-key.pem")


def check_files(cert):
    missing = [p for p in (CA, cert, KEY) if not os.path.exists(p)]
    if missing:
        names = ", ".join(os.path.basename(p) for p in missing)
        sys.exit(f"[!] 証明書がない: {names}\n"
                 f"    tools/mockserver/make-teaching-certs.sh を実行すること。")


def print_pin(cert):
    """アプリの expectedPin に貼り付ける SPKI SHA-256 ピンを表示する。"""
    try:
        pub = subprocess.run(["openssl", "x509", "-in", cert, "-pubkey", "-noout"],
                             capture_output=True, check=True).stdout
        der = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "der"],
                             input=pub, capture_output=True, check=True).stdout
        dgst = subprocess.run(["openssl", "dgst", "-sha256", "-binary"],
                              input=der, capture_output=True, check=True).stdout
        b64 = subprocess.run(["base64"], input=dgst,
                             capture_output=True, check=True).stdout.decode().strip()
        print(f"[*] 公開鍵ピン (NetworkActivity.expectedPin に貼る): {b64}")
    except Exception as e:
        print(f"[!] ピンの計算に失敗: {e}")


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        scheme = "HTTPS" if isinstance(self.connection, ssl.SSLSocket) else "HTTP "
        print(f"\n=== [{scheme}] {self.command} {self.path} ===", flush=True)
        for k, v in self.headers.items():
            print(f"  {k}: {v}", flush=True)
        print(f"  ---- body ----\n  {body}", flush=True)
        payload = b'{"ok":true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = do_POST

    def log_message(self, fmt, *args):
        pass


def serve_http():
    http.server.HTTPServer(("127.0.0.1", 8080), Handler).serve_forever()


def serve_https(cert):
    httpd = http.server.HTTPServer(("127.0.0.1", 8443), Handler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    # leaf に続けて CA も送る。アプリ側は CA だけを trust anchor として持っている。
    ctx.load_cert_chain(chain_file(cert), KEY)
    httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
    httpd.serve_forever()


def chain_file(cert):
    """leaf + CA を1つにまとめた一時ファイルを作る。"""
    out = os.path.join(HERE, ".chain.pem")
    with open(out, "w") as fp:
        for src in (cert, CA):
            with open(src) as s:
                fp.write(s.read())
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--expired", action="store_true",
                    help="期限切れのサーバ証明書で待ち受ける (公開鍵ピンは同じまま)")
    args = ap.parse_args()

    cert = CERT_EXPIRED if args.expired else CERT
    check_files(cert)
    print(f"[*] サーバ証明書: {os.path.basename(cert)}"
          + ("  ← 期限切れ。ピンは一致するが通常の TLS 検証で落ちるはず" if args.expired else ""))
    print_pin(cert)
    print("[*] HTTP  http://127.0.0.1:8080  (エミュレータからは 10.0.2.2:8080)")
    print("[*] HTTPS https://127.0.0.1:8443 (エミュレータからは 10.0.2.2:8443)")
    print("[*] Ctrl-C で終了")
    threading.Thread(target=serve_http, daemon=True).start()
    try:
        serve_https(cert)
    except KeyboardInterrupt:
        sys.exit(0)
