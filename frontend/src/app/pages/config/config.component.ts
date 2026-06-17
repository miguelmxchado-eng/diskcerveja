import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ConfigCaixaResponse } from '../../core/models';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './config.component.html',
})
export class ConfigComponent implements OnInit {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/api/config/caixa`;

  caixaObrigatorio = signal(false);
  loading = signal(false);
  salvando = signal(false);
  sucesso = signal<string | null>(null);
  erro = signal<string | null>(null);

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.loading.set(true);
    this.http.get<ConfigCaixaResponse>(this.api).subscribe({
      next: (res) => { this.caixaObrigatorio.set(res.caixaObrigatorio); this.loading.set(false); },
      error: () => { this.erro.set('Erro ao carregar configurações.'); this.loading.set(false); },
    });
  }

  salvar() {
    this.salvando.set(true);
    this.sucesso.set(null);
    this.erro.set(null);
    this.http.patch<ConfigCaixaResponse>(this.api, { caixaObrigatorio: this.caixaObrigatorio() }).subscribe({
      next: (res) => {
        this.caixaObrigatorio.set(res.caixaObrigatorio);
        this.sucesso.set('Configuração salva com sucesso!');
        this.salvando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao salvar configuração.');
        this.salvando.set(false);
      },
    });
  }
}
