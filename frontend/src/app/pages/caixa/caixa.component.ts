import { Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe, DatePipe } from '@angular/common';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-caixa',
  standalone: true,
  imports: [
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSnackBarModule,
    MatTableModule,
    MatProgressBarModule,
    MatDialogModule,
    MatIconModule,
    DecimalPipe,
    DatePipe,
  ],
  templateUrl: './caixa.component.html',
  styleUrl: './caixa.component.scss',
})
export class CaixaComponent implements OnInit {
  sessao = signal<any | null>(null);
  loading = signal(false);
  valorAbertura = 0;
  valorFechamento = 0;
  movTipo: 'SAIDA_TROCO' | 'SAIDA_DESPESA' = 'SAIDA_TROCO';
  movValor = 0;
  movDesc = '';

  cols = ['tipo', 'valor', 'descricao', 'data'];

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
          this.reload();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 4000 }),
      });
  }
}
