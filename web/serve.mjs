#!/usr/bin/env node
// Tiny static file server for local PWA dev/testing — no bundler, no deps.
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { join, extname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(fileURLToPath(new URL(".", import.meta.url)), "app");
const PORT = Number(process.env.PORT ?? 8787);

const MIME = {
  ".html": "text/html", ".js": "application/javascript", ".css": "text/css",
  ".json": "application/json", ".webmanifest": "application/manifest+json",
  ".png": "image/png", ".svg": "image/svg+xml",
};

createServer(async (req, res) => {
  try {
    let path = join(ROOT, decodeURIComponent(new URL(req.url, "http://localhost").pathname));
    if ((await stat(path)).isDirectory()) path = join(path, "index.html");
    const body = await readFile(path);
    res.writeHead(200, { "Content-Type": MIME[extname(path)] ?? "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end("Not found");
  }
}).listen(PORT, () => console.log(`NoFUD PWA dev server: http://localhost:${PORT}/`));
