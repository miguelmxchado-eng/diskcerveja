ALTER TABLE produto
    ADD COLUMN codigo_barras VARCHAR(80);

CREATE UNIQUE INDEX uq_produto_codigo_barras
    ON produto (codigo_barras)
    WHERE codigo_barras IS NOT NULL;
