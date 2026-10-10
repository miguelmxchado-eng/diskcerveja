import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AnotacaoDto } from './models';

@Injectable({ providedIn: 'root' })
export class AnotacaoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/anotacoes`;

  listar(concluidas: boolean, q?: string): Observable<AnotacaoDto[]> {
    let params = new HttpParams().set('concluidas', String(concluidas));
    if (q?.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<AnotacaoDto[]>(this.base, { params });
  }

  contagem(): Observable<{ abertas: number; concluidas: number }> {
    return this.http.get<{ abertas: number; concluidas: number }>(`${this.base}/contagem`);
  }

  criar(dto: AnotacaoDto): Observable<AnotacaoDto> {
    return this.http.post<AnotacaoDto>(this.base, dto);
  }

  atualizar(id: number, dto: AnotacaoDto): Observable<AnotacaoDto> {
    return this.http.put<AnotacaoDto>(`${this.base}/${id}`, dto);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
