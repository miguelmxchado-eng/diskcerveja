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
const MAX_RETRIES = 3;

export type ScanCallback = (code: string, format: string, isPix: boolean) => void;

@Injectable({ providedIn: 'root' })
export class BarcodeScannerEngineService {
  private scanner: Html5Qrcode | null = null;
  private readerId: string | null = null;
  private running = false;
  private starting = false;
  private destroyed = false;
  private cameraId: string | null = null;
  private useEnvironment = true;
  private readonly debounce = new ScanDebounce(1100, 500);
  private onScan: ScanCallback | null = null;

  async start(readerElementId: string, onScan: ScanCallback): Promise<void> {
    await this.stop();
    this.destroyed = false;
    this.readerId = readerElementId;
    this.onScan = onScan;
    this.debounce.reset();
    await this.startWithRetry(0);
  }

  async stop(): Promise<void> {
    this.destroyed = true;
    this.running = false;
    this.starting = false;
    if (!this.scanner) return;
    const s = this.scanner;
    this.scanner = null;
    try {
      if (s.isScanning) {
        await s.stop();
      }
      await s.clear();
    } catch {
      /* ignore cleanup errors */
    }
  }

  async flipCamera(): Promise<void> {
    this.useEnvironment = !this.useEnvironment;
    this.cameraId = null;
    const id = this.readerId;
    const cb = this.onScan;
    if (!id || !cb) return;
    await this.stop();
    this.destroyed = false;
    this.readerId = id;
    this.onScan = cb;
    await this.startWithRetry(0);
  }

  isActive(): boolean {
    return this.running;
  }

  private async startWithRetry(attempt: number): Promise<void> {
    if (this.destroyed || !this.readerId || !this.onScan) return;
    this.starting = true;
    try {
      const cameraId = await this.resolveCameraId();
      const config = this.buildConfig();
      this.scanner = new Html5Qrcode(this.readerId, { verbose: false });
      await this.scanner.start(cameraId, config, (text) => this.handleFrame(text), () => {});
      if (!this.destroyed) {
        this.running = true;
        if (typeof cameraId === 'string') {
          localStorage.setItem(CAMERA_KEY, cameraId);
        }
      }
    } catch (e) {
      if (attempt < MAX_RETRIES - 1 && !this.destroyed) {
        await this.delay(400 * (attempt + 1));
        await this.stop();
        this.destroyed = false;
        this.readerId = this.readerId;
        return this.startWithRetry(attempt + 1);
      }
      throw e;
    } finally {
      this.starting = false;
    }
  }

  private buildConfig(): Html5QrcodeCameraScanConfig {
    return {
      fps: 24,
      disableFlip: false,
      qrbox: (w, h) => {
        const mw = Math.min(w, h);
        const size = Math.floor(mw * 0.88);
        return { width: size, height: Math.floor(size * 0.42) };
      },
      aspectRatio: 1.777,
      videoConstraints: {
        facingMode: this.useEnvironment ? 'environment' : 'user',
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
    };
  }

  private async resolveCameraId(): Promise<string | { facingMode: string }> {
    if (this.cameraId) return this.cameraId;

    const saved = localStorage.getItem(CAMERA_KEY);
    try {
      const cameras = await Html5Qrcode.getCameras();
      if (!cameras.length) {
        return { facingMode: 'environment' };
      }
      if (saved && cameras.some((c) => c.id === saved)) {
        this.cameraId = saved;
        return saved;
      }
      const back = cameras.find((c) => /back|rear|traseir|environment/i.test(c.label));
      const chosen = this.useEnvironment ? back ?? cameras[cameras.length - 1] : cameras[0];
      this.cameraId = chosen.id;
      return chosen.id;
    } catch {
      return { facingMode: this.useEnvironment ? 'environment' : 'user' };
    }
  }

  private handleFrame(raw: string): void {
    if (this.destroyed || !this.onScan || this.starting) return;
    const code = normalizeScannedCode(raw);
    if (!code) return;
    if (!this.debounce.accept(code)) return;

    const isPix = isPixQrPayload(code);
    if (!isPix && !isValidProductCode(code)) return;

    this.debounce.pauseAfterRead();
    const format = detectBarcodeFormat(code);
    this.onScan(code, format, isPix);
  }

  private delay(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }
}
