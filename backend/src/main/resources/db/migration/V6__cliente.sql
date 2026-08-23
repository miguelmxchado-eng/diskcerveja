CREATE TABLE cliente (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(40),
    endereco VARCHAR(500),
    observacao VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cliente_nome ON cliente (lower(nome));
CREATE INDEX idx_cliente_telefone ON cliente (telefone);

ALTER TABLE pedido
    ADD COLUMN cliente_id BIGINT REFERENCES cliente (id);
