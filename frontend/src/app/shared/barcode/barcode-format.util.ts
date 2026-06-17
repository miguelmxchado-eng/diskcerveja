/** Formatos suportados na leitura (EAN, UPC, Code 128, QR). */
export type BarcodeFormatLabel =
  | 'EAN-13'
  | 'EAN-8'
  | 'UPC'
  | 'CODE_128'
  | 'QR_CODE'
  | 'PIX'
  | 'DESCONHECIDO';

const EAN13 = /^\d{13}$/;
const EAN8 = /^\d{8}$/;
const UPC = /^\d{12}$/;
const CODE128 = /^[\x20-\x7E]{4,80}$/;

/** EMV QR Code Pix (BR Code) costuma iniciar com 000201. */
export function isPixQrPayload(value: string): boolean {
  const v = value.trim();
  return v.startsWith('000201') && v.includes('BR.GOV.BCB.PIX');
}

export function normalizeScannedCode(raw: string): string {
  return raw.trim().replace(/\s+/g, '');
}

export function detectBarcodeFormat(code: string): BarcodeFormatLabel {
  const v = normalizeScannedCode(code);
  if (!v) return 'DESCONHECIDO';
  if (isPixQrPayload(v)) return 'PIX';
  if (EAN13.test(v)) return 'EAN-13';
  if (EAN8.test(v)) return 'EAN-8';
  if (UPC.test(v)) return 'UPC';
  if (v.length > 80) return 'DESCONHECIDO';
  if (/^https?:\/\//i.test(v) || v.length > 20) return 'QR_CODE';
  if (CODE128.test(v)) return 'CODE_128';
  return 'DESCONHECIDO';
}

export function isValidProductCode(code: string): boolean {
  const v = normalizeScannedCode(code);
  if (!v || v.length > 80) return false;
  if (isPixQrPayload(v)) return true;
  const fmt = detectBarcodeFormat(v);
  return fmt !== 'DESCONHECIDO' || v.length >= 3;
}

export function formatLabel(code: string): string {
  return detectBarcodeFormat(code);
}
