-- Bloco de anotações da loja (compartilhado entre admin e operadores)
CREATE TABLE IF NOT EXISTS anotacao (
    id          BIGSERIAL PRIMARY KEY,
    titulo      VARCHAR(120),
    texto       VARCHAR(4000) NOT NULL,
    cor         VARCHAR(20) NOT NULL DEFAULT 'AMARELO',
    fixada      BOOLEAN NOT NULL DEFAULT FALSE,
    concluida   BOOLEAN NOT NULL DEFAULT FALSE,
    autor_nome  VARCHAR(120),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_anotacao_cor CHECK (cor IN ('AMARELO', 'VERDE', 'AZUL', 'ROSA', 'BRANCO'))
);

CREATE INDEX IF NOT EXISTS idx_anotacao_concluida ON anotacao (concluida, fixada, updated_at DESC);
