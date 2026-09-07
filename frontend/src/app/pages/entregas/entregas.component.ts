import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { EntregaResumo } from '../../core/models';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-entregas',
  standalone: true,
  imports: [MatSnackBarModule, MatIconModule, DecimalPipe, StatusLabelPipe],
  templateUrl: './entregas.component.html',
  styleUrl: './entregas.component.scss',
})
export class EntregasComponent implements OnInit, OnDestroy {
  readonly rows = signal<EntregaResumo[]>([]);
  readonly loading = signal(false);
  readonly atualizando = signal(false);
  readonly acaoId = signal<number | null>(null);

  readonly isEntregador = computed(() => this.auth.user()?.perfil === 'ENTREGADOR');
  readonly canAct = computed(() => {
    const p = this.auth.user()?.perfil;
    return p === 'ENTREGADOR' || p === 'ADMIN' || p === 'OPERADOR';
  });

  readonly emRota = computed(() => this.rows().filter((r) => r.statusPedido === 'SAIU_ENTREGA'));
  readonly naFila = computed(() =>
    this.rows().filter((r) => r.statusPedido === 'ABERTO' || r.statusPedido === 'EM_PREPARO'),
  );

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.reload(true);
    this.timer = setInterval(() => this.reload(false), 20000);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  reload(showFull = true): void {
    if (showFull) this.loading.set(true);
    else this.atualizando.set(true);
    this.http.get<EntregaResumo[]>(`${environment.apiUrl}/api/entregas`).subscribe({
      next: (r) => {
        this.rows.set(r);
        this.loading.set(false);
        this.atualizando.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.atualizando.set(false);
        this.snack.open('Não foi possível carregar as entregas.', 'OK', { duration: 3500 });
      },
    });
  }

  status(pedidoId: number, status: 'SAIU_ENTREGA' | 'ENTREGUE'): void {
    if (this.acaoId() != null) return;
    this.acaoId.set(pedidoId);
    const okMsg = status === 'SAIU_ENTREGA' ? 'Saiu para entrega.' : 'Entrega concluída.';
    this.http.patch(`${environment.apiUrl}/api/pedidos/${pedidoId}/status`, { status }).subscribe({
      next: () => {
        this.snack.open(okMsg, 'OK', { duration: 2200 });
        this.acaoId.set(null);
        this.reload(false);
      },
      error: (e) => {
        this.acaoId.set(null);
        this.snack.open(e?.error?.erro ?? 'Não foi possível atualizar.', 'OK', { duration: 4000 });
      },
    });
  }

  telHref(telefone?: string | null): string | null {
    const d = (telefone || '').replace(/\D/g, '');
    return d ? `tel:${d}` : null;
  }

  waHref(telefone?: string | null): string | null {
    const raw = (telefone || '').replace(/\D/g, '');
    if (!raw) return null;
    const num = raw.startsWith('55') ? raw : `55${raw}`;
    return `https://wa.me/${num}`;
  }

  mapsHref(endereco?: string | null): string | null {
    const e = (endereco || '').trim();
    if (!e) return null;
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(e)}`;
  }

  pagamentoLabel(r: EntregaResumo): string {
    if (r.pagamentoConfirmado) return 'Já pago';
    const f = (r.formaPagamento || '').toUpperCase();
    if (f === 'PIX') return 'Cobrar Pix';
    if (f === 'CARTAO') return 'Cobrar cartão';
    if (f === 'DINHEIRO') return 'Cobrar dinheiro';
    if (f === 'MISTO') return 'Cobrar (misto)';
    return 'A cobrar';
  }

  pagamentoTone(r: EntregaResumo): 'ok' | 'warn' {
    return r.pagamentoConfirmado ? 'ok' : 'warn';
  }
}
