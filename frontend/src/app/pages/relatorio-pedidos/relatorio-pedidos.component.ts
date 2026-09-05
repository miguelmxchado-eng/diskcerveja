import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DecimalPipe, DatePipe } from '@angular/common';
import { Subject, Subscription, concatMap, from, of, reduce } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import {
  PedidoItemResponse,
  PedidoPeriodoResponse,
  PedidoResumoDto,
} from '../../core/models';

type PeriodChip = 'hoje' | '7dias' | '30dias' | 'mes' | 'personalizado';

interface PaymentRow {
  nome: string;
  valor: number;
  pct: number;
}

interface ChartBar {
  label: string;
  total: number;
  heightPct: number;
  peak: boolean;
}

interface TopProduto {
  nome: string;
  unidades: number;
  valor: number;
}

interface KpiDelta {
  label: string;
  up: boolean;
  available: boolean;
}

const PAGE_SIZE = 10;

function hojeIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function diasAtrasIso(dias: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - dias);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatarDataBr(iso: string): string {
  if (!iso) return '';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

@Component({
  selector: 'app-relatorio-pedidos',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatSnackBarModule, DecimalPipe, DatePipe, StatusLabelPipe],
  templateUrl: './relatorio-pedidos.component.html',
  styleUrl: './relatorio-pedidos.component.scss',
})
export class RelatorioPedidosComponent implements OnInit, OnDestroy {
  readonly periodoChip = signal<PeriodChip>('7dias');
  readonly dados = signal<PedidoPeriodoResponse | null>(null);
  readonly carregando = signal(true);
  readonly carregandoPagina = signal(false);
  readonly filtrosAbertos = signal(false);
  readonly menuPedidoId = signal<number | null>(null);

  readonly busca = signal('');
  readonly filtroStatus = signal('');
  readonly filtroTipo = signal('');
  readonly filtroPagamento = signal('');
  readonly paginaAtual = signal(1);
  readonly pedidoAbertoId = signal<number | null>(null);

  dataInicio = diasAtrasIso(6);
  dataFim = hojeIso();
  readonly hojeMax = hojeIso();

  private readonly busca$ = new Subject<string>();
  private buscaSub?: Subscription;
  private loadSub?: Subscription;

  readonly periodoExibicao = computed(() => {
    const d = this.dados();
    if (!d) return '';
    const ini = d.dataInicio?.includes('-') ? formatarDataBr(d.dataInicio) : d.dataInicio;
    const fim = d.dataFim?.includes('-') ? formatarDataBr(d.dataFim) : d.dataFim;
    const dias = d.quantidadeDiasNoPeriodo || 1;
    return `${ini} - ${fim} (${dias} ${dias === 1 ? 'dia' : 'dias'})`;
  });

  readonly kpis = computed(() => {
    const d = this.dados();
    return {
      pedidos: d?.quantidadePedidosPeriodo ?? d?.totalPedidos ?? d?.pedidos?.length ?? 0,
      faturamento: d?.somaVendasEntregues ?? d?.somaTotalPedidos ?? 0,
      lucro: d?.somaLucroEntregues ?? 0,
      margem: d?.margemPercentual ?? 0,
    };
  });

  readonly deltaPedidos = computed(() =>
    this.deltaPct(this.kpis().pedidos, this.dados()?.pedidosPeriodoAnterior),
  );
  readonly deltaFaturamento = computed(() =>
    this.deltaPct(this.kpis().faturamento, this.dados()?.vendasPeriodoAnterior),
  );
  readonly deltaLucro = computed(() =>
    this.deltaPct(this.kpis().lucro, this.dados()?.lucroPeriodoAnterior),
  );
  readonly deltaMargem = computed(() => {
    const atual = this.kpis().margem;
    const ant = this.dados()?.margemPeriodoAnterior;
    if (ant == null) return { label: '—', up: true, available: false } satisfies KpiDelta;
    const diff = atual - ant;
    return {
      label: `${diff >= 0 ? '↑' : '↓'} ${Math.abs(diff).toFixed(1)} p.p.`,
      up: diff >= 0,
      available: true,
    };
  });

  readonly pedidosPaginados = computed(() => this.dados()?.pedidos ?? []);

  readonly totalPaginas = computed(() => Math.max(1, this.dados()?.totalPaginas ?? 1));

  readonly totalFiltrado = computed(() => this.dados()?.totalPedidos ?? 0);

  readonly paginationLabel = computed(() => {
    const total = this.totalFiltrado();
    if (total === 0) return 'Mostrando 0 de 0 pedidos';
    const page = this.dados()?.pagina ?? this.paginaAtual();
    const size = this.dados()?.tamanhoPagina ?? PAGE_SIZE;
    const start = (page - 1) * size + 1;
    const end = Math.min(page * size, total);
    return `Mostrando ${start} a ${end} de ${total} pedidos`;
  });

  readonly paginasVisiveis = computed(() => {
    const total = this.totalPaginas();
    const atual = this.dados()?.pagina ?? this.paginaAtual();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    const pages = new Set<number>([1, total, atual]);
    for (let i = atual - 1; i <= atual + 1; i++) {
      if (i > 1 && i < total) pages.add(i);
    }
    if (atual <= 3) {
      pages.add(2);
      pages.add(3);
      pages.add(4);
    }
    if (atual >= total - 2) {
      pages.add(total - 1);
      pages.add(total - 2);
      pages.add(total - 3);
    }
    return [...pages].sort((a, b) => a - b);
  });

  readonly chartBars = computed((): ChartBar[] => {
    const series = this.dados()?.faturamentoDiario ?? [];
    if (!series.length) return [];
    const values = series.map((s) => Number(s.total) || 0);
    const max = Math.max(...values, 1);
    return series.map((s, i) => {
      const total = values[i];
      return {
        label: s.rotulo,
        total,
        heightPct: total > 0 ? Math.max(8, Math.round((total / max) * 100)) : 3,
        peak: total > 0 && total >= max * 0.995,
      };
    });
  });

  readonly paymentRows = computed((): PaymentRow[] => {
    const rows = this.dados()?.formasPagamento ?? [];
    return rows.map((r) => ({
      nome: r.forma,
      valor: Number(r.valor) || 0,
      pct: r.percentual ?? 0,
    }));
  });

  readonly topProdutos = computed((): TopProduto[] => {
    return (this.dados()?.topProdutos ?? []).map((p) => ({
      nome: p.nome,
      unidades: p.unidades,
      valor: Number(p.valor) || 0,
    }));
  });

  readonly variacaoFaturamentoResumo = computed(() => this.deltaFaturamento());

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.buscaSub = this.busca$
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe((q) => {
        this.busca.set(q);
        this.paginaAtual.set(1);
        this.carregar();
      });
    this.carregar();
  }

  ngOnDestroy(): void {
    this.buscaSub?.unsubscribe();
    this.loadSub?.unsubscribe();
  }

  selecionarPeriodo(chip: Exclude<PeriodChip, 'personalizado'>): void {
    this.periodoChip.set(chip);
    this.paginaAtual.set(1);
    if (chip === 'hoje') {
      this.dataInicio = hojeIso();
      this.dataFim = hojeIso();
    } else if (chip === '7dias') {
      this.dataInicio = diasAtrasIso(6);
      this.dataFim = hojeIso();
    } else if (chip === '30dias') {
      this.dataInicio = diasAtrasIso(29);
      this.dataFim = hojeIso();
    } else if (chip === 'mes') {
      const now = new Date();
      const y = now.getFullYear();
      const m = String(now.getMonth() + 1).padStart(2, '0');
      this.dataInicio = `${y}-${m}-01`;
      this.dataFim = hojeIso();
    }
    this.carregar();
  }

  aplicarCalendario(): void {
    if (!this.dataInicio || !this.dataFim) {
      this.snack.open('Escolha a data inicial e a data final.', 'OK', { duration: 2500 });
      return;
    }
    if (this.dataFim < this.dataInicio) {
      this.snack.open('A data final não pode ser antes da inicial.', 'OK', { duration: 2500 });
      return;
    }
    this.periodoChip.set('personalizado');
    this.paginaAtual.set(1);
    this.carregar();
  }

  carregar(opts?: { quiet?: boolean }): void {
    const quiet = !!opts?.quiet && !!this.dados();
    if (quiet) {
      this.carregandoPagina.set(true);
    } else {
      this.carregando.set(true);
    }

    let params = new HttpParams()
      .set('inicio', this.dataInicio)
      .set('fim', this.dataFim)
      .set('pagina', String(this.paginaAtual()))
      .set('tamanho', String(PAGE_SIZE));

    const q = this.busca().trim();
    if (q) params = params.set('q', q);
    if (this.filtroStatus()) params = params.set('status', this.filtroStatus());
    if (this.filtroTipo()) params = params.set('tipo', this.filtroTipo());
    if (this.filtroPagamento()) params = params.set('pagamento', this.filtroPagamento());

    this.loadSub?.unsubscribe();
    this.loadSub = this.http
      .get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, { params })
      .subscribe({
        next: (d) => {
          this.dados.set(d);
          if (d.dataInicio && d.dataFim && !String(d.dataInicio).includes('/')) {
            this.dataInicio = d.dataInicio;
            this.dataFim = d.dataFim;
          }
          this.paginaAtual.set(d.pagina || this.paginaAtual());
          this.carregando.set(false);
          this.carregandoPagina.set(false);
        },
        error: (e) => {
          this.carregando.set(false);
          this.carregandoPagina.set(false);
          this.snack.open(e?.error?.erro ?? e?.error?.message ?? 'Erro ao carregar pedidos.', 'OK', {
            duration: 3000,
          });
        },
      });
  }

  sincronizarCaixa(): void {
    const d = this.dados();
    if (!d) return;
    this.http
      .post<{ pedidosSincronizados: number }>(`${environment.apiUrl}/api/caixa/sincronizar-vendas`, {
        inicio: d.dataInicio,
        fim: d.dataFim,
      })
      .subscribe({
        next: (r) => {
          this.snack.open(`${r.pedidosSincronizados} pedido(s) alinhados ao caixa.`, 'OK', {
            duration: 3500,
          });
          this.carregar();
        },
        error: (e) =>
          this.snack.open(e?.error?.erro ?? 'Erro ao sincronizar', 'OK', { duration: 4000 }),
      });
  }

  exportar(): void {
    let params = new HttpParams()
      .set('inicio', this.dataInicio)
      .set('fim', this.dataFim)
      .set('pagina', '1')
      .set('tamanho', '100');
    const q = this.busca().trim();
    if (q) params = params.set('q', q);
    if (this.filtroStatus()) params = params.set('status', this.filtroStatus());
    if (this.filtroTipo()) params = params.set('tipo', this.filtroTipo());
    if (this.filtroPagamento()) params = params.set('pagamento', this.filtroPagamento());

    this.snack.open('Preparando exportação…', undefined, { duration: 1200 });
    this.http
      .get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, { params })
      .pipe(
        concatMap((first) => {
          const totalPages = Math.max(1, first.totalPaginas || 1);
          const pages = Array.from({ length: totalPages }, (_, i) => i + 1);
          return from(pages).pipe(
            concatMap((p) => {
              if (p === 1) return of(first);
              return this.http.get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, {
                params: params.set('pagina', String(p)),
              });
            }),
            reduce((acc: PedidoResumoDto[], page) => {
              acc.push(...(page.pedidos ?? []));
              return acc;
            }, []),
          );
        }),
        catchError(() => {
          this.snack.open('Falha ao exportar.', 'OK', { duration: 2500 });
          return of([] as PedidoResumoDto[]);
        }),
      )
      .subscribe((rows) => {
        if (rows.length) this.baixarCsv(rows);
      });
  }

  private baixarCsv(rows: PedidoResumoDto[]): void {
    if (!rows.length) {
      this.snack.open('Nenhum pedido para exportar.', 'OK', { duration: 2500 });
      return;
    }
    const header = [
      'Pedido',
      'Data',
      'Cliente',
      'Tipo',
      'Status',
      'Pagamento',
      'Itens',
      'Desconto',
      'Total',
      'Lucro',
      'Caixa',
    ];
    const lines = rows.map((p) =>
      [
        p.id,
        new Date(p.dataHora).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }),
        p.clienteNome ?? '',
        p.tipo,
        p.status,
        p.formaPagamento,
        this.itensTexto(p),
        (p.desconto ?? 0).toFixed(2),
        p.total.toFixed(2),
        p.lucro != null ? p.lucro.toFixed(2) : '',
        p.registradoNoCaixa ? 'Sim' : 'Não',
      ]
        .map((c) => `"${String(c).replace(/"/g, '""')}"`)
        .join(','),
    );
    const blob = new Blob([['\ufeff' + header.join(','), ...lines].join('\n')], {
      type: 'text/csv;charset=utf-8;',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `historico-vendas-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  toggleFiltros(): void {
    this.filtrosAbertos.update((v) => !v);
  }

  onBuscaChange(value: string): void {
    this.busca$.next(value);
  }

  onFiltroChange(): void {
    this.paginaAtual.set(1);
    this.carregar();
  }

  irParaPagina(page: number): void {
    const clamped = Math.min(Math.max(1, page), this.totalPaginas());
    if (clamped === this.paginaAtual()) return;
    this.paginaAtual.set(clamped);
    this.carregar({ quiet: true });
  }

  showEllipsisBefore(pg: number, index: number): boolean {
    const pages = this.paginasVisiveis();
    if (index === 0) return false;
    return pg - pages[index - 1] > 1;
  }

  toggleMenuPedido(id: number, event: Event): void {
    event.stopPropagation();
    this.menuPedidoId.update((current) => (current === id ? null : id));
  }

  fecharMenuPedido(): void {
    this.menuPedidoId.set(null);
  }

  toggleDetalhePedido(id: number, event?: Event): void {
    event?.stopPropagation();
    this.pedidoAbertoId.update((atual) => (atual === id ? null : id));
    this.menuPedidoId.set(null);
  }

  itensDoPedido(p: PedidoResumoDto): PedidoItemResponse[] {
    return p.itens ?? [];
  }

  itensResumo(p: PedidoResumoDto): string {
    const itens = this.itensDoPedido(p);
    if (!itens.length) return 'Sem itens';
    const partes = itens.slice(0, 2).map((i) => `${i.quantidade}× ${i.produtoNome}`);
    if (itens.length > 2) {
      partes.push(`+${itens.length - 2}`);
    }
    return partes.join(' · ');
  }

  itensTexto(p: PedidoResumoDto): string {
    return this.itensDoPedido(p)
      .map((i) => `${i.quantidade}x ${i.produtoNome}`)
      .join('; ');
  }

  subtotalItem(i: PedidoItemResponse): number {
    return Number(i.precoUnitario) * i.quantidade;
  }

  subtotalItens(p: PedidoResumoDto): number {
    return this.itensDoPedido(p).reduce((acc, i) => acc + this.subtotalItem(i), 0);
  }

  copiarIdPedido(p: PedidoResumoDto): void {
    void navigator.clipboard.writeText(String(p.id));
    this.snack.open(`Pedido #${p.id} copiado.`, 'OK', { duration: 2000 });
    this.menuPedidoId.set(null);
  }

  statusBadge(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'ENTREGUE') return 'hist-badge--ok';
    if (s === 'CANCELADO') return 'hist-badge--danger';
    if (s === 'SAIU_ENTREGA' || s === 'EM_PREPARO') return 'hist-badge--warn';
    if (s === 'ABERTO') return 'hist-badge--gold';
    return 'hist-badge--neutral';
  }

  tipoBadge(tipo: string): string {
    const t = (tipo || '').toUpperCase();
    if (t === 'ENTREGA') return 'hist-badge--delivery';
    if (t === 'RETIRADA') return 'hist-badge--warn';
    return 'hist-badge--balcao';
  }

  private deltaPct(atual: number, anterior: number | undefined | null): KpiDelta {
    if (anterior == null) {
      return { label: '—', up: true, available: false };
    }
    if (anterior === 0) {
      if (atual > 0) return { label: '↑ novo', up: true, available: true };
      return { label: '—', up: true, available: false };
    }
    const p = ((atual - anterior) / Math.abs(anterior)) * 100;
    return {
      label: `${p >= 0 ? '↑' : '↓'} ${Math.abs(p).toFixed(0)}%`,
      up: p >= 0,
      available: true,
    };
  }
}
