import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class BarcodeFeedbackService {
  private audioCtx: AudioContext | null = null;

  /** Bip curto de sucesso (verde / alta). */
  success(): void {
    this.playTone(1240, 0.045, 'sine', 0.14);
    setTimeout(() => this.playTone(1560, 0.035, 'sine', 0.1), 50);
    this.vibrate(35);
  }

  /** Bip de erro (vermelho / grave). */
  error(): void {
    this.playTone(280, 0.09, 'square', 0.1);
    setTimeout(() => this.playTone(200, 0.08, 'square', 0.08), 70);
    this.vibrate([40, 40, 40]);
  }

  warn(): void {
    this.playTone(520, 0.07, 'triangle', 0.1);
    this.vibrate(20);
  }

  private vibrate(pattern: number | number[]): void {
    navigator.vibrate?.(pattern);
  }

  private playTone(
    freq: number,
    durationSec: number,
    type: OscillatorType,
    volume: number,
  ): void {
    try {
      if (!this.audioCtx) {
        this.audioCtx = new AudioContext();
      }
      const ctx = this.audioCtx;
      if (ctx.state === 'suspended') {
        void ctx.resume();
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.value = freq;
      gain.gain.value = volume;
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + durationSec);
      osc.connect(gain);
      gain.connect(ctx.destination);
      const t = ctx.currentTime;
      osc.start(t);
      osc.stop(t + durationSec + 0.02);
    } catch {
      /* áudio opcional */
    }
  }
}
