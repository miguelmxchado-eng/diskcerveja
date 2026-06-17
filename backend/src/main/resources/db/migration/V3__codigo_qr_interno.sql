ALTER TABLE produto
    ADD COLUMN codigo_qr VARCHAR(80);

ALTER TABLE produto
    ADD COLUMN codigo_interno VARCHAR(80);

CREATE UNIQUE INDEX uq_produto_codigo_qr
    ON produto (codigo_qr)
    WHERE codigo_qr IS NOT NULL;

CREATE UNIQUE INDEX uq_produto_codigo_interno
    ON produto (codigo_interno)
    WHERE codigo_interno IS NOT NULL;
