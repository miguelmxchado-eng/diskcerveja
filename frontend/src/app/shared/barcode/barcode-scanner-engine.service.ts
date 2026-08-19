import { Injectable } from '@angular/core';
import { Html5Qrcode, Html5QrcodeCameraScanConfig } from 'html5-qrcode';
import {
  detectBarcodeFormat,
  isPixQrPayload,
  isValidProductCode,
  normalizeScannedCode,
} from './barcode-format.util';
import { ScanDebounce } from './scan-debounce';

const CAMERA_KEY = 'dcm_last_camera_id';
const MAX_RETRIES = 2;

export type ScanCallback = (code: string, format: string, isPix: boolean) => void;

@Injectable({ providedIn: 'root' })
export class BarcodeScannerEngineService {
  private scanner: Html5Qrcode | null = null;
  private readerId: string | null = null;
  private running = false;
  private destroyed = false;
  private startGen = 0;
  private cameraId: string | null = null;
  private useEnvironment = true;
  private readonly debounce = new ScanDebounce(1100, 500);
  private onScan: ScanCallback | null = null;

  async start(readerElementId: string, onScan: ScanCallback): Promise<void> {
    if (typeof window !== 'undefined' && !window.isSecureContext) {
      throw new Error('SECURE_CONTEXT');
    }
    const host = document.getElementById(readerElementId);
    if (!host) {
      throw new Error('READER_NOT_READY');
    }

    const gen = ++this.startGen;
    await this.releaseScanner();
    if (gen !== this.startGen) return;

    this.destroyed = false;
    this.readerId = readerElementId;
    this.onScan = onScan;
    this.debounce.reset();
    await this.startWithRetry(0, gen);
  }

  async stop(): Promise<void> {
    this.startGen++;
    this.destroyed = true;
    this.running = false;
    await this.releaseScanner();
  }

  async flipCamera(): Promise<void> {
    const id = this.readerId;
    const cb = this.onScan;
    if (!id || !cb) return;

    try {
      const cameras = await Html5Qrcode.getCameras();
      if (cameras.length > 1) {
        const idx = this.cameraId ? cameras.findIndex((c) => c.id === this.cameraId) : -1;
        this.cameraId = cameras[(idx + 1) % cameras.length].id;
      } else {
        this.useEnvironment = !this.useEnvironment;
        this.cameraId = null;
      }
    } catch {
      this.useEnvironment = !this.useEnvironment;
      this.cameraId = null;
    }

    await this.start(id, cb);
  }

  isActive(): boolean {
    return this.running;
  }

  private async startWithRetry(attempt: number, gen: number): Promise<void> {
    if (this.destroyed || gen !== this.startGen || !this.readerId || !this.onScan) return;
    try {
      const camera = await this.resolveCamera();
      this.scanner = new Html5Qrcode(this.readerId, {
        verbose: false,
        useBarCodeDetectorIfSupported: true,
        experimentalFeatures: { useBarCodeDetectorIfSupported: true },
      });
      await this.scanner.start(camera, this.buildConfig(), (text) => this.handleFrame(text), () => {});
      if (this.destroyed || gen !== this.startGen) {
        await this.releaseScanner();
        return;
      }
      this.running = true;
      if (typeof camera === 'string') {
        localStorage.setItem(CAMERA_KEY, camera);
      }
    } catch (e) {
      await this.releaseScanner();
      if (attempt < MAX_RETRIES && !this.destroyed && gen === this.startGen) {
        this.cameraId = null;
        await this.delay(350 * (attempt + 1));
        return this.startWithRetry(attempt + 1, gen);
      }
      throw e;
    }
  }

  private buildConfig(): Html5QrcodeCameraScanConfig {
    // Sem videoConstraints + deviceId (OverconstrainedError no Chrome).
    // Sem qrbox: lê o frame inteiro — melhor para EAN em viewport pequeno.
    return {
      fps: 12,
      disableFlip: false,
    };
  }

  private async resolveCamera(): Promise<string | MediaTrackConstraints> {
    if (this.cameraId) return this.cameraId;

    const facing: MediaTrackConstraints = {
      facingMode: { ideal: this.useEnvironment ? 'environment' : 'user' },
    };

    const saved = localStorage.getItem(CAMERA_KEY);
    try {
      const cameras = await Html5Qrcode.getCameras();
      if (!cameras.length) return facing;
      if (saved && cameras.some((c) => c.id === saved)) {
        this.cameraId = saved;
        return saved;
      }
      const back = cameras.find((c) => /back|rear|traseir|environment/i.test(c.label));
      const chosen = this.useEnvironment ? back ?? cameras[cameras.length - 1] : cameras[0];
      this.cameraId = chosen.id;
      return chosen.id;
    } catch {
      return facing;
    }
  }

  private handleFrame(raw: string): void {
    if (this.destroyed || !this.onScan) return;
    const code = normalizeScannedCode(raw);
    if (!code) return;
    if (!this.debounce.accept(code)) return;

    const isPix = isPixQrPayload(code);
    if (!isPix && !isValidProductCode(code)) return;

    this.debounce.pauseAfterRead();
    const format = detectBarcodeFormat(code);
    this.onScan(code, format, isPix);
  }

  private async releaseScanner(): Promise<void> {
    this.running = false;
    if (!this.scanner) return;
    const s = this.scanner;
    this.scanner = null;
    try {
      if (s.isScanning) {
        await s.stop();
      }
    } catch {
      /* ignore */
    }
    try {
      s.clear();
    } catch {
      /* ignore */
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }
}
