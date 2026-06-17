CREATE TABLE combo (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    codigo VARCHAR(80),
    codigo_barras VARCHAR(80),
    codigo_qr VARCHAR(80),
    categoria VARCHAR(30) NOT NULL,
    descricao VARCHAR(2000),
    imagem TEXT,
    preco_venda NUMERIC(12, 2) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_combo_codigo
    ON combo (codigo)
    WHERE codigo IS NOT NULL;

CREATE UNIQUE INDEX uq_combo_codigo_barras
    ON combo (codigo_barras)
    WHERE codigo_barras IS NOT NULL;

CREATE UNIQUE INDEX uq_combo_codigo_qr
    ON combo (codigo_qr)
    WHERE codigo_qr IS NOT NULL;

CREATE TABLE combo_item (
    id BIGSERIAL PRIMARY KEY,
    combo_id BIGINT NOT NULL REFERENCES combo (id) ON DELETE CASCADE,
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    quantidade INTEGER NOT NULL
);

CREATE INDEX ix_combo_item_combo ON combo_item (combo_id);
CREATE INDEX ix_combo_item_produto ON combo_item (produto_id);

-- pedido_item passa a suportar linhas de combo (agregador) além de produto avulso.
ALTER TABLE pedido_item ALTER COLUMN produto_id DROP NOT NULL;
ALTER TABLE pedido_item ADD COLUMN combo_id BIGINT REFERENCES combo (id);
ALTER TABLE pedido_item ADD COLUMN descricao VARCHAR(255);

-- Integridade: cada item é OU produto OU combo (exatamente um).
ALTER TABLE pedido_item
    ADD CONSTRAINT ck_pedido_item_produto_ou_combo
    CHECK (
        (produto_id IS NOT NULL AND combo_id IS NULL)
        OR (produto_id IS NULL AND combo_id IS NOT NULL)
    );

CREATE INDEX ix_pedido_item_combo ON pedido_item (combo_id);
