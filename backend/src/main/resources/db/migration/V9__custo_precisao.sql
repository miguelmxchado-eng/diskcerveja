-- Custo unitário com mais casas: compra da caixa ÷ N não perde centavos ao reabrir o cadastro
-- (ex.: R$ 3,79 ÷ 5 = 0,758 → na tela volta R$ 3,79 e não R$ 3,80).
ALTER TABLE produto
    ALTER COLUMN custo TYPE NUMERIC(12, 4)
    USING ROUND(custo::numeric, 4);
