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

`VITE_API_BASE_URL` is baked in at build time, so for a device build point it at your dev
machine's LAN IP, not `localhost` — on the TV, `localhost` resolves to the TV itself. Set it in
`.env` before building (e.g. `VITE_API_BASE_URL=http://192.168.1.45:8080`), and make sure the
server (`../../server`) is running and reachable on that IP/port — it binds `0.0.0.0` by default,
but a firewall on the dev machine may need to allow inbound connections on the port.

```powershell
npm run build          # production build to dist/
npm run package:webos   # wraps `ares-package dist -o dist-ipk`
ares-install --device <device-name> dist-ipk/*.ipk
ares-launch --device <device-name> no.esotericgames.quotes.tvweb
```
