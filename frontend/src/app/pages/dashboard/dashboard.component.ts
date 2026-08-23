import { Component, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe } from '@angular/common';
import { environment } from '../../../environments/environment';
import { DashboardResponse } from '../../core/models';
import { produtoFotoUrl } from '../../shared/produto-foto';

type ChartTab = 'daily' | 'weekly' | 'monthly';

interface PedidoRecenteUi {
  id: number;
  cliente: string;
  tipo: string;
  status: string;
  statusClass: string;
}

interface CriticoUi {
  nome: string;
  qtd: string;
  foto: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatIconModule, DecimalPipe, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  readonly apiBase = environment.apiUrl;
  readonly loading = signal(true);
  readonly data = signal<DashboardResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly chartTab = signal<ChartTab>('monthly');
  readonly recentes = signal<PedidoRecenteUi[]>([]);
  readonly criticos = signal<CriticoUi[]>([]);

  readonly today = new Date().toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  readonly chartSeries = computed(() => {
    const d = this.data();
    if (!d) return [];
    switch (this.chartTab()) {
      case 'daily':
        return d.graficoDiario ?? [];
      case 'weekly':
        return d.graficoSemanal ?? [];
      default:
        return d.graficoMensal ?? [];
    }
  });

  readonly chartMax = computed(() => Math.max(...this.chartSeries().map((s) => s.vendas), 1));

  readonly paymentRows = computed(() => {
    const map = this.data()?.caixa?.vendasPorFormaPagamento ?? {};
    const entries = Object.entries(map).map(([k, v]) => ({ key: k, value: Number(v) || 0 }));
    const total = entries.reduce((a, e) => a + e.value, 0) || 1;
    return entries
      .sort((a, b) => b.value - a.value)
      .map((e) => ({
        nome: this.labelPagamento(e.key),
        valor: e.value,
        pct: Math.round((e.value / total) * 100),
      }));
  });

  readonly pedidosHoje = computed(() => {
    const n = this.recentes().length;
    const d = this.data();
    if (!d) return n;
    return Math.max(n, d.pedidosEmAndamento + (d.cancelamentosHoje || 0));
  });

  readonly entreguesHoje = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return Math.max(0, this.pedidosHoje() - d.pedidosEmAndamento);
  });

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<DashboardResponse>(`${environment.apiUrl}/api/dashboard`).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.data.set(null);
        this.loading.set(false);
        this.error.set(this.messageForError(err));
      },
    });
    this.loadRecentes();
    this.loadCriticos();
  }

  setChartTab(t: ChartTab): void {
    this.chartTab.set(t);
  }

  barHeight(vendas: number): number {
    return Math.max(6, Math.round((vendas / this.chartMax()) * 100));
  }

  statusBadge(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'ENTREGUE' || s === 'CONCLUIDO') return 'em-badge--ok';
    if (s === 'EM_ROTA' || s === 'SAIU_ENTREGA') return 'em-badge--warn';
    if (s === 'CANCELADO') return 'em-badge--danger';
    if (s === 'PREPARANDO' || s === 'CONFIRMADO' || s === 'PENDENTE') return 'em-badge--gold';
    return 'em-badge--neutral';
  }

  labelStatus(status: string): string {
    const map: Record<string, string> = {
      ENTREGUE: 'Entregue',
      CONCLUIDO: 'Concluído',
      EM_ROTA: 'Em rota',
      SAIU_ENTREGA: 'Em rota',
      PREPARANDO: 'Preparando',
      CONFIRMADO: 'Preparando',
      PENDENTE: 'Preparando',
      CANCELADO: 'Cancelado',
    };
    return map[(status || '').toUpperCase()] ?? status;
  }

  private loadRecentes(): void {
    this.http.get<any[]>(`${environment.apiUrl}/api/pedidos`).subscribe({
      next: (list) => {
        const rows = (list ?? []).slice(0, 3).map((p) => ({
          id: p.id as number,
          cliente: (p.clienteNome as string) || (p.tipo === 'BALCAO' ? 'Balcão' : 'Consumidor'),
          tipo: p.tipo === 'ENTREGA' || p.tipo === 'DELIVERY' ? 'Delivery' : 'Balcão',
          status: this.labelStatus(p.status),
          statusClass: this.statusBadge(p.status),
        }));
        this.recentes.set(rows);
      },
      error: () => this.recentes.set([]),
    });
  }

  private loadCriticos(): void {
    this.http.get<any[]>(`${environment.apiUrl}/api/estoque/baixo`).subscribe({
      next: (list) => {
        this.criticos.set(
          (list ?? []).slice(0, 3).map((p) => ({
            nome: p.nome as string,
            qtd: `${p.estoqueAtual} ${p.estoqueAtual === 1 ? 'unidade' : 'unidades'}`,
            foto: produtoFotoUrl(p.nome, p.categoria),
          })),
        );
      },
      error: () => {
        this.http.get<any[]>(`${environment.apiUrl}/api/produtos`).subscribe({
          next: (prods) => {
            const baixo = (prods ?? [])
              .filter((p) => p.ativo && p.estoqueAtual <= p.estoqueMinimo)
              .slice(0, 3);
            this.criticos.set(
              baixo.map((p) => ({
                nome: p.nome as string,
                qtd: `${p.estoqueAtual} ${p.estoqueAtual === 1 ? 'unidade' : 'unidades'}`,
                foto: produtoFotoUrl(p.nome, p.categoria),
              })),
            );
          },
          error: () => this.criticos.set([]),
        });
      },
    });
  }

  private labelPagamento(k: string): string {
    const u = k.toUpperCase();
    if (u.includes('PIX')) return 'PIX';
    if (u.includes('CARTAO') || u.includes('CARTÃO') || u.includes('CREDITO') || u.includes('DEBITO')) return 'Cartão';
    if (u.includes('DINHEIRO') || u.includes('CASH')) return 'Dinheiro';
    return k;
  }

  private messageForError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return `Sem resposta de ${environment.apiUrl}. Verifique se o backend está no ar.`;
    }
    if (err.status === 401) return 'Sessão expirada. Faça login novamente.';
    if (err.status === 403) return 'Acesso negado a este recurso.';
    if (err.status >= 500) return 'Erro no servidor ao montar o painel.';
    const body = err.error as { detail?: string; message?: string } | null;
    return body?.detail ?? body?.message ?? `Falha ao carregar (HTTP ${err.status}).`;
  }
}
