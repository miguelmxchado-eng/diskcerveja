# Disk Cerveja Manager

Sistema interno (ERP leve) para pedidos, estoque, entregas, caixa e vendas do dia.

## Estrutura

* `backend/` — Spring Boot 3, JWT, PostgreSQL, Flyway
* `frontend/` — Angular 17 + Angular Material
* `docker-compose.yml` — PostgreSQL + API (build com Maven dentro do container)

## Subir com Docker

Na pasta `disk-cerveja-manager`:

```bash
docker compose up --build
```

API: `http://localhost:8080` · Postgres: `localhost:5432` (usuário/senha `diskcerveja`).

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

