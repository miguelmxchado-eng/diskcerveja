import { DOCUMENT } from '@angular/common';
import { Component, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { filter } from 'rxjs/operators';
import { AuthService } from '../core/auth.service';
import { BarcodeScannerOverlayComponent } from '../shared/barcode/barcode-scanner-overlay.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, BarcodeScannerOverlayComponent],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
})
export class MainLayoutComponent {
  private readonly doc = inject(DOCUMENT);

  readonly footerYear = new Date().getFullYear();

  /** Mirrors Travl `#main-wrapper.menu-toggle` (sidebar mini on desktop, open drawer on overlay). */
  menuToggle = false;
  profileOpen = false;
  readonly contentMinHeight = signal(600);
  readonly sectionTitle = signal('Dashboard');

  private readonly titles: Record<string, string> = {
    dashboard: 'Dashboard',
    pdv: 'Pedidos (PDV)',
    'relatorio-pedidos': 'Pedidos (dia/semana/mês/ano)',
    produtos: 'Novo Produto',
    estoque: 'Estoque',
    caixa: 'Caixa',
    entregas: 'Entregas',
    usuarios: 'Usuários',
    config: 'Configurações',
  };

  constructor(
    readonly auth: AuthService,
    private readonly router: Router,
  ) {
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe(() => {
      this.refreshTitle();
      this.closeOverlayMenuAfterNav();
    });
    this.refreshTitle();
    this.updateContentMinHeight();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(ev: MouseEvent): void {
    const t = ev.target as HTMLElement | null;
    if (t?.closest?.('.header-profile')) return;
    this.profileOpen = false;
  }

  @HostListener('window:resize')
  onResize(): void {
    this.updateContentMinHeight();
  }

  flipMenu(): void {
    this.menuToggle = !this.menuToggle;
  }

  closeNavIfOverlay(): void {
    if (this.doc.body.getAttribute('data-sidebar-style') === 'overlay') {
      this.menuToggle = false;
    }
  }

  logout(): void {
    this.auth.logout();
  }

  toggleProfile(ev: Event): void {
    ev.preventDefault();
    this.profileOpen = !this.profileOpen;
  }

  private refreshTitle(): void {
    const parts = this.router.url.split('?')[0].split('/').filter(Boolean);
    const key = parts[parts.length - 1] ?? 'dashboard';
    let title = this.titles[key] ?? 'Dashboard';
    if (key === 'produtos' && !this.auth.isAdmin()) {
      title = 'Produtos';
    }
    this.sectionTitle.set(title);
  }

  private closeOverlayMenuAfterNav(): void {
    if (this.doc.body.getAttribute('data-sidebar-style') === 'overlay') {
      this.menuToggle = false;
    }
  }

  private updateContentMinHeight(): void {
    const h = this.doc.defaultView?.innerHeight ?? 600;
    this.contentMinHeight.set(Math.max(h - 45, 400));
  }
}
