import {
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  ViewChild,
  effect,
  inject,
  signal,
} from '@angular/core';
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
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('readerHost') readerHost?: ElementRef<HTMLElement>;

  readonly readerId = `dcm-scanner-${Math.random().toString(36).slice(2, 9)}`;
  readonly retrying = signal(false);

  constructor() {
    effect(() => {
      const open = this.scanner.isOpen();
      if (open) {
        queueMicrotask(() => void this.bootCamera());
      } else {
        void this.engine.stop();
      }
    });
  }

  ngOnDestroy(): void {
    void this.engine.stop();
  }

  fechar(): void {
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
    } catch {
      this.scanner.setError('Não foi possível trocar a câmera.');
      this.feedback.error();
    }
  }

  private async bootCamera(): Promise<void> {
    const host = this.readerHost?.nativeElement;
    if (!host) {
      requestAnimationFrame(() => void this.bootCamera());
      return;
    }

    host.id = this.readerId;
    this.scanner.setStatus('starting');
    this.retrying.set(false);

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
      this.scanner.setStatus('scanning');
    } catch (e: unknown) {
      const msg = this.cameraErrorMessage(e);
      this.scanner.setError(msg);
      this.feedback.error();
      this.retrying.set(true);
      setTimeout(() => {
        if (this.scanner.isOpen()) {
          this.retrying.set(false);
          void this.bootCamera();
        }
      }, 2000);
    }
  }

  private cameraErrorMessage(e: unknown): string {
    const text = e instanceof Error ? e.message : String(e);
    if (/NotAllowed|Permission/i.test(text)) {
      return 'Permissão da câmera negada.';
    }
    if (/NotFound|Devices/i.test(text)) {
      return 'Nenhuma câmera encontrada.';
    }
    if (/NotReadable|in use|busy/i.test(text)) {
      return 'Câmera em uso por outro app.';
    }
    return 'Falha ao iniciar câmera. Tentando novamente…';
  }
}
