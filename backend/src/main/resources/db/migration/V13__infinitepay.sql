-- Pagamento online (InfinitePay) + flag de confirmação no pedido
ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS pagamento_confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pagamento_ref VARCHAR(80);

INSERT INTO config_sistema (chave, valor) VALUES
    ('loja.infinitepay_handle', ''),
    ('loja.public_base_url', '')
ON CONFLICT (chave) DO NOTHING;
