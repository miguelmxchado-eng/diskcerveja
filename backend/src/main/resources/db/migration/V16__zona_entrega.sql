-- Zonas de entrega por CEP (prefixo ou faixa)
CREATE TABLE IF NOT EXISTS zona_entrega (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(80) NOT NULL,
    taxa            NUMERIC(12, 2) NOT NULL,
    cep_prefixos    VARCHAR(500) NOT NULL,
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    ordem           INT NOT NULL DEFAULT 0,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_zona_entrega_ativo_ordem ON zona_entrega (ativo, ordem);

COMMENT ON COLUMN zona_entrega.cep_prefixos IS
    'Lista separada por vírgula: prefixo (74000) ou faixa (74000-74099). CEP com 8 dígitos.';
