import { Injectable } from '@angular/core';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import {
  detectBarcodeFormat,
  isPixQrPayload,
  isValidProductCode,
  normalizeScannedCode,
} from './barcode-format.util';
import { ScanDebounce } from './scan-debounce';

const MAX_RETRIES = 2;

/** Formatos aceitos: EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39 (+ QR). */
const POSSIBLE_FORMATS: BarcodeFormat[] = [
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.UPC_A,
  BarcodeFormat.UPC_E,
  BarcodeFormat.CODE_128,
  BarcodeFormat.CODE_39,
  BarcodeFormat.QR_CODE,
];

export type ScanCallback = (code: string, format: string, isPix: boolean) => void;

/**
 * Motor de leitura reutilizável baseado em `@zxing/browser`.
 * Uma única instância ativa por vez (garantido por `startGen` + `releaseScanner`).
 * Recebe o elemento <video> e emite o texto detectado via callback.
 */
@Injectable({ providedIn: 'root' })
export class BarcodeScannerEngineService {
  private reader: BrowserMultiFormatReader | null = null;
  private controls: IScannerControls | undefined;
  private videoEl: HTMLVideoElement | null = null;
  private running = false;
  private destroyed = false;
  private startGen = 0;
  private useEnvironment = true;
  private readonly debounce = new ScanDebounce(1100, 500);
  private onScan: ScanCallback | null = null;

  async start(video: HTMLVideoElement, onScan: ScanCallback): Promise<void> {
    if (typeof window !== 'undefined' && !window.isSecureContext) {
      throw new Error('SECURE_CONTEXT');
    }
    if (!video) {
      throw new Error('READER_NOT_READY');
    }

    const gen = ++this.startGen;
    await this.releaseScanner();
    if (gen !== this.startGen) return;

    this.destroyed = false;
    this.videoEl = video;
    this.onScan = onScan;
    this.debounce.reset();
    console.log('Iniciando ZXing');
    console.log('Elemento de vídeo:', video);
    await this.startWithRetry(0, gen);
  }

  async stop(): Promise<void> {
    this.startGen++;
    this.destroyed = true;
    this.running = false;
    await this.releaseScanner();
  }

  async flipCamera(): Promise<void> {
    const video = this.videoEl;
    const cb = this.onScan;
    if (!video || !cb) return;
    this.useEnvironment = !this.useEnvironment;
    await this.start(video, cb);
  }

  isActive(): boolean {
    return this.running;
  }

  private async startWithRetry(attempt: number, gen: number): Promise<void> {
    if (this.destroyed || gen !== this.startGen || !this.videoEl || !this.onScan) return;
    try {
      const hints = new Map<DecodeHintType, unknown>();
      hints.set(DecodeHintType.POSSIBLE_FORMATS, POSSIBLE_FORMATS);
      // TRY_HARDER melhora a detecção de 1D em quadros ruidosos.
      hints.set(DecodeHintType.TRY_HARDER, true);

      this.reader = new BrowserMultiFormatReader(hints, {
        // Leitura contínua rápida, sem sobrecarregar a CPU.
        delayBetweenScanAttempts: 100,
        delayBetweenScanSuccess: 300,
      });

      // Câmera traseira preferencial + HD (resolve as barras finas de EAN/UPC).
      const constraints: MediaStreamConstraints = {
        video: {
          facingMode: { ideal: this.useEnvironment ? 'environment' : 'user' },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      };

      this.controls = await this.reader.decodeFromConstraints(
        constraints,
        this.videoEl,
        (result, error) => this.handleResult(result, error),
      );

      if (this.destroyed || gen !== this.startGen) {
        await this.releaseScanner();
        return;
      }
      this.running = true;
      console.log('Câmera iniciada');
    } catch (e) {
      await this.releaseScanner();
      if (attempt < MAX_RETRIES && !this.destroyed && gen === this.startGen) {
        // Segunda tentativa: relaxa para a câmera padrão do aparelho.
        this.useEnvironment = attempt === 0 ? this.useEnvironment : true;
        await this.delay(350 * (attempt + 1));
        return this.startWithRetry(attempt + 1, gen);
      }
      throw e;
    }
  }

  /**
   * Callback de leitura contínua. `error` (NotFoundException) chega em cada
   * frame sem código — é normal e não deve virar log/erro na tela.
   */
  private handleResult(result: unknown, _error: unknown): void {
    if (this.destroyed || !this.onScan) return;
    const res = result as { getText?: () => string } | undefined;
    if (!res || typeof res.getText !== 'function') return;

    const code = normalizeScannedCode(res.getText() ?? '');
    if (!code) return;
    if (!this.debounce.accept(code)) return;

    const isPix = isPixQrPayload(code);
    if (!isPix && !isValidProductCode(code)) return;

    this.debounce.pauseAfterRead();
    console.log('Código detectado:', code);
    const format = detectBarcodeFormat(code);
    this.onScan(code, format, isPix);
  }

  /** Parada segura: encerra o loop, libera as tracks e limpa o vídeo. */
  private async releaseScanner(): Promise<void> {
    this.running = false;
    try {
      this.controls?.stop();
    } catch (e) {
      console.error('Erro ao parar ZXing:', e);
    }
    this.controls = undefined;

    const video = this.videoEl;
    const stream = (video?.srcObject as MediaStream | null) ?? null;
    stream?.getTracks().forEach((track) => {
      try {
        track.stop();
      } catch {
        /* ignore */
      }
    });
    if (video) {
      video.srcObject = null;
    }
    this.reader = null;
    console.log('Encerrando câmera');
  }

  private delay(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }
}
