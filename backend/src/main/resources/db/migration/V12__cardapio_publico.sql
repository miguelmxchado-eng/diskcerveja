-- Cardápio público (versão cliente)
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS visivel_cardapio BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS descricao_cardapio VARCHAR(500),
    ADD COLUMN IF NOT EXISTS imagem_url VARCHAR(500);

ALTER TABLE combo
    ADD COLUMN IF NOT EXISTS visivel_cardapio BOOLEAN NOT NULL DEFAULT TRUE;

-- Configuração da loja (cardápio)
INSERT INTO config_sistema (chave, valor) VALUES
    ('loja.nome', 'Empório Machado'),
    ('loja.whatsapp', ''),
    ('loja.aberta', 'true'),
    ('loja.horario', 'Seg–Dom · 18h às 02h'),
    ('loja.taxa_entrega', '5.00'),
    ('loja.pedido_minimo', '20.00'),
    ('loja.info', 'Delivery e retirada. Consulte a área de atendimento.')
ON CONFLICT (chave) DO NOTHING;
