import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { normalizeScannedCode } from './barcode-format.util';

/**
 * Leitores USB HID emulam teclado: caracteres rápidos + Enter.
 * Ative com `startListening()` quando for suportar hardware dedicado.
 */
@Injectable({ providedIn: 'root' })
export class HidScannerService implements OnDestroy {
  readonly scan$ = new Subject<string>();

  private buffer = '';
  private lastKeyAt = 0;
  private listening = false;
  private readonly maxGapMs = 80;
  private readonly minLength = 3;

  constructor(private readonly zone: NgZone) {}

  startListening(): void {
    if (this.listening || typeof document === 'undefined') return;
    this.listening = true;
    document.addEventListener('keydown', this.onKeyDown, true);
  }

  stopListening(): void {
    if (!this.listening) return;
    this.listening = false;
    document.removeEventListener('keydown', this.onKeyDown, true);
    this.buffer = '';
  }

  ngOnDestroy(): void {
    this.stopListening();
    this.scan$.complete();
  }

  private readonly onKeyDown = (ev: KeyboardEvent): void => {
    const target = ev.target as HTMLElement | null;
    if (target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable) {
      return;
    }

    const now = Date.now();
    if (now - this.lastKeyAt > this.maxGapMs) {
      this.buffer = '';
    }
    this.lastKeyAt = now;

    if (ev.key === 'Enter') {
      const code = normalizeScannedCode(this.buffer);
      this.buffer = '';
      if (code.length >= this.minLength) {
        this.zone.run(() => this.scan$.next(code));
      }
      ev.preventDefault();
      return;
    }

    if (ev.key.length === 1 && !ev.ctrlKey && !ev.metaKey && !ev.altKey) {
      this.buffer += ev.key;
    }
  };
}
