export interface BarcodeScanResult {
  code: string;
  format: string;
  isPix: boolean;
}

export type BarcodeScannerMode = 'continuous' | 'single';

export interface BarcodeScannerOpenOptions {
  mode?: BarcodeScannerMode;
}

export type ScannerUiStatus = 'idle' | 'starting' | 'scanning' | 'paused' | 'success' | 'error';
