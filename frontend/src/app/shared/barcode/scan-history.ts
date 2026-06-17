import { formatLabel } from './barcode-format.util';

export interface ScanHistoryEntry {
  code: string;
  format: string;
  at: number;
  nome?: string;
  ok: boolean;
}

const MAX = 8;

export class ScanHistory {
  private entries: ScanHistoryEntry[] = [];

  push(code: string, ok: boolean, nome?: string): ScanHistoryEntry {
    const entry: ScanHistoryEntry = {
      code,
      format: formatLabel(code),
      at: Date.now(),
      nome,
      ok,
    };
    this.entries = [entry, ...this.entries.filter((e) => e.code !== code)].slice(0, MAX);
    return entry;
  }

  list(): ScanHistoryEntry[] {
    return [...this.entries];
  }

  clear(): void {
    this.entries = [];
  }
}
