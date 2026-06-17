import { Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { EntregaResumo } from '../../core/models';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-entregas',
  standalone: true,
  imports: [MatTableModule, MatButtonModule, MatSnackBarModule, MatProgressBarModule, MatIconModule, DecimalPipe, LowerCasePipe, StatusLabelPipe],
  templateUrl: './entregas.component.html',
  styleUrl: './entregas.component.scss',
})
export class EntregasComponent implements OnInit {
  cols = ['pedido', 'cliente', 'endereco', 'taxa', 'statusPedido', 'statusEntrega', 'acoes'];
  rows = signal<EntregaResumo[]>([]);
  loading = signal(false);

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.http.get<EntregaResumo[]>(`${environment.apiUrl}/api/entregas`).subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  status(pedidoId: number, status: string) {
    this.http.patch(`${environment.apiUrl}/api/pedidos/${pedidoId}/status`, { status }).subscribe({
      next: () => {
        this.snack.open('Status atualizado', 'OK', { duration: 2000 });
        this.reload();
      },
      error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 4000 }),
    });
  }
}
