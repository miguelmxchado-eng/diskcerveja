CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    login VARCHAR(80) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE produto (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    preco NUMERIC(12, 2) NOT NULL,
    custo NUMERIC(12, 2),
    estoque_atual INTEGER NOT NULL DEFAULT 0,
    estoque_minimo INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE pedido (
    id BIGSERIAL PRIMARY KEY,
    data_hora TIMESTAMPTZ NOT NULL,
    cliente_nome VARCHAR(255),
    telefone VARCHAR(40),
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    forma_pagamento VARCHAR(20) NOT NULL,
    endereco_entrega VARCHAR(500),
    estoque_baixado BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_id BIGINT REFERENCES usuario (id)
);

CREATE TABLE pedido_item (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    quantidade INTEGER NOT NULL,
    preco_unitario NUMERIC(12, 2) NOT NULL
);

CREATE TABLE entrega (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL UNIQUE REFERENCES pedido (id) ON DELETE CASCADE,
    entregador_id BIGINT REFERENCES usuario (id),
    entregador_nome VARCHAR(120),
    taxa_entrega NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    horario_saida TIMESTAMPTZ,
    horario_entrega TIMESTAMPTZ
);

CREATE TABLE caixa_sessao (
    id BIGSERIAL PRIMARY KEY,
    data_referencia DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    hora_abertura TIMESTAMPTZ NOT NULL,
    hora_fechamento TIMESTAMPTZ,
    valor_abertura NUMERIC(14, 2) NOT NULL DEFAULT 0,
    valor_fechamento NUMERIC(14, 2),
    usuario_abertura_id BIGINT NOT NULL REFERENCES usuario (id),
    usuario_fechamento_id BIGINT REFERENCES usuario (id)
);

CREATE UNIQUE INDEX uq_caixa_sessao_data_aberto ON caixa_sessao (data_referencia) WHERE status = 'ABERTO';

CREATE TABLE movimento_caixa (
    id BIGSERIAL PRIMARY KEY,
    caixa_sessao_id BIGINT NOT NULL REFERENCES caixa_sessao (id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    valor NUMERIC(14, 2) NOT NULL,
    descricao VARCHAR(255),
    pedido_id BIGINT REFERENCES pedido (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE movimento_estoque (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    tipo VARCHAR(20) NOT NULL,
    quantidade INTEGER NOT NULL,
    pedido_id BIGINT REFERENCES pedido (id),
    motivo VARCHAR(255),
    usuario_id BIGINT REFERENCES usuario (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE config_sistema (
    chave VARCHAR(80) PRIMARY KEY,
    valor VARCHAR(500) NOT NULL
);

INSERT INTO config_sistema (chave, valor) VALUES ('caixa.obrigatorio', 'true')
ON CONFLICT (chave) DO NOTHING;

CREATE INDEX idx_pedido_status ON pedido (status);
CREATE INDEX idx_pedido_data ON pedido (data_hora);
CREATE INDEX idx_movimento_caixa_sessao ON movimento_caixa (caixa_sessao_id);
CREATE INDEX idx_movimento_estoque_produto ON movimento_estoque (produto_id);
