-- Preço de compra obrigatório no cadastro; snapshot do custo no item do pedido (lucro real).
UPDATE produto SET custo = 0 WHERE custo IS NULL;

-- Custos de demonstração só onde ainda não havia preço de compra.
UPDATE produto SET custo = 5.50 WHERE nome = 'Heineken 600ml' AND custo = 0;
UPDATE produto SET custo = 3.80 WHERE nome = 'Skol 1L' AND custo = 0;
UPDATE produto SET custo = 7.50 WHERE nome = 'Coca 2L' AND custo = 0;
UPDATE produto SET custo = 9.00 WHERE nome = 'Batata frita' AND custo = 0;

ALTER TABLE produto
    ALTER COLUMN custo SET DEFAULT 0,
    ALTER COLUMN custo SET NOT NULL;

ALTER TABLE pedido_item
    ADD COLUMN IF NOT EXISTS custo_unitario NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE pedido_item pi
SET custo_unitario = COALESCE(p.custo, 0)
FROM produto p
WHERE pi.produto_id = p.id;

UPDATE pedido_item pi
SET custo_unitario = COALESCE((
    SELECT SUM(COALESCE(pr.custo, 0) * ci.quantidade)
    FROM combo_item ci
    JOIN produto pr ON pr.id = ci.produto_id
    WHERE ci.combo_id = pi.combo_id
), 0)
WHERE pi.combo_id IS NOT NULL;
