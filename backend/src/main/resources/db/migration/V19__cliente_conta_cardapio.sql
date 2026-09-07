-- Conta do cliente no cardápio público (WhatsApp + senha) e endereço estruturado.
ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS senha_hash VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cep VARCHAR(9),
    ADD COLUMN IF NOT EXISTS logradouro VARCHAR(200),
    ADD COLUMN IF NOT EXISTS numero VARCHAR(30),
    ADD COLUMN IF NOT EXISTS complemento VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bairro VARCHAR(120),
    ADD COLUMN IF NOT EXISTS cidade VARCHAR(120),
    ADD COLUMN IF NOT EXISTS uf VARCHAR(2);
