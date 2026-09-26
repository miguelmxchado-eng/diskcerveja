-- Dados fiscais opcionais por produto (NFC-e). Vazios usam o default do emissor.
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS ncm VARCHAR(8),
    ADD COLUMN IF NOT EXISTS cfop VARCHAR(4),
    ADD COLUMN IF NOT EXISTS csosn VARCHAR(4);
