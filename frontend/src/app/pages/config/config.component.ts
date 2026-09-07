import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ConfigCaixaResponse, LojaConfig, ZonaEntrega } from '../../core/models';

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './config.component.html',
})
export class ConfigComponent implements OnInit {
  private http = inject(HttpClient);
  private apiCaixa = `${environment.apiUrl}/api/config/caixa`;
  private apiLoja = `${environment.apiUrl}/api/config/loja`;
  private apiZonas = `${environment.apiUrl}/api/config/zonas-entrega`;

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
  zonas = signal<ZonaEntrega[]>([]);
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
      next: (res) => this.loja.set(res),
      error: () => this.erro.set('Erro ao carregar configurações da loja.'),
    });
    this.http.get<ZonaEntrega[]>(this.apiZonas).subscribe({
      next: (res) => {
        this.zonas.set(res ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.erro.set('Erro ao carregar zonas de entrega.');
        this.loading.set(false);
      },
    });
  }

  patchLoja(partial: Partial<LojaConfig>) {
    this.loja.update((l) => ({ ...l, ...partial }));
  }

  adicionarZona() {
    this.zonas.update((z) => [
      ...z,
      {
        nome: '',
        taxa: Number(this.loja().taxaEntrega) || 10,
        cepPrefixos: '',
        bairros: '',
        ativo: true,
        ordem: z.length,
      },
    ]);
  }

  removerZona(idx: number) {
    this.zonas.update((z) => z.filter((_, i) => i !== idx));
  }

  patchZona(idx: number, partial: Partial<ZonaEntrega>) {
    this.zonas.update((list) => list.map((z, i) => (i === idx ? { ...z, ...partial } : z)));
  }

  salvar() {
    this.salvando.set(true);
    this.sucesso.set(null);
    this.erro.set(null);
    const l = this.loja();
    const zonasPayload = this.zonas().map((z, i) => ({
      id: z.id ?? null,
      nome: z.nome,
      taxa: Number(z.taxa) || 0,
      cepPrefixos: z.cepPrefixos,
      bairros: z.bairros ?? '',
      ativo: !!z.ativo,
      ordem: i,
    }));

    this.http.patch<ConfigCaixaResponse>(this.apiCaixa, { caixaObrigatorio: this.caixaObrigatorio() }).subscribe({
      next: (res) => this.caixaObrigatorio.set(res.caixaObrigatorio),
      error: () => {
        this.erro.set('Erro ao salvar caixa.');
        this.salvando.set(false);
      },
    });
    this.http.patch<LojaConfig>(this.apiLoja, l).subscribe({
      next: (res) => this.loja.set(res),
      error: () => {
        this.erro.set('Erro ao salvar loja.');
        this.salvando.set(false);
      },
    });
    this.http.put<ZonaEntrega[]>(this.apiZonas, zonasPayload).subscribe({
      next: (res) => {
        this.zonas.set(res ?? []);
        this.sucesso.set('Configuração salva com sucesso!');
        this.salvando.set(false);
      },
      error: (e) => {
        this.erro.set(e?.error?.erro || 'Erro ao salvar zonas de entrega.');
        this.salvando.set(false);
      },
    });
  }
}
