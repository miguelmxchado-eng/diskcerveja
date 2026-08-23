import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ClienteDto } from './models';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/clientes`;

  listar(q?: string): Observable<ClienteDto[]> {
    let params = new HttpParams();
    if (q?.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<ClienteDto[]>(this.base, { params });
  }

  criar(dto: ClienteDto): Observable<ClienteDto> {
    return this.http.post<ClienteDto>(this.base, dto);
  }

  atualizar(id: number, dto: ClienteDto): Observable<ClienteDto> {
    return this.http.put<ClienteDto>(`${this.base}/${id}`, dto);
  }
}
