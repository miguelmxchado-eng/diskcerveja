-- Preço de compra da caixa/pacote exatamente como na NF (evita 3,79 virar 3,80 na edição).
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS custo_embalagem NUMERIC(12, 2);

-- Preenche a partir do custo unitário atual × unidades (quando vende caixa/unidade).
UPDATE produto
SET custo_embalagem = ROUND(custo * unidades_por_embalagem, 2)
WHERE unidades_por_embalagem IS NOT NULL
  AND unidades_por_embalagem > 1
  AND custo_embalagem IS NULL;
