import { Component, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DecimalPipe, DatePipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import {
  PedidoItemResponse,
  PedidoPeriodoResponse,
  PedidoResumoDto,
  PeriodoPedido,
} from '../../core/models';

type PeriodChip = 'hoje' | '7dias' | '30dias' | 'mes';

interface PaymentRow {
  nome: string;
  valor: number;
  pct: number;
}

interface ChartBar {
  label: string;
  total: number;
  heightPct: number;
}

interface TopProduto {
  nome: string;
  unidades: number;
  valor: number;
}

const CHIP_TO_PERIODO: Record<PeriodChip, PeriodoPedido> = {
  hoje: 'DIA',
  '7dias': 'SEMANA',
  '30dias': 'ANO',
  mes: 'MES',
};

const PAGE_SIZE = 20;

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
  readonly carregando = signal(true);
  readonly filtrosAbertos = signal(false);
  readonly menuPedidoId = signal<number | null>(null);

  readonly busca = signal('');
  readonly filtroStatus = signal('');
  readonly filtroTipo = signal('');
  readonly filtroPagamento = signal('');
  readonly paginaAtual = signal(1);
  /** Pedido com detalhe de itens aberto. */
  readonly pedidoAbertoId = signal<number | null>(null);

  readonly pedidosPeriodo = computed(() => {
    const d = this.dados();
    if (!d) return [];
    if (this.periodoChip() !== '30dias') return d.pedidos;
    const cutoff = new Date();
    cutoff.setHours(0, 0, 0, 0);
    cutoff.setDate(cutoff.getDate() - 29);
    return d.pedidos.filter((p) => new Date(p.dataHora) >= cutoff);
  });

  readonly periodoExibicao = computed(() => {
    const d = this.dados();
    if (!d) return '';
    if (this.periodoChip() === '30dias') {
      const fim = new Date();
      const inicio = new Date();
      inicio.setDate(fim.getDate() - 29);
      return `${inicio.toLocaleDateString('pt-BR')} — ${fim.toLocaleDateString('pt-BR')}`;
    }
    return `${d.dataInicio} — ${d.dataFim}`;
  });

  readonly kpis = computed(() => {
    const pedidos = this.pedidosPeriodo();
    const entregues = pedidos.filter((p) => p.status === 'ENTREGUE');
    const faturamentoApi = this.dados()?.somaVendasEntregues ?? this.dados()?.somaTotalPedidos ?? 0;
    const lucroApi = this.dados()?.somaLucroEntregues ?? 0;
    const margemApi = this.dados()?.margemPercentual ?? 0;

    if (this.periodoChip() === '30dias') {
      const faturamento = entregues.reduce((acc, p) => acc + p.total, 0);
      const lucro = entregues.reduce((acc, p) => acc + (p.lucro ?? 0), 0);
      const margem = faturamento > 0 ? (lucro / faturamento) * 100 : 0;
      return {
        pedidos: pedidos.length,
        faturamento,
        lucro,
        margem,
      };
    }

    return {
      pedidos: pedidos.length,
      faturamento: faturamentoApi || this.dados()?.somaTotalPedidos || 0,
      lucro: lucroApi,
      margem: margemApi,
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
    if (total === 0) return '0 de 0';
    const start = (this.paginaAtual() - 1) * PAGE_SIZE + 1;
    const end = Math.min(this.paginaAtual() * PAGE_SIZE, total);
    return `${start}–${end} de ${total}`;
  });

  readonly paginasVisiveis = computed(() => {
    const total = this.totalPaginas();
    const atual = this.paginaAtual();
    const pages: number[] = [];
    const from = Math.max(1, atual - 2);
    const to = Math.min(total, from + 4);
    for (let i = from; i <= to; i++) pages.push(i);
    return pages;
  });

  readonly chartBars = computed((): ChartBar[] => {
    const pedidos = this.pedidosPeriodo().filter((p) => p.status === 'ENTREGUE');
    const byDay = new Map<string, number>();
    for (const p of pedidos) {
      const d = new Date(p.dataHora);
      const key = d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
      byDay.set(key, (byDay.get(key) ?? 0) + p.total);
    }
    const entries = [...byDay.entries()].slice(-7);
    const max = Math.max(...entries.map(([, v]) => v), 1);
    return entries.map(([label, total]) => ({
      label,
      total,
      heightPct: Math.max(4, Math.round((total / max) * 100)),
    }));
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

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  selecionarPeriodo(chip: PeriodChip): void {
    this.periodoChip.set(chip);
    this.paginaAtual.set(1);
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    const chip = this.periodoChip();
    const periodo = CHIP_TO_PERIODO[chip];
    const params = new HttpParams().set('periodo', periodo);
    this.http
      .get<PedidoPeriodoResponse>(`${environment.apiUrl}/api/pedidos/periodo`, { params })
      .subscribe({
        next: (d) => {
          this.dados.set(d);
          this.carregando.set(false);
        },
        error: () => {
          this.carregando.set(false);
          this.snack.open('Erro ao carregar pedidos.', 'OK', { duration: 3000 });
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
          this.snack.open(`${r.pedidosSincronizados} pedido(s) alinhados ao caixa.`, 'OK', { duration: 3500 });
          this.carregar();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro ao sincronizar', 'OK', { duration: 4000 }),
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
        new Date(p.dataHora).toLocaleString('pt-BR'),
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
    if (s === 'ENTREGUE') return 'em-badge--ok';
    if (s === 'CANCELADO') return 'em-badge--danger';
    if (s === 'SAIU_ENTREGA' || s === 'EM_PREPARO') return 'em-badge--warn';
    if (s === 'ABERTO') return 'em-badge--gold';
    return 'em-badge--neutral';
  }

  tipoBadge(tipo: string): string {
    const t = (tipo || '').toUpperCase();
    if (t === 'ENTREGA') return 'em-badge--gold';
    if (t === 'RETIRADA') return 'em-badge--warn';
    return 'em-badge--neutral';
  }

  private labelPagamento(k: string): string {
    const u = k.toUpperCase();
    if (u.includes('PIX')) return 'PIX';
    if (u.includes('CARTAO') || u.includes('CARTÃO')) return 'Cartão';
    if (u.includes('DINHEIRO')) return 'Dinheiro';
    return k;
  }
}
