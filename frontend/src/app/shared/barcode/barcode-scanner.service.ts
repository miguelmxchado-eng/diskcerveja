import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';
import {
  BarcodeScanResult,
  BarcodeScannerMode,
  BarcodeScannerOpenOptions,
  ScannerUiStatus,
} from './barcode-scan.types';

@Injectable({ providedIn: 'root' })
export class BarcodeScannerService {
  readonly isOpen = signal(false);
  readonly minimized = signal(false);
  readonly status = signal<ScannerUiStatus>('idle');
  readonly mode = signal<BarcodeScannerMode>('continuous');
  readonly lastCode = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly detectFlash = signal(false);

  private readonly scanSubject = new Subject<BarcodeScanResult>();
  readonly scanned$ = this.scanSubject.asObservable();

  open(options: BarcodeScannerOpenOptions = {}): void {
    this.mode.set(options.mode ?? 'continuous');
    this.errorMessage.set(null);
    this.minimized.set(false);
    this.status.set('starting');
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.minimized.set(false);
    this.status.set('idle');
    this.lastCode.set(null);
    this.errorMessage.set(null);
  }

  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open({ mode: 'continuous' });
    }
  }

  toggleMinimize(): void {
    this.minimized.update((v) => !v);
  }

  setStatus(status: ScannerUiStatus): void {
    this.status.set(status);
  }

  setError(msg: string): void {
    this.errorMessage.set(msg);
    this.status.set('error');
  }

  flashSuccess(): void {
    this.detectFlash.set(true);
    setTimeout(() => this.detectFlash.set(false), 350);
  }

  emitScan(result: BarcodeScanResult): void {
    this.lastCode.set(result.code);
    this.scanSubject.next(result);
    if (this.mode() === 'single') {
      setTimeout(() => this.close(), 120);
    }
  }
}
