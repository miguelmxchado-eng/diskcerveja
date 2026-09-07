import {
  AfterViewInit,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import * as L from 'leaflet';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ZonaEntrega } from '../../core/models';

type BairroProps = { id: string; nome: string };

const ZONE_COLORS = [
  '#D2A410',
  '#2E7D32',
  '#1565C0',
  '#C62828',
  '#6A1B9A',
  '#00838F',
  '#EF6C00',
  '#455A64',
];

@Component({
  selector: 'app-zonas-entrega',
  standalone: true,
  imports: [CommonModule, FormsModule, MatSnackBarModule],
  templateUrl: './zonas-entrega.component.html',
  styleUrl: './zonas-entrega.component.scss',
})
export class ZonasEntregaComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);
  private readonly api = `${environment.apiUrl}/api/config/zonas-entrega`;

  readonly loading = signal(true);
  readonly salvando = signal(false);
  readonly zonas = signal<ZonaEntrega[]>([]);
  readonly zonaAtivaIdx = signal(0);

  private map?: L.Map;
  private geoLayer?: L.GeoJSON;
  private geojson: GeoJSON.FeatureCollection | null = null;
  /** bairroId → índice da zona (ou -1) */
  private assignment = new Map<string, number>();

  ngOnInit(): void {
    this.http.get<ZonaEntrega[]>(this.api).subscribe({
      next: (z) => {
        const list =
          z?.length > 0
            ? z
            : [
                this.novaZona('Zona 1', 8),
                this.novaZona('Zona 2', 12),
                this.novaZona('Zona 3', 15),
              ];
        this.zonas.set(list);
        this.hydrateAssignmentsFromZonas(list);
        this.loading.set(false);
        queueMicrotask(() => this.refreshLayerStyles());
      },
      error: () => {
        this.zonas.set([
          this.novaZona('Zona 1', 8),
          this.novaZona('Zona 2', 12),
          this.novaZona('Zona 3', 15),
        ]);
        this.loading.set(false);
        this.snack.open('Não deu para carregar zonas salvas.', 'OK', { duration: 3000 });
      },
    });
  }

  ngAfterViewInit(): void {
    void this.initMap();
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  corZona(idx: number): string {
    return ZONE_COLORS[idx % ZONE_COLORS.length];
  }

  selecionarZona(idx: number): void {
    this.zonaAtivaIdx.set(idx);
  }

  patchZona(idx: number, partial: Partial<ZonaEntrega>): void {
    this.zonas.update((list) => list.map((z, i) => (i === idx ? { ...z, ...partial } : z)));
  }

  adicionarZona(): void {
    const n = this.zonas().length + 1;
    this.zonas.update((z) => [...z, this.novaZona(`Zona ${n}`, 10)]);
    this.zonaAtivaIdx.set(this.zonas().length - 1);
  }

  removerZona(idx: number): void {
    if (this.zonas().length <= 1) {
      this.snack.open('Deixe ao menos uma zona.', 'OK', { duration: 2500 });
      return;
    }
    for (const [id, zi] of [...this.assignment.entries()]) {
      if (zi === idx) this.assignment.delete(id);
      else if (zi > idx) this.assignment.set(id, zi - 1);
    }
    this.zonas.update((z) => z.filter((_, i) => i !== idx));
    this.zonaAtivaIdx.set(Math.min(this.zonaAtivaIdx(), this.zonas().length - 1));
    this.refreshLayerStyles();
  }

  limparZonaAtiva(): void {
    const idx = this.zonaAtivaIdx();
    for (const [id, zi] of [...this.assignment.entries()]) {
      if (zi === idx) this.assignment.delete(id);
    }
    this.refreshLayerStyles();
  }

  bairrosDaZona(idx: number): string[] {
    const nomes: string[] = [];
    if (!this.geojson) return nomes;
    for (const f of this.geojson.features) {
      const id = String((f.properties as BairroProps)?.id ?? '');
      if (this.assignment.get(id) === idx) {
        nomes.push(String((f.properties as BairroProps)?.nome ?? id));
      }
    }
    return nomes.sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  salvar(): void {
    const payload = this.zonas().map((z, i) => {
      const bairros = this.bairrosDaZona(i);
      return {
        id: null,
        nome: z.nome?.trim() || `Zona ${i + 1}`,
        taxa: Number(z.taxa) || 0,
        cepPrefixos: z.cepPrefixos?.trim() || '',
        bairros: bairros.join(', '),
        ativo: z.ativo !== false,
        ordem: i,
      };
    });

    const vazias = payload.filter((p) => !p.bairros && !p.cepPrefixos);
    if (vazias.length) {
      this.snack.open(
        `Pinte bairros no mapa para: ${vazias.map((v) => v.nome).join(', ')}`,
        'OK',
        { duration: 4500 },
      );
      return;
    }

    this.salvando.set(true);
    this.http.put<ZonaEntrega[]>(this.api, payload).subscribe({
      next: (res) => {
        this.salvando.set(false);
        this.zonas.set(res ?? payload);
        this.hydrateAssignmentsFromZonas(this.zonas());
        this.refreshLayerStyles();
        this.snack.open('Zonas salvas. O frete do cardápio usa o bairro do cliente.', 'OK', {
          duration: 4000,
        });
      },
      error: (e) => {
        this.salvando.set(false);
        this.snack.open(e?.error?.erro || 'Erro ao salvar zonas.', 'OK', { duration: 4000 });
      },
    });
  }

  private novaZona(nome: string, taxa: number): ZonaEntrega {
    return { nome, taxa, cepPrefixos: '', bairros: '', ativo: true, ordem: 0 };
  }

  private hydrateAssignmentsFromZonas(zonas: ZonaEntrega[]): void {
    this.assignment.clear();
    if (!this.geojson) return;
    const byNorm = new Map<string, string>();
    for (const f of this.geojson.features) {
      const p = f.properties as BairroProps;
      byNorm.set(this.norm(p.nome), p.id);
    }
    zonas.forEach((z, zi) => {
      const lista = (z.bairros || '')
        .split(/[,;\n]/)
        .map((s) => s.trim())
        .filter(Boolean);
      for (const nome of lista) {
        const id = byNorm.get(this.norm(nome));
        if (id) this.assignment.set(id, zi);
      }
    });
  }

  private norm(s: string): string {
    return (s || '')
      .normalize('NFD')
      .replace(/\p{M}+/gu, '')
      .toLowerCase()
      .replace(/^bairro\s+/, '')
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  }

  private async initMap(): Promise<void> {
    const el = document.getElementById('mapa-zonas-anapolis');
    if (!el || this.map) return;

    this.map = L.map(el, { zoomControl: true }).setView([-16.3281, -48.953], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 18,
      attribution: '&copy; OpenStreetMap',
    }).addTo(this.map);

    this.geojson = await firstValueFrom(
      this.http.get<GeoJSON.FeatureCollection>('assets/geo/bairros-anapolis.geojson'),
    );

    this.hydrateAssignmentsFromZonas(this.zonas());

    this.geoLayer = L.geoJSON(this.geojson, {
      style: (feat) => this.styleFor(feat),
      onEachFeature: (feat, layer) => {
        const p = feat.properties as BairroProps;
        layer.bindTooltip(p.nome, { sticky: true });
        layer.on('click', () => this.toggleBairro(p.id, layer as L.Path));
      },
    }).addTo(this.map);

    const bounds = this.geoLayer.getBounds();
    if (bounds.isValid()) this.map.fitBounds(bounds.pad(0.05));
  }

  private styleFor(feat?: GeoJSON.Feature): L.PathOptions {
    const id = String((feat?.properties as BairroProps)?.id ?? '');
    const zi = this.assignment.get(id);
    if (zi == null || zi < 0) {
      return {
        color: '#666',
        weight: 1,
        fillColor: '#cfcfcf',
        fillOpacity: 0.35,
      };
    }
    const c = this.corZona(zi);
    return { color: c, weight: 2, fillColor: c, fillOpacity: 0.55 };
  }

  private toggleBairro(id: string, layer: L.Path): void {
    const ativa = this.zonaAtivaIdx();
    const cur = this.assignment.get(id);
    if (cur === ativa) this.assignment.delete(id);
    else this.assignment.set(id, ativa);
    layer.setStyle(this.styleFor({ type: 'Feature', properties: { id, nome: '' }, geometry: null as never }));
  }

  private refreshLayerStyles(): void {
    this.geoLayer?.eachLayer((layer) => {
      const path = layer as L.Path & { feature?: GeoJSON.Feature };
      if (path.feature) {
        path.setStyle(this.styleFor(path.feature));
      }
    });
  }
}
