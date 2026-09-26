-- NFC-e: vínculo do pedido com o emissor fiscal à parte
ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS cpf_na_nota VARCHAR(14),
    ADD COLUMN IF NOT EXISTS nfce_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nfce_chave VARCHAR(50),
    ADD COLUMN IF NOT EXISTS nfce_protocolo VARCHAR(30),
    ADD COLUMN IF NOT EXISTS nfce_qr_code TEXT,
    ADD COLUMN IF NOT EXISTS nfce_motivo VARCHAR(500),
    ADD COLUMN IF NOT EXISTS nfce_id_externo VARCHAR(40),
    ADD COLUMN IF NOT EXISTS nfce_emitida_em TIMESTAMPTZ;
