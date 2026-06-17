import { Component, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe, KeyValuePipe, DatePipe } from '@angular/common';
import { environment } from '../../../environments/environment';
import { DashboardResponse } from '../../core/models';
import { SalesStatusPanelComponent } from './sales-status-panel.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    MatCardModule,
    MatProgressBarModule,
    MatButtonModule,
    MatIconModule,
    DecimalPipe,
    KeyValuePipe,
    DatePipe,
    SalesStatusPanelComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  readonly apiBase = environment.apiUrl;
  readonly loading = signal(true);
  readonly data = signal<DashboardResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly salesPageDark = signal(false);

  today = new Date().toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
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
  }

  /** Texto curto para o card de vendas (usa `vendasOntem` da API). */
  variacaoVendasHint(d: DashboardResponse): string | null {
    const o = Number(d.vendasOntem);
    const h = Number(d.vendasHoje);
    if (o <= 0) return null;
    const p = ((h - o) / o) * 100;
    return (p >= 0 ? '+' : '') + p.toFixed(1) + '% vs ontem';
  }

  private messageForError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return `Sem resposta de ${environment.apiUrl}. Verifique se o backend está no ar (GET /api/dashboard) e se não há bloqueio de rede ou CORS.`;
    }
    if (err.status === 401) {
      return 'Sessão expirada ou não autenticado. Você será redirecionado ao login.';
    }
    if (err.status === 403) {
      return 'Acesso negado a este recurso.';
    }
    if (err.status >= 500) {
      return 'Erro no servidor ao montar o painel. Tente novamente em instantes.';
    }
    const body = err.error as { detail?: string; message?: string } | null;
    const msg = body?.detail ?? body?.message;
    if (typeof msg === 'string' && msg.length > 0) {
      return msg;
    }
    return `Falha ao carregar o painel (HTTP ${err.status}).`;
  }
}
