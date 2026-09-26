# Disk Cerveja Manager

Sistema interno (ERP leve) para pedidos, estoque, entregas, caixa e vendas do dia.

## Estrutura

* `backend/` — Spring Boot 3, JWT, PostgreSQL, Flyway
* `frontend/` — Angular 17 + Angular Material
* `emissor-fiscal/` — API NFC-e (SEFAZ-GO), serviço separado
* `certs/` — certificado A1 `.pfx` para o emissor (não versionado)
* `docker-compose.yml` — Postgres + API + emissor NFC-e

## Subir com Docker

```bash
# 1) Certificado (homolog)
ln -sf ~/Downloads/seu-certificado.pfx certs/certificado.pfx
cp .env.docker.example .env   # preencha CERT_PFX_PASSWORD, NFCE_CSC, EMITENTE_IE

docker compose up --build
```

- App/API: `http://localhost:8080`
- Emissor NFC-e: `http://localhost:8090`
- Postgres: `localhost:5432` (usuário/senha `diskcerveja`)

No Angular local (`ng serve`), aponte `environment.apiUrl` para `http://localhost:8080` (Docker) ou `http://localhost:8083` (backend Maven na máquina).

## Backend sem Docker (Java 17 + Maven + Postgres)

1. Crie o banco `diskcerveja` e usuário conforme `application.yml` (ou variáveis `DB\\\_\\\*`).
2. Na pasta `backend`: `mvn spring-boot:run`

## Frontend (Node 18+)

```bash
cd frontend
npm install
npm start
```

App: `http://localhost:4200` (proxy CORS já liberado no backend).

## Usuários iniciais (seed)

|Login|Senha|Perfil|
|-|-|-|
|admin|admin123|ADMIN|
|operador|admin123|OPERADOR|
|entregador|admin123|ENTREGADOR|

## Segurança em produção

Defina `JWT\\\_SECRET` longo e aleatório e revise `caixa.obrigatorio` em `/api/config/caixa` (ADMIN).

