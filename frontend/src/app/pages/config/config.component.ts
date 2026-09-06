import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ConfigCaixaResponse, LojaConfig } from '../../core/models';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './config.component.html',
})
export class ConfigComponent implements OnInit {
  private http = inject(HttpClient);
  private apiCaixa = `${environment.apiUrl}/api/config/caixa`;
  private apiLoja = `${environment.apiUrl}/api/config/loja`;

  caixaObrigatorio = signal(false);
  loja = signal<LojaConfig>({
    nome: 'Empório Machado',
    whatsapp: '',
    aberta: true,
    horario: '',
    taxaEntrega: 5,
    pedidoMinimo: 20,
    info: '',
    infinitepayHandle: '',
    publicBaseUrl: '',
  });
  loading = signal(false);
  salvando = signal(false);
  sucesso = signal<string | null>(null);
  erro = signal<string | null>(null);

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.loading.set(true);
    this.erro.set(null);
    this.http.get<ConfigCaixaResponse>(this.apiCaixa).subscribe({
      next: (res) => this.caixaObrigatorio.set(res.caixaObrigatorio),
      error: () => this.erro.set('Erro ao carregar configurações.'),
    });
    this.http.get<LojaConfig>(this.apiLoja).subscribe({
      next: (res) => {
        this.loja.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar configurações da loja.');
        this.loading.set(false);
      },
    });
  }

  patchLoja(partial: Partial<LojaConfig>) {
    this.loja.update((l) => ({ ...l, ...partial }));
  }

  salvar() {
    this.salvando.set(true);
    this.sucesso.set(null);
    this.erro.set(null);
    const l = this.loja();
    this.http.patch<ConfigCaixaResponse>(this.apiCaixa, { caixaObrigatorio: this.caixaObrigatorio() }).subscribe({
      next: (res) => this.caixaObrigatorio.set(res.caixaObrigatorio),
      error: () => {
        this.erro.set('Erro ao salvar caixa.');
        this.salvando.set(false);
      },
    });
    this.http.patch<LojaConfig>(this.apiLoja, l).subscribe({
      next: (res) => {
        this.loja.set(res);
        this.sucesso.set('Configuração salva com sucesso!');
        this.salvando.set(false);
      },
      error: () => {
        this.erro.set('Erro ao salvar loja.');
        this.salvando.set(false);
      },
    });
  }
}
