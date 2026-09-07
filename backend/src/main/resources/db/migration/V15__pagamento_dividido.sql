CREATE TABLE pedido_pagamento (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedido (id) ON DELETE CASCADE,
    forma_pagamento VARCHAR(20) NOT NULL,
    valor NUMERIC(14, 2) NOT NULL,
    valor_recebido NUMERIC(14, 2),
    CONSTRAINT ck_pedido_pagamento_valor_positivo CHECK (valor > 0),
    CONSTRAINT ck_pedido_pagamento_recebido CHECK (
        valor_recebido IS NULL OR valor_recebido >= valor
    )
);

CREATE INDEX idx_pedido_pagamento_pedido ON pedido_pagamento (pedido_id);
CREATE INDEX idx_pedido_pagamento_forma ON pedido_pagamento (forma_pagamento);

INSERT INTO pedido_pagamento (pedido_id, forma_pagamento, valor)
SELECT id, forma_pagamento, total
FROM pedido
WHERE total > 0;

ALTER TABLE movimento_caixa
    ADD COLUMN forma_pagamento VARCHAR(20);

UPDATE movimento_caixa m
SET forma_pagamento = p.forma_pagamento
FROM pedido p
WHERE m.pedido_id = p.id
  AND m.tipo = 'ENTRADA_VENDA';

CREATE INDEX idx_movimento_caixa_forma_pagamento
    ON movimento_caixa (forma_pagamento);
