# Tinta

Tinta es un servicio personal para escribir artículos con orden y planear publicaciones en redes. Lo abres en el navegador del portátil y, más adelante, en una app de Android. Los dos ven los mismos datos porque los guarda el servicio.

- **Escribir:** secciones y párrafos, con variantes de cada uno y forks del artículo entero. Tachas sin borrar, dejas notas y revisas lo que propone un agente.
- **Exportar:** un `.zip` con la estructura del portfolio (Astro), el `.md` suelto o el texto con formato para pegar en X Articles.
- **Planear:** un calendario por canal (X, LinkedIn, Instagram, el portfolio). Llega en la fase 2.

## Piezas

| Carpeta | Qué hay |
|---|---|
| `crates/tinta-server` | El servicio, en Rust con SQLite |
| `web` | La web, en Svelte con TipTap |
| `diseno` | Las maquetas aprobadas, en HTML |
| `docs` | El plan (`plan.md`) y la API (`api.md`) |
| `DESIGN.md` | El sistema visual, según [design.md](https://github.com/google-labs-code/design.md) |

## Probarlo en local

Hace falta Docker. Rust se usa dentro de un contenedor, sin instalarlo.

```sh
(cd web && npm ci && npm run build)
scripts/dev-servidor.sh        # http://127.0.0.1:8091, clave «tinta»
scripts/cargo.sh test          # pruebas del servicio
(cd web && npx vitest run)     # pruebas de la web
```

## Desplegar

La imagen se publica en `ghcr.io/ruben-salas20/tinta-server` en cada cambio de `main`. Pide tres variables de entorno: `TINTA_API_KEY` (para la app), `TINTA_WEB_PASSWORD` (para entrar desde el navegador) y, desde la fase 3, `TINTA_MCP_KEY`. Los datos viven en `/data`.
