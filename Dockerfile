# Imagen del servicio, con la web dentro. Se construye desde la raíz del repo:
#   docker build -t tinta-server:local .

FROM node:24-trixie-slim AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

FROM rust:1.98-slim-trixie AS build
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY crates ./crates
# Las cachés guardan las dependencias ya compiladas entre construcciones.
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release --locked -p tinta-server \
    && cp target/release/tinta-server /usr/local/bin/

FROM debian:trixie-slim
# sqlite3 sirve a la copia diaria: hace un volcado coherente con `.backup`.
RUN apt-get update \
    && apt-get install -y --no-install-recommends sqlite3 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -u 1000 -m tinta \
    && mkdir /data && chown tinta:tinta /data
COPY --from=build /usr/local/bin/tinta-server /usr/local/bin/
COPY --from=web /web/dist /srv/web
USER tinta
VOLUME /data
EXPOSE 8080
CMD ["tinta-server"]
