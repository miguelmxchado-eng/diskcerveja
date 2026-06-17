import {
  AfterViewInit,
  Component,
  EventEmitter,
  Inject,
  OnDestroy,
  Output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Html5Qrcode } from 'html5-qrcode';
import {
  detectBarcodeFormat,
  formatLabel,
  isPixQrPayload,
  isValidProductCode,
  normalizeScannedCode,
} from './barcode-format.util';
import { ScanDebounce } from './scan-debounce';
import { BarcodeFeedbackService } from './barcode-feedback.service';

export interface BarcodeScannerDialogData {
  title?: string;
  /** Leitura contínua (PDV) — não fecha ao escanear. */
  continuous?: boolean;
  /** Permite digitar manualmente. */
  allowManual?: boolean;
  /** Mensagem auxiliar. */
  hint?: string;
}

export type { BarcodeScanResult } from './barcode-scan.types';
import type { BarcodeScanResult } from './barcode-scan.types';

@Component({
  selector: 'app-barcode-scanner-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './barcode-scanner-dialog.component.html',
  styleUrl: './barcode-scanner-dialog.component.scss',
})
export class BarcodeScannerDialogComponent implements AfterViewInit, OnDestroy {
  @Output() readonly scanned = new EventEmitter<BarcodeScanResult>();

  readonly readerId = 'barcode-scanner-reader';
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly flashOk = signal(false);
  readonly previewCode = signal<string | null>(null);
  readonly previewFormat = signal<string | null>(null);

  manualCode = '';

  private scanner: Html5Qrcode | null = null;
  private readonly debounce = new ScanDebounce(1400);
  private destroyed = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: BarcodeScannerDialogData,
    private readonly dialogRef: MatDialogRef<BarcodeScannerDialogComponent>,
    private readonly feedback: BarcodeFeedbackService,
  ) {}

  ngAfterViewInit(): void {
    void this.startCamera();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    void this.stopCamera();
  }

  fechar(): void {
    this.dialogRef.close(null);
  }

  aplicarManual(): void {
    this.processScan(this.manualCode, true);
  }

  private async startCamera(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.scanner = new Html5Qrcode(this.readerId, { verbose: false });
      await this.scanner.start(
        { facingMode: { ideal: 'environment' } },
        {
          fps: 12,
          qrbox: (viewfinderWidth, viewfinderHeight) => {
            const minEdge = Math.min(viewfinderWidth, viewfinderHeight);
            const size = Math.floor(minEdge * 0.72);
            return { width: size, height: Math.floor(size * 0.65) };
          },
          aspectRatio: 1,
        },
        (text) => this.processScan(text, false),
        () => {},
      );
      if (!this.destroyed) {
        this.loading.set(false);
      }
    } catch (e: unknown) {
      const msg =
        e instanceof Error && e.message.includes('NotAllowed')
          ? 'Permissão da câmera negada. Libere o acesso nas configurações do navegador.'
          : 'Não foi possível iniciar a câmera. Verifique permissões ou use entrada manual.';
      this.error.set(msg);
      this.loading.set(false);
      this.feedback.error();
    }
  }

  private async stopCamera(): Promise<void> {
    if (!this.scanner) return;
    try {
      if (this.scanner.isScanning) {
        await this.scanner.stop();
      }
      this.scanner.clear();
    } catch {
      /* ignore */
    }
    this.scanner = null;
  }

  private processScan(raw: string, force: boolean): void {
    const code = normalizeScannedCode(raw);
    if (!code) return;
    if (!force && !this.debounce.accept(code)) return;

    const format = formatLabel(code);
    const isPix = isPixQrPayload(code);

    if (!isPix && !isValidProductCode(code)) {
      this.error.set('Código inválido ou não reconhecido.');
      this.feedback.error();
      return;
    }

    this.previewCode.set(code);
    this.previewFormat.set(format);
    this.error.set(null);
    this.flashOk.set(true);
    setTimeout(() => this.flashOk.set(false), 280);
    this.feedback.success();

    const result: BarcodeScanResult = { code, format: detectBarcodeFormat(code), isPix };
    this.scanned.emit(result);

    if (!this.data.continuous) {
      void this.stopCamera();
      this.dialogRef.close(result);
    }
  }
}
