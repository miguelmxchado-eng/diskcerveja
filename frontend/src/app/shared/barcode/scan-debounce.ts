/** Evita leituras duplicadas + pausa pós-leitura (estilo caixa de mercado). */
export class ScanDebounce {
  private lastCode = '';
  private lastAt = 0;
  private pausedUntil = 0;

  constructor(
    private readonly windowMs = 1200,
    private readonly pauseMs = 500,
  ) {}

  accept(code: string): boolean {
    const now = Date.now();
    if (now < this.pausedUntil) return false;

    const normalized = code.trim();
    if (!normalized) return false;
    if (normalized === this.lastCode && now - this.lastAt < this.windowMs) {
      return false;
    }
    this.lastCode = normalized;
    this.lastAt = now;
    return true;
  }

  /** Pausa leituras após sucesso (500ms) antes de continuar. */
  pauseAfterRead(): void {
    this.pausedUntil = Date.now() + this.pauseMs;
  }

  reset(): void {
    this.lastCode = '';
    this.lastAt = 0;
    this.pausedUntil = 0;
  }
}
