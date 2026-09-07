-- Permite data URL / base64 da foto do produto (mesmo padrão dos combos).
ALTER TABLE produto
    ALTER COLUMN imagem_url TYPE TEXT;
