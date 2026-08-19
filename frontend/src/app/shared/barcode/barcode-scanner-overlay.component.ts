import { Component, ElementRef, OnDestroy, ViewChild, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { BarcodeScannerService } from './barcode-scanner.service';
import { BarcodeScannerEngineService } from './barcode-scanner-engine.service';
import { BarcodeFeedbackService } from './barcode-feedback.service';
import { BarcodeScanResult } from './barcode-scan.types';

@Component({
  selector: 'app-barcode-scanner-overlay',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './barcode-scanner-overlay.component.html',
  styleUrl: './barcode-scanner-overlay.component.scss',
})
export class BarcodeScannerOverlayComponent implements OnDestroy {
  readonly scanner = inject(BarcodeScannerService);
  private readonly engine = inject(BarcodeScannerEngineService);
  private readonly feedback = inject(BarcodeFeedbackService);

  readonly readerId = `dcm-scanner-${Math.random().toString(36).slice(2, 9)}`;
  readonly retrying = signal(false);
  private bootSeq = 0;
  private hostEl?: HTMLElement;
  private bootInFlight = false;

  @ViewChild('readerHost')
  set readerHost(ref: ElementRef<HTMLElement> | undefined) {
    this.hostEl = ref?.nativeElement;
    if (
      this.hostEl &&
      this.scanner.isOpen() &&
      !this.scanner.minimized() &&
      !this.bootInFlight &&
      !this.engine.isActive()
    ) {
      void this.bootCamera();
    }
  }

  constructor() {
    effect(() => {
      const open = this.scanner.isOpen();
      const minimized = this.scanner.minimized();
      if (!open || minimized) {
        this.bootSeq++;
        this.bootInFlight = false;
        void this.engine.stop();
      }
    });
  }

  ngOnDestroy(): void {
    this.bootSeq++;
    this.bootInFlight = false;
    void this.engine.stop();
  }

  fechar(): void {
    this.bootSeq++;
    this.bootInFlight = false;
    void this.engine.stop();
    this.scanner.close();
  }

  minimizar(): void {
    this.scanner.toggleMinimize();
  }

  async trocarCamera(): Promise<void> {
    this.scanner.setStatus('starting');
    try {
      await this.engine.flipCamera();
      this.scanner.setStatus('scanning');
    } catch (e: unknown) {
      this.scanner.setError(this.cameraErrorMessage(e));
      this.feedback.error();
    }
  }

  retryCamera(): void {
    void this.bootCamera();
  }

  private async bootCamera(): Promise<void> {
    const seq = ++this.bootSeq;
    this.bootInFlight = true;
    const ready = await this.waitForReader(seq);
    if (seq !== this.bootSeq || !this.scanner.isOpen() || this.scanner.minimized()) {
      if (seq === this.bootSeq) this.bootInFlight = false;
      return;
    }
    if (!ready) {
      this.bootInFlight = false;
      this.retrying.set(false);
      this.scanner.setError('Área da câmera ainda não ficou pronta. Toque em Tentar novamente.');
      return;
    }

    this.scanner.setStatus('starting');
    this.retrying.set(true);

    try {
      await this.engine.start(this.readerId, (code, format, isPix) => {
        const result: BarcodeScanResult = { code, format, isPix };
        this.scanner.flashSuccess();
        this.scanner.setStatus('success');
        this.scanner.emitScan(result);
        setTimeout(() => {
          if (this.scanner.isOpen()) {
            this.scanner.setStatus('scanning');
          }
        }, 500);
      });
      if (seq !== this.bootSeq) return;
      this.retrying.set(false);
      this.bootInFlight = false;
      this.scanner.setStatus('scanning');
    } catch (e: unknown) {
      if (seq !== this.bootSeq) return;
      this.retrying.set(false);
      this.bootInFlight = false;
      this.scanner.setError(this.cameraErrorMessage(e));
      this.feedback.error();
    }
  }

  private waitForReader(seq: number): Promise<boolean> {
    const deadline = Date.now() + 4000;
    return new Promise((resolve) => {
      const tick = () => {
        if (seq !== this.bootSeq) {
          resolve(false);
          return;
        }
        const el = document.getElementById(this.readerId);
        if (el && el.offsetWidth > 8 && el.offsetHeight > 8) {
          resolve(true);
          return;
        }
        if (Date.now() > deadline) {
          resolve(false);
          return;
        }
        requestAnimationFrame(tick);
      };
      tick();
    });
  }

  private cameraErrorMessage(e: unknown): string {
    const text = e instanceof Error ? e.message : String(e);
    if (/SECURE_CONTEXT/i.test(text)) {
      return 'A câmera só funciona em HTTPS. Abra o sistema pelo endereço seguro (cadeado no navegador).';
    }
    if (/READER_NOT_READY/i.test(text)) {
      return 'Área da câmera ainda não ficou pronta. Toque em Tentar novamente.';
    }
    if (/NotAllowed|Permission|denied/i.test(text)) {
      return 'Permissão da câmera negada. Autorize a câmera neste site e tente de novo.';
    }
    if (/NotFound|DevicesNotFound|Requested device not found/i.test(text)) {
      return 'Nenhuma câmera encontrada neste aparelho.';
    }
    if (/NotReadable|in use|AbortError|busy/i.test(text)) {
      return 'Câmera em uso por outro app. Feche-o e tente de novo.';
    }
    if (/Overconstrained|constraint/i.test(text)) {
      return 'Esta câmera não aceitou as configurações. Toque em Tentar novamente.';
    }
    return 'Falha ao iniciar a câmera. Verifique a permissão e tente novamente.';
  }
}
