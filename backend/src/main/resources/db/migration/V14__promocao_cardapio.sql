-- Destaque de promoções no cardápio público
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS promocao_cardapio BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE combo
    ADD COLUMN IF NOT EXISTS promocao_cardapio BOOLEAN NOT NULL DEFAULT FALSE;
