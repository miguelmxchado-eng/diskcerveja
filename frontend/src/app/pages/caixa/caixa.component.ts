import { Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe, DatePipe } from '@angular/common';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-caixa',
  standalone: true,
  imports: [
    FormsModule,
    MatSnackBarModule,
    MatDialogModule,
    MatIconModule,
    DecimalPipe,
    DatePipe,
    StatusLabelPipe,
  ],
  templateUrl: './caixa.component.html',
  styleUrl: './caixa.component.scss',
})
export class CaixaComponent implements OnInit {
  sessao = signal<any | null>(null);
  loading = signal(false);
  valorAbertura = 200;
  valorFechamento = 0;
  observacao = '';
  responsavel = 'Administrador';
  movTipo: 'SAIDA_TROCO' | 'SAIDA_DESPESA' = 'SAIDA_TROCO';
  movValor = 0;
  movDesc = '';

  readonly ultimoFechamento = {
    data: new Date('2026-08-22T23:48:00'),
    responsavel: 'Administrador',
    saldoInicial: 200,
    vendas: 1842.3,
    sangrias: 300,
    saldoEsperado: 1742.3,
    saldoInformado: 1742.3,
  };

  readonly kpis = {
    ultimos7Dias: 9486.2,
    mediaDiaria: 1355.17,
    diferencas: 0,
  };

  readonly checklist = [
    'Conferir o troco e notas disponíveis',
    'Verificar se o PDV está operacional',
    'Confirmar o saldo do último fechamento',
  ];

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.http.get(`${environment.apiUrl}/api/caixa/sessao`, { observe: 'response' }).subscribe({
      next: (r) => {
        this.sessao.set(r.status === 204 ? null : r.body);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  abrir() {
    this.http.post(`${environment.apiUrl}/api/caixa/abertura`, { valorAbertura: this.valorAbertura }).subscribe({
      next: () => {
        this.snack.open('Caixa aberto', 'OK', { duration: 2000 });
        this.reload();
      },
      error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 4000 }),
    });
  }

  fechar() {
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        titulo: 'Fechar caixa',
        mensagem: 'Confirma o fechamento do caixa? Esta ação não pode ser desfeita.',
        confirmLabel: 'Fechar caixa',
        confirmColor: 'warn',
      },
      width: '380px',
    }).afterClosed().subscribe((ok) => {
      if (!ok) return;
      this.http.post(`${environment.apiUrl}/api/caixa/fechamento`, { valorFechamento: this.valorFechamento }).subscribe({
        next: () => {
          this.snack.open('Caixa fechado com sucesso', 'OK', { duration: 2000 });
          this.reload();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro ao fechar', 'OK', { duration: 4000 }),
      });
    });
  }

  movimento() {
    this.http
      .post(`${environment.apiUrl}/api/caixa/movimento`, {
        tipo: this.movTipo,
        valor: this.movValor,
        descricao: this.movDesc,
      })
      .subscribe({
        next: () => {
          this.snack.open('Movimento registrado', 'OK', { duration: 2000 });
          this.movValor = 0;
          this.movDesc = '';
          this.reload();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 4000 }),
      });
  }

  totalSangrias(movimentos: { tipo: string; valor: number }[] | undefined): number {
    if (!movimentos?.length) return 0;
    return movimentos
      .filter((m) => m.tipo === 'SAIDA_TROCO' || m.tipo === 'SAIDA_DESPESA')
      .reduce((acc, m) => acc + (m.valor ?? 0), 0);
  }

  totalVendas(movimentos: { tipo: string; valor: number }[] | undefined): number {
    if (!movimentos?.length) return 0;
    return movimentos
      .filter((m) => m.tipo === 'ENTRADA_VENDA')
      .reduce((acc, m) => acc + (m.valor ?? 0), 0);
  }

  verHistorico() {
    this.snack.open('Histórico de caixas em breve.', 'OK', { duration: 2500 });
  }

  verDetalhesFechamento() {
    this.snack.open('Detalhes do fechamento em breve.', 'OK', { duration: 2500 });
  }
}
