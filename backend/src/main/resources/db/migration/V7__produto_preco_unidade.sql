-- Preço avulso + quantas unidades vêm na embalagem (estoque passa a ser em unidades).
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS preco_unidade NUMERIC(12, 2) NULL,
    ADD COLUMN IF NOT EXISTS unidades_por_embalagem INTEGER NULL;

ALTER TABLE pedido_item
    ADD COLUMN IF NOT EXISTS venda_unidade BOOLEAN NOT NULL DEFAULT FALSE;
