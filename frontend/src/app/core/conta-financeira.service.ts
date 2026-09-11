import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ContaFinanceiraDto, StatusContaFinanceira, TipoContaFinanceira } from './models';

@Injectable({ providedIn: 'root' })
export class ContaFinanceiraService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/contas`;

  listar(
    tipo: TipoContaFinanceira,
    status?: StatusContaFinanceira | null,
    q?: string,
  ): Observable<ContaFinanceiraDto[]> {
    let params = new HttpParams().set('tipo', tipo);
    if (status) {
      params = params.set('status', status);
    }
    if (q?.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<ContaFinanceiraDto[]>(this.base, { params });
  }

  criar(dto: ContaFinanceiraDto): Observable<ContaFinanceiraDto> {
    return this.http.post<ContaFinanceiraDto>(this.base, dto);
  }

  atualizar(id: number, dto: ContaFinanceiraDto): Observable<ContaFinanceiraDto> {
    return this.http.put<ContaFinanceiraDto>(`${this.base}/${id}`, dto);
  }

  quitar(
    id: number,
    body?: { formaPagamento?: string | null; dataPagamento?: string | null },
  ): Observable<ContaFinanceiraDto> {
    return this.http.post<ContaFinanceiraDto>(`${this.base}/${id}/quitar`, body ?? {});
  }

  cancelar(id: number): Observable<ContaFinanceiraDto> {
    return this.http.post<ContaFinanceiraDto>(`${this.base}/${id}/cancelar`, {});
  }
}
