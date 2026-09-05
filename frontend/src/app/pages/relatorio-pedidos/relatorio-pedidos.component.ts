import { Component, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DecimalPipe, DatePipe } from '@angular/common';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import {
  PedidoItemResponse,
  PedidoPeriodoResponse,
  PedidoResumoDto,
  PeriodoPedido,
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

interface PrevKpis {
  pedidos: number;
  faturamento: number;
  lucro: number;
  margem: number;
}

const CHIP_TO_PERIODO: Record<Exclude<PeriodChip, 'personalizado' | '30dias'>, PeriodoPedido> = {
  hoje: 'DIA',
  '7dias': 'SEMANA',
  mes: 'MES',
};

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

function parseIsoLocal(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function toIsoLocal(d: Date): string {
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

function diasEntreInclusive(inicio: string, fim: string): number {
  const a = parseIsoLocal(inicio);
  const b = parseIsoLocal(fim);
  return Math.max(1, Math.round((b.getTime() - a.getTime()) / 86400000) + 1);
}

function periodoAnterior(inicio: string, fim: string): { inicio: string; fim: string } {
  const dias = diasEntreInclusive(inicio, fim);
  const fimAnt = parseIsoLocal(inicio);
  fimAnt.setDate(fimAnt.getDate() - 1);
  const iniAnt = new Date(fimAnt);
  iniAnt.setDate(iniAnt.getDate() - (dias - 1));
  return { inicio: toIsoLocal(iniAnt), fim: toIsoLocal(fimAnt) };
}

@Component({
  selector: 'app-relatorio-pedidos',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatSnackBarModule, DecimalPipe, DatePipe, StatusLabelPipe],
  templateUrl: './relatorio-pedidos.component.html',
  styleUrl: './relatorio-pedidos.component.scss',
})
export class RelatorioPedidosComponent implements OnInit {
  readonly periodoChip = signal<PeriodChip>('7dias');
  readonly dados = signal<PedidoPeriodoResponse | null>(null);
  readonly prevKpis = signal<PrevKpis | null>(null);
  readonly carregando = signal(true);
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

  readonly pedidosPeriodo = computed(() => this.dados()?.pedidos ?? []);

  readonly periodoExibicao = computed(() => {
    const d = this.dados();
    if (!d) return '';
    const ini = d.dataInicio?.includes('-') ? formatarDataBr(d.dataInicio) : d.dataInicio;
    const fim = d.dataFim?.includes('-') ? formatarDataBr(d.dataFim) : d.dataFim;
    const dias = d.quantidadeDiasNoPeriodo || diasEntreInclusive(this.dataInicio, this.dataFim);
    return `${ini} - ${fim} (${dias} ${dias === 1 ? 'dia' : 'dias'})`;
  });

  readonly kpis = computed(() => {
    const pedidos = this.pedidosPeriodo();
    const faturamentoApi = this.dados()?.somaVendasEntregues ?? this.dados()?.somaTotalPedidos ?? 0;
    const lucroApi = this.dados()?.somaLucroEntregues ?? 0;
    const margemApi = this.dados()?.margemPercentual ?? 0;
    return {
      pedidos: pedidos.length,
      faturamento: faturamentoApi || this.dados()?.somaTotalPedidos || 0,
      lucro: lucroApi,
      margem: margemApi,
    };
  });

  readonly deltaPedidos = computed(() => this.deltaPct(this.kpis().pedidos, this.prevKpis()?.pedidos));
  readonly deltaFaturamento = computed(() =>
    this.deltaPct(this.kpis().faturamento, this.prevKpis()?.faturamento),
  );
  readonly deltaLucro = computed(() => this.deltaPct(this.kpis().lucro, this.prevKpis()?.lucro));
  readonly deltaMargem = computed(() => {
    const atual = this.kpis().margem;
    const ant = this.prevKpis()?.margem;
    if (ant == null) return { label: '—', up: true, available: false } satisfies KpiDelta;
    const diff = atual - ant;
    return {
      label: `${diff >= 0 ? '↑' : '↓'} ${Math.abs(diff).toFixed(1)} p.p.`,
      up: diff >= 0,
      available: true,
    };
  });

  readonly pedidosFiltrados = computed(() => {
    let list = this.pedidosPeriodo();
    const q = this.busca().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (p) =>
          String(p.id).includes(q) ||
          (p.clienteNome?.toLowerCase().includes(q) ?? false) ||
          (p.telefone?.includes(q) ?? false) ||
          (p.itens ?? []).some((i) => (i.produtoNome ?? '').toLowerCase().includes(q)),
      );
    }
    const status = this.filtroStatus();
    if (status) list = list.filter((p) => p.status === status);
    const tipo = this.filtroTipo();
    if (tipo) list = list.filter((p) => p.tipo === tipo);
    const pag = this.filtroPagamento();
    if (pag) list = list.filter((p) => p.formaPagamento === pag);
    return list;
  });

  readonly topProdutos = computed((): TopProduto[] => {
    const map = new Map<string, TopProduto>();
    for (const p of this.pedidosPeriodo()) {
      if (p.status !== 'ENTREGUE') continue;
      for (const item of p.itens ?? []) {
        const nome = (item.produtoNome || 'Item').trim();
        const key = nome.toLowerCase();
        const atual = map.get(key) ?? { nome, unidades: 0, valor: 0 };
        atual.unidades += item.quantidade;
        atual.valor += Number(item.precoUnitario) * item.quantidade;
        map.set(key, atual);
      }
    }
    return [...map.values()].sort((a, b) => b.valor - a.valor).slice(0, 8);
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.pedidosFiltrados().length / PAGE_SIZE)),
  );

  readonly pedidosPaginados = computed(() => {
    const start = (this.paginaAtual() - 1) * PAGE_SIZE;
    return this.pedidosFiltrados().slice(start, start + PAGE_SIZE);
  });

  readonly paginationLabel = computed(() => {
    const total = this.pedidosFiltrados().length;
    if (total === 0) return 'Mostrando 0 de 0 pedidos';
    const start = (this.paginaAtual() - 1) * PAGE_SIZE + 1;
    const end = Math.min(this.paginaAtual() * PAGE_SIZE, total);
    return `Mostrando ${start} a ${end} de ${total} pedidos`;
  });

  readonly paginasVisiveis = computed(() => {
    const total = this.totalPaginas();
    const atual = this.paginaAtual();
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
    const d = this.dados();
    const pedidos = this.pedidosPeriodo().filter((p) => p.status === 'ENTREGUE');
    const byDay = new Map<string, number>();
    for (const p of pedidos) {
      const dt = new Date(p.dataHora);
      const key = dt.toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        timeZone: 'America/Sao_Paulo',
      });
      byDay.set(key, (byDay.get(key) ?? 0) + p.total);
    }

    const labels: string[] = [];
    if (d?.dataInicio && d?.dataFim) {
      const cur = parseIsoLocal(d.dataInicio.includes('/') ? this.dataInicio : d.dataInicio);
      const end = parseIsoLocal(d.dataFim.includes('/') ? this.dataFim : d.dataFim);
      // API may return BR formatted dates — fall back to local inputs
      let start = cur;
      let finish = end;
      if (Number.isNaN(start.getTime()) || Number.isNaN(finish.getTime())) {
        start = parseIsoLocal(this.dataInicio);
        finish = parseIsoLocal(this.dataFim);
      }
      const guard = 62;
      let n = 0;
      for (let x = new Date(start); x <= finish && n < guard; x.setDate(x.getDate() + 1), n++) {
        labels.push(
          x.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }),
        );
      }
    }
    if (!labels.length) {
      labels.push(...[...byDay.keys()].slice(-7));
    }

    const values = labels.map((label) => byDay.get(label) ?? 0);
    const max = Math.max(...values, 1);
    return labels.map((label, i) => {
      const total = values[i];
      return {
        label,
        total,
        heightPct: total > 0 ? Math.max(8, Math.round((total / max) * 100)) : 3,
        peak: total > 0 && total >= max * 0.995,
      };
    });
  });

  readonly paymentRows = computed((): PaymentRow[] => {
    const pedidos = this.pedidosPeriodo().filter((p) => p.status === 'ENTREGUE');
    const map = new Map<string, number>();
    for (const p of pedidos) {
      map.set(p.formaPagamento, (map.get(p.formaPagamento) ?? 0) + p.total);
    }
    const entries = [...map.entries()].map(([k, v]) => ({ key: k, value: v }));
    const total = entries.reduce((acc, e) => acc + e.value, 0) || 1;
    return entries
      .sort((a, b) => b.value - a.value)
      .map((e) => ({
        nome: this.labelPagamento(e.key),
        valor: e.value,
        pct: Math.round((e.value / total) * 100),
      }));
  });

  readonly variacaoFaturamentoResumo = computed(() => this.deltaFaturamento());

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
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

  carregar(): void {
    this.carregando.set(true);
    const chip = this.periodoChip();
    let params = new HttpParams();
    let inicio = this.dataInicio;
    let fim = this.dataFim;
    if (chip === 'personalizado' || chip === '30dias') {
      params = params.set('inicio', inicio).set('fim', fim);
    } else {
      params = params.set('periodo', CHIP_TO_PERIODO[chip]);
    }

    this.http
      .get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, { params })
      .subscribe({
        next: (d) => {
          this.dados.set(d);
          if (d.dataInicio && d.dataFim && !d.dataInicio.includes('/')) {
            inicio = d.dataInicio;
            fim = d.dataFim;
            this.dataInicio = inicio;
            this.dataFim = fim;
          }
          this.carregando.set(false);
          this.carregarPeriodoAnterior(inicio, fim);
        },
        error: (e) => {
          this.carregando.set(false);
          this.prevKpis.set(null);
          this.snack.open(e?.error?.erro ?? 'Erro ao carregar pedidos.', 'OK', { duration: 3000 });
        },
      });
  }

  private carregarPeriodoAnterior(inicio: string, fim: string): void {
    const ant = periodoAnterior(inicio, fim);
    const params = new HttpParams().set('inicio', ant.inicio).set('fim', ant.fim);
    this.http
      .get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, { params })
      .pipe(catchError(() => of(null)))
      .subscribe((prev) => {
        if (!prev) {
          this.prevKpis.set(null);
          return;
        }
        this.prevKpis.set({
          pedidos: prev.pedidos?.length ?? 0,
          faturamento: prev.somaVendasEntregues ?? prev.somaTotalPedidos ?? 0,
          lucro: prev.somaLucroEntregues ?? 0,
          margem: prev.margemPercentual ?? 0,
        });
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
    const rows = this.pedidosFiltrados();
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
    this.busca.set(value);
    this.paginaAtual.set(1);
  }

  onFiltroChange(): void {
    this.paginaAtual.set(1);
  }

  irParaPagina(page: number): void {
    const clamped = Math.min(Math.max(1, page), this.totalPaginas());
    this.paginaAtual.set(clamped);
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
    if (anterior == null || anterior === 0) {
      if (atual > 0 && anterior === 0) {
        return { label: '↑ novo', up: true, available: true };
      }
      return { label: '—', up: true, available: false };
    }
    const p = ((atual - anterior) / Math.abs(anterior)) * 100;
    return {
      label: `${p >= 0 ? '↑' : '↓'} ${Math.abs(p).toFixed(0)}%`,
      up: p >= 0,
      available: true,
    };
  }

  private labelPagamento(k: string): string {
    const u = k.toUpperCase();
    if (u.includes('PIX')) return 'PIX';
    if (u.includes('CARTAO') || u.includes('CARTÃO')) return 'Cartão';
    if (u.includes('DINHEIRO')) return 'Dinheiro';
    return k;
  }
}
