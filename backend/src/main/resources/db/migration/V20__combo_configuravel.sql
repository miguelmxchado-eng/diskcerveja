-- Combos configuráveis (montar copão) + observação por item do pedido
ALTER TABLE combo
    ADD COLUMN IF NOT EXISTS configuravel BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS combo_opcao_grupo (
    id          BIGSERIAL PRIMARY KEY,
    combo_id    BIGINT NOT NULL REFERENCES combo (id) ON DELETE CASCADE,
    nome        VARCHAR(120) NOT NULL,
    obrigatorio BOOLEAN NOT NULL DEFAULT TRUE,
    minimo      INT NOT NULL DEFAULT 1,
    maximo      INT NOT NULL DEFAULT 1,
    ordem       INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_combo_opcao_grupo_combo ON combo_opcao_grupo (combo_id);

CREATE TABLE IF NOT EXISTS combo_opcao (
    id          BIGSERIAL PRIMARY KEY,
    grupo_id    BIGINT NOT NULL REFERENCES combo_opcao_grupo (id) ON DELETE CASCADE,
    rotulo      VARCHAR(120) NOT NULL,
    produto_id  BIGINT REFERENCES produto (id),
    ordem       INT NOT NULL DEFAULT 0,
    ativo       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_combo_opcao_grupo ON combo_opcao (grupo_id);

ALTER TABLE pedido_item
    ADD COLUMN IF NOT EXISTS observacao VARCHAR(500);
