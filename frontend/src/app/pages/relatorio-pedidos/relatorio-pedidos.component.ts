import { Component, OnInit, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { DecimalPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { PedidoPeriodoResponse, PeriodoPedido } from '../../core/models';

@Component({
  selector: 'app-relatorio-pedidos',
  standalone: true,
  imports: [MatTabsModule, MatTableModule, MatButtonModule, MatCardModule, MatSnackBarModule, MatProgressBarModule, DecimalPipe, DatePipe, LowerCasePipe, StatusLabelPipe],
  templateUrl: './relatorio-pedidos.component.html',
  styleUrl: './relatorio-pedidos.component.scss',
})
export class RelatorioPedidosComponent implements OnInit {
  private static readonly TAB_MAP: PeriodoPedido[] = ['DIA', 'SEMANA', 'MES', 'ANO'];

  readonly tabIndex = signal(0);
  readonly periodoAtual = signal<PeriodoPedido>('DIA');
  readonly dados = signal<PedidoPeriodoResponse | null>(null);
  /** Inicia em true para o primeiro paint não mostrar tabela vazia antes do GET. */
  readonly carregando = signal(true);

  cols = ['id', 'data', 'cliente', 'tipo', 'status', 'total', 'pagamento', 'caixa'];

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  onTabChange(index: number) {
    if (index < 0 || index >= RelatorioPedidosComponent.TAB_MAP.length) {
      return;
    }
    this.tabIndex.set(index);
    this.periodoAtual.set(RelatorioPedidosComponent.TAB_MAP[index] ?? 'DIA');
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    const p = this.periodoAtual();
    const params = new HttpParams().set('periodo', p);
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

  sincronizarCaixa() {
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
}
