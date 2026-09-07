-- Bairros da zona (mapa Anápolis) — match do frete por nome do bairro.
ALTER TABLE zona_entrega
    ADD COLUMN IF NOT EXISTS bairros TEXT;

ALTER TABLE zona_entrega
    ALTER COLUMN cep_prefixos DROP NOT NULL;

ALTER TABLE zona_entrega
    ALTER COLUMN cep_prefixos SET DEFAULT '';

COMMENT ON COLUMN zona_entrega.bairros IS
    'Lista de bairros (vírgula). Usado no frete do cardápio junto com o campo bairro do endereço.';
