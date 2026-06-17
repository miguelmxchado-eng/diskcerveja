import { Subscription } from 'rxjs';
import { BarcodeScannerService } from './barcode-scanner.service';
import { BarcodeScanResult, BarcodeScannerOpenOptions } from './barcode-scan.types';

export interface LegacyScannerOptions extends BarcodeScannerOpenOptions {
  title?: string;
  hint?: string;
  continuous?: boolean;
}

/**
 * Abre o mini-scanner flutuante. Passe `BarcodeScannerService` injetado no componente.
 * O parâmetro `dialog` é ignorado (mantido por compatibilidade).
 */
export function openBarcodeScanner(
  scannerOrDialog: BarcodeScannerService | unknown,
  options: LegacyScannerOptions = {},
  onScan?: (result: BarcodeScanResult) => void,
): { close: () => void } {
  const scanner = scannerOrDialog as BarcodeScannerService;
  const mode = options.continuous === false ? 'single' : 'continuous';
  scanner.open({ mode });

  let sub: Subscription | undefined;
  if (onScan) {
    sub = scanner.scanned$.subscribe((r) => onScan(r));
  }

  return {
    close: () => {
      sub?.unsubscribe();
      scanner.close();
    },
  };
}
