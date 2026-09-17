# tv-web

React + TypeScript + Vite frontend for web-based TV platforms (webOS now; written to be reusable
for other web-based TV platforms, e.g. Tizen, later). Not a Gradle module — a plain npm project,
same tooling conventions as `admin`.

## Browser development

```powershell
npm install
npm run dev      # Vite dev server, defaults to http://localhost:5173 (or next free port)
```

Expects the server (see `../../server`) running on `http://localhost:8080`; override via
`VITE_API_BASE_URL` in a local `.env` (see `.env.example`).

## webOS packaging

Requires LG's `ares-cli` installed and on `PATH` separately — not part of this repo.

```powershell
npm run build          # production build to dist/
npm run package:webos   # wraps `ares-package dist -o dist-ipk`
ares-install --device <device-name> dist-ipk/*.ipk
ares-launch --device <device-name> no.esotericgames.quotes.tvweb
```
