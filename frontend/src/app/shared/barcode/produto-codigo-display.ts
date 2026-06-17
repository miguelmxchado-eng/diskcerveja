import { Produto } from '../../core/models';

/** Código principal exibido na listagem (barras > QR > interno). */
export function codigoPrincipal(p: Produto): string | null {
  return p.codigoBarras?.trim() || p.codigoQr?.trim() || p.codigoInterno?.trim() || null;
}

export function produtoCombinaBusca(p: Produto, termo: string): boolean {
  const q = termo.trim().toLowerCase();
  if (!q) return true;
  const nome = p.nome.toLowerCase();
  const codes = [p.codigoBarras, p.codigoQr, p.codigoInterno]
    .filter(Boolean)
    .map((c) => c!.toLowerCase());
  return nome.includes(q) || codes.some((c) => c.includes(q) || c === q);
}
