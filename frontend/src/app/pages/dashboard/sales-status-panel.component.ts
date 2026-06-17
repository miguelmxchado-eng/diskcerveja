import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { DashboardResponse, PontoGraficoVendas } from '../../core/models';

export type SalesChartTab = 'daily' | 'weekly' | 'monthly';

@Component({
  selector: 'app-sales-status-panel',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './sales-status-panel.component.html',
  styleUrl: './sales-status-panel.component.scss',
})
export class SalesStatusPanelComponent {
  d = input.required<DashboardResponse>();
  readonly pageDarkChange = output<boolean>();

  readonly tab = signal<SalesChartTab>('monthly');
  readonly dark = signal(false);
  readonly hoverIdx = signal<number | null>(null);

  readonly series = computed(() => {
    const x = this.d();
    switch (this.tab()) {
      case 'daily':
        return x.graficoDiario ?? [];
      case 'weekly':
        return x.graficoSemanal ?? [];
      default:
        return x.graficoMensal ?? [];
    }
  });

  readonly maxVendas = computed(() => Math.max(...this.series().map((s) => s.vendas), 1e-9));
  readonly maxCanc = computed(() => Math.max(...this.series().map((s) => s.cancelamentos), 1));

  pctV(v: number): number {
    return Math.min(100, (v / this.maxVendas()) * 100);
  }

  pctC(c: number): number {
    return Math.min(100, (c / this.maxCanc()) * 100);
  }

  lucroMil(p: PontoGraficoVendas): string {
    return (p.vendas / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 });
  }

  variacaoVendas(): string | null {
    const o = this.d().vendasOntem;
    const h = this.d().vendasHoje;
    if (o <= 0) return null;
    const p = ((h - o) / o) * 100;
    return (p >= 0 ? '+' : '') + p.toFixed(1) + '%';
  }

  setTab(t: SalesChartTab): void {
    this.tab.set(t);
  }

  toggleDark(): void {
    this.dark.update((x) => !x);
    this.pageDarkChange.emit(this.dark());
  }
}
