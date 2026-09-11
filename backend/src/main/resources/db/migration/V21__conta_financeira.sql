-- Contas a pagar e a receber (lançamentos manuais simples)
CREATE TABLE IF NOT EXISTS conta_financeira (
    id              BIGSERIAL PRIMARY KEY,
    tipo            VARCHAR(20) NOT NULL,
    descricao       VARCHAR(200) NOT NULL,
    pessoa          VARCHAR(120),
    valor           NUMERIC(12, 2) NOT NULL,
    vencimento      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    data_pagamento  DATE,
    forma_pagamento VARCHAR(20),
    observacao      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_conta_tipo CHECK (tipo IN ('PAGAR', 'RECEBER')),
    CONSTRAINT chk_conta_status CHECK (status IN ('ABERTA', 'QUITADA', 'CANCELADA')),
    CONSTRAINT chk_conta_valor CHECK (valor > 0)
);

CREATE INDEX IF NOT EXISTS idx_conta_fin_tipo_status ON conta_financeira (tipo, status);
CREATE INDEX IF NOT EXISTS idx_conta_fin_vencimento ON conta_financeira (vencimento);
