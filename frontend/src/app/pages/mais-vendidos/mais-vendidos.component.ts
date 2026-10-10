import { Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MaisVendidoItem,
  MaisVendidosDia,
  MaisVendidosResponse,
  MaisVendidosSerieResponse,
} from '../../core/models';

type PeriodChip = 'hoje' | '7dias' | '30dias' | 'mes' | 'mesPassado' | 'personalizado';
type Metrica = 'unidades' | 'valor' | 'lucro';

interface RankBar {
  item: MaisVendidoItem;
  posicao: number;
  total: number;
  widthPct: number;
}

interface DiaBar {
  label: string;
  titulo: string;
  total: number;
  heightPct: number;
  peak: boolean;
  showTick: boolean;
}

interface CategoriaFatia {
  categoria: string;
  label: string;
  total: number;
  pct: number;
  cor: string;
}

interface Variacao {
  label: string;
  up: boolean;
  novo: boolean;
}

const CATEGORIA_LABEL: Record<string, string> = {
  CERVEJAS: 'Cervejas',
  DESTILADOS: 'Destilados',
  REFRIGERANTES: 'Refrigerantes',
  ENERGETICOS: 'Energéticos',
  PETISCOS: 'Petiscos',
  COMBOS: 'Combos',
  CIGARROS: 'Cigarros',
  OUTROS: 'Outros',
};

const CORES = ['#d2a410', '#171717', '#e8c547', '#8a6d00', '#667085', '#f0d56a', '#b7791f', '#c8ccd4'];

const TABELA_INICIAL = 20;

function isoLocal(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function diasAtrasIso(dias: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - dias);
  return isoLocal(d);
}

function dataBr(iso: string): string {
  if (!iso) return '';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

function variacao(atual: number, anterior: number): Variacao | null {
  if (anterior <= 0) {
    return atual > 0 ? { label: 'novo', up: true, novo: true } : null;
  }
  const pct = ((atual - anterior) / anterior) * 100;
  const arred = Math.round(pct);
  return { label: `${arred > 0 ? '+' : ''}${arred}%`, up: pct >= 0, novo: false };
}

@Component({
  selector: 'app-mais-vendidos',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatSnackBarModule, DecimalPipe],
  templateUrl: './mais-vendidos.component.html',
  styleUrl: './mais-vendidos.component.scss',
})
export class MaisVendidosComponent implements OnInit, OnDestroy {
  readonly periodoChip = signal<PeriodChip>('30dias');
  readonly dados = signal<MaisVendidosResponse | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);

  readonly metrica = signal<Metrica>('unidades');
  readonly categoriaFiltro = signal<string>('');
  readonly busca = signal('');
  readonly mostrarTodos = signal(false);

  readonly selecionado = signal<MaisVendidoItem | null>(null);
  readonly serieSelecionado = signal<MaisVendidosDia[] | null>(null);
  readonly carregandoSerie = signal(false);

  readonly hojeMax = isoLocal(new Date());
  dataInicio = diasAtrasIso(29);
  dataFim = isoLocal(new Date());

  @ViewChild('evo') private evo?: ElementRef<HTMLElement>;

  private loadSub?: Subscription;
  private serieSub?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
    this.serieSub?.unsubscribe();
  }

  readonly periodoTexto = computed(() => {
    const d = this.dados();
    if (!d) return '';
    return d.inicio === d.fim ? dataBr(d.inicio) : `${dataBr(d.inicio)} a ${dataBr(d.fim)}`;
  });

  readonly periodoAnteriorTexto = computed(() => {
    const d = this.dados();
    if (!d) return '';
    return d.inicioAnterior === d.fimAnterior
      ? dataBr(d.inicioAnterior)
      : `${dataBr(d.inicioAnterior)} a ${dataBr(d.fimAnterior)}`;
  });

  readonly categoriasDisponiveis = computed(() =>
    (this.dados()?.categorias ?? []).map((c) => ({ id: c.categoria, label: this.categoriaLabel(c.categoria) })),
  );

  /** Itens após filtros de categoria e busca, ordenados pela métrica escolhida. */
  readonly itensFiltrados = computed(() => {
    const d = this.dados();
    if (!d) return [];
    const cat = this.categoriaFiltro();
    const q = this.busca().trim().toLocaleLowerCase('pt-BR');
    const m = this.metrica();
    return d.itens
      .filter((i) => !cat || i.categoria === cat)
      .filter((i) => !q || i.nome.toLocaleLowerCase('pt-BR').includes(q))
      .slice()
      .sort((a, b) => this.valorMetrica(b, m) - this.valorMetrica(a, m) || b.unidades - a.unidades);
  });

  readonly totalFiltrado = computed(() => {
    const m = this.metrica();
    return this.itensFiltrados().reduce((s, i) => s + this.valorMetrica(i, m), 0);
  });

  readonly itensTabela = computed(() => {
    const all = this.itensFiltrados();
    return this.mostrarTodos() ? all : all.slice(0, TABELA_INICIAL);
  });

  readonly temMaisItens = computed(() => this.itensFiltrados().length > TABELA_INICIAL);

  readonly rankBars = computed<RankBar[]>(() => {
    const m = this.metrica();
    const top = this.itensFiltrados().slice(0, 10);
    const max = Math.max(...top.map((i) => this.valorMetrica(i, m)), 0);
    return top.map((item, idx) => {
      const total = this.valorMetrica(item, m);
      return {
        item,
        posicao: idx + 1,
        total,
        widthPct: max > 0 ? Math.max(2, (Math.max(total, 0) / max) * 100) : 0,
      };
    });
  });

  readonly campeao = computed(() => {
    const d = this.dados();
    if (!d || !d.itens.length) return null;
    return d.itens[0];
  });

  readonly kpiVarUnidades = computed(() => {
    const d = this.dados();
    return d ? variacao(d.totalUnidades, d.totalUnidadesAnterior) : null;
  });

  readonly kpiVarValor = computed(() => {
    const d = this.dados();
    return d ? variacao(d.totalValor, d.totalValorAnterior) : null;
  });

  readonly margem = computed(() => {
    const d = this.dados();
    if (!d || d.totalValor <= 0) return 0;
    return (d.totalLucro / d.totalValor) * 100;
  });

  readonly fatias = computed<CategoriaFatia[]>(() => {
    const d = this.dados();
    if (!d) return [];
    const usarUnidades = this.metrica() === 'unidades';
    const linhas = d.categorias
      .map((c) => ({ categoria: c.categoria, total: usarUnidades ? c.unidades : c.valor }))
      .filter((c) => c.total > 0)
      .sort((a, b) => b.total - a.total);
    const soma = linhas.reduce((s, c) => s + c.total, 0);
    return linhas.map((c, i) => ({
      categoria: c.categoria,
      label: this.categoriaLabel(c.categoria),
      total: c.total,
      pct: soma > 0 ? (c.total / soma) * 100 : 0,
      cor: CORES[i % CORES.length],
    }));
  });

  readonly donutBg = computed(() => {
    const f = this.fatias();
    if (!f.length) return '#efe8dc';
    let acc = 0;
    const stops = f.map((s) => {
      const from = acc;
      acc += s.pct;
      return `${s.cor} ${from}% ${acc}%`;
    });
    return `conic-gradient(${stops.join(', ')})`;
  });

  /** Gráfico diário: produto selecionado ou todos os itens; agrupa por semana/mês em períodos longos. */
  readonly evolucaoUsaValor = computed(() => this.metrica() !== 'unidades');

  readonly diaBars = computed<DiaBar[]>(() => {
    const serie = this.selecionado() ? this.serieSelecionado() : this.dados()?.porDia;
    if (!serie?.length) return [];
    const usarValor = this.evolucaoUsaValor();
    const grupos = this.agrupar(serie);
    const max = Math.max(...grupos.map((g) => (usarValor ? g.valor : g.unidades)), 0);
    const step = Math.max(1, Math.ceil(grupos.length / 12));
    return grupos.map((g, idx) => {
      const total = usarValor ? g.valor : g.unidades;
      return {
        label: g.label,
        titulo: g.titulo,
        total,
        heightPct: max > 0 ? Math.max(total > 0 ? 3 : 0, (total / max) * 100) : 0,
        peak: max > 0 && total === max,
        showTick: idx % step === 0 || idx === grupos.length - 1,
      };
    });
  });

  readonly agrupamentoTexto = computed(() => {
    const n = this.dados()?.porDia.length ?? 0;
    if (n > 120) return 'por mês';
    if (n > 45) return 'por semana';
    return 'por dia';
  });

  readonly melhorDia = computed(() => {
    const bars = this.diaBars();
    if (!bars.length) return null;
    const peak = bars.reduce((a, b) => (b.total > a.total ? b : a));
    return peak.total > 0 ? peak : null;
  });

  private agrupar(serie: MaisVendidosDia[]): { label: string; titulo: string; unidades: number; valor: number }[] {
    const n = serie.length;
    if (n <= 45) {
      return serie.map((d) => {
        const [, m, day] = d.dia.split('-');
        return { label: `${day}/${m}`, titulo: dataBr(d.dia), unidades: d.unidades, valor: d.valor };
      });
    }
    const porMes = n > 120;
    const out: { label: string; titulo: string; unidades: number; valor: number }[] = [];
    let atual: { key: string; label: string; titulo: string; unidades: number; valor: number } | null = null;
    serie.forEach((d, idx) => {
      const [y, m, day] = d.dia.split('-');
      const key = porMes ? `${y}-${m}` : String(Math.floor(idx / 7));
      if (!atual || atual.key !== key) {
        atual = {
          key,
          label: porMes ? `${m}/${y.slice(2)}` : `${day}/${m}`,
          titulo: porMes ? `${m}/${y}` : `Semana de ${dataBr(d.dia)}`,
          unidades: 0,
          valor: 0,
        };
        out.push(atual);
      }
      atual.unidades += d.unidades;
      atual.valor += d.valor;
    });
    return out;
  }

  valorMetrica(i: MaisVendidoItem, m: Metrica = this.metrica()): number {
    if (m === 'valor') return i.valor;
    if (m === 'lucro') return i.lucro;
    return i.unidades;
  }

  variacaoItem(i: MaisVendidoItem): Variacao | null {
    if (this.metrica() === 'unidades') return variacao(i.unidades, i.unidadesAnterior);
    return variacao(i.valor, i.valorAnterior);
  }

  participacao(i: MaisVendidoItem): number {
    const total = this.totalFiltrado();
    return total > 0 ? (this.valorMetrica(i) / total) * 100 : 0;
  }

  categoriaLabel(c: string): string {
    return CATEGORIA_LABEL[c] ?? c;
  }

  selecionarPeriodo(chip: Exclude<PeriodChip, 'personalizado'>): void {
    this.periodoChip.set(chip);
    const hoje = isoLocal(new Date());
    if (chip === 'hoje') {
      this.dataInicio = hoje;
      this.dataFim = hoje;
    } else if (chip === '7dias') {
      this.dataInicio = diasAtrasIso(6);
      this.dataFim = hoje;
    } else if (chip === '30dias') {
      this.dataInicio = diasAtrasIso(29);
      this.dataFim = hoje;
    } else if (chip === 'mes') {
      const now = new Date();
      this.dataInicio = isoLocal(new Date(now.getFullYear(), now.getMonth(), 1));
      this.dataFim = hoje;
    } else if (chip === 'mesPassado') {
      const now = new Date();
      this.dataInicio = isoLocal(new Date(now.getFullYear(), now.getMonth() - 1, 1));
      this.dataFim = isoLocal(new Date(now.getFullYear(), now.getMonth(), 0));
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
    this.carregar();
  }

  setMetrica(m: Metrica): void {
    this.metrica.set(m);
  }

  setCategoria(c: string): void {
    this.categoriaFiltro.set(this.categoriaFiltro() === c ? '' : c);
    this.mostrarTodos.set(false);
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    const params = new HttpParams().set('inicio', this.dataInicio).set('fim', this.dataFim);
    this.loadSub?.unsubscribe();
    this.loadSub = this.http
      .get<MaisVendidosResponse>(`${environment.apiUrl}/api/relatorios/mais-vendidos`, { params })
      .subscribe({
        next: (d) => {
          this.dados.set(d);
          this.carregando.set(false);
          const sel = this.selecionado();
          if (sel) {
            const ainda = d.itens.find((i) => i.chave === sel.chave);
            if (ainda) {
              this.selecionar(ainda, true);
            } else {
              this.limparSelecao();
            }
          }
        },
        error: (e) => {
          this.carregando.set(false);
          this.dados.set(null);
          this.erro.set(e?.error?.erro || 'Não foi possível carregar o relatório.');
        },
      });
  }

  selecionar(item: MaisVendidoItem, forcar = false): void {
    if (!forcar && this.selecionado()?.chave === item.chave) {
      this.limparSelecao();
      return;
    }
    this.selecionado.set(item);
    this.serieSelecionado.set(null);
    this.carregandoSerie.set(true);
    const d = this.dados();
    const params = new HttpParams()
      .set('inicio', d?.inicio ?? this.dataInicio)
      .set('fim', d?.fim ?? this.dataFim)
      .set('chave', item.chave);
    this.serieSub?.unsubscribe();
    this.serieSub = this.http
      .get<MaisVendidosSerieResponse>(`${environment.apiUrl}/api/relatorios/mais-vendidos/serie`, { params })
      .subscribe({
        next: (r) => {
          this.serieSelecionado.set(r.porDia);
          this.carregandoSerie.set(false);
        },
        error: () => {
          this.carregandoSerie.set(false);
          this.snack.open('Não foi possível carregar o gráfico do produto.', 'OK', { duration: 2500 });
        },
      });
  }

  rolarParaGrafico(): void {
    this.evo?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  limparSelecao(): void {
    this.serieSub?.unsubscribe();
    this.selecionado.set(null);
    this.serieSelecionado.set(null);
    this.carregandoSerie.set(false);
  }

  exportar(): void {
    const d = this.dados();
    const itens = this.itensFiltrados();
    if (!d || !itens.length) {
      this.snack.open('Nada para exportar neste período.', 'OK', { duration: 2500 });
      return;
    }
    const num = (v: number) => v.toFixed(2).replace('.', ',');
    const esc = (s: string) => `"${s.replace(/"/g, '""')}"`;
    const linhas = [
      ['Posição', 'Produto', 'Categoria', 'Quantidade', 'Faturamento (R$)', 'Lucro (R$)', 'Pedidos', 'Qtd. período anterior'].join(';'),
      ...itens.map((i, idx) =>
        [
          idx + 1,
          esc(i.nome),
          esc(this.categoriaLabel(i.categoria)),
          i.unidades,
          num(i.valor),
          num(i.lucro),
          i.pedidos,
          i.unidadesAnterior,
        ].join(';'),
      ),
    ];
    const blob = new Blob(['\ufeff' + linhas.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `mais-vendidos_${d.inicio}_${d.fim}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
