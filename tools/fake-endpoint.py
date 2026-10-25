#!/usr/bin/env python3
"""Fake OpenAI-compatible endpoint a droidremote loop verifikálásához.

Minden POST /v1/chat/completions kérést kiír a /tmp/opencode/fake-requests.jsonl-be,
és egy fix `done` akciót ad vissza. Így a loop teljes lánca (start-HOME, observe,
prompt, model client, codec, trace) modell nélkül ellenőrizhető.
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = "/tmp/opencode/fake-requests.jsonl"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith("/models"):
            body = json.dumps({"data": [{"id": "fake"}]}).encode()
            self._send(body)
        else:
            self._send(b"{}")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8", "replace")
        with open(LOG, "a") as handle:
            handle.write(raw.replace("\n", " ") + "\n")
        content = json.dumps({"a": "done", "summary": "fake ok"})
        payload = {
            "choices": [{"message": {"role": "assistant", "content": content}}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 5},
        }
        self._send(json.dumps(payload).encode())

    def _send(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    print(f"fake endpoint: http://127.0.0.1:{port}/v1", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
