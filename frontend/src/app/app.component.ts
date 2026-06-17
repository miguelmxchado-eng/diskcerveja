import { Component, HostListener, inject, OnInit } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class AppComponent implements OnInit {
  private readonly doc = inject(DOCUMENT);

  ngOnInit(): void {
    this.applyTravlBodyAttrs();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.applySidebarStyleForViewport();
  }

  private applyTravlBodyAttrs(): void {
    const body = this.doc.body;
    body.setAttribute('data-typography', 'poppins');
    body.setAttribute('data-theme-version', 'light');
    body.setAttribute('data-layout', 'vertical');
    body.setAttribute('data-primary', 'color_1');
    body.setAttribute('data-nav-headerbg', 'color_1');
    body.setAttribute('data-headerbg', 'color_1');
    body.setAttribute('data-sibebarbg', 'color_1');
    body.setAttribute('data-sidebar-position', 'fixed');
    body.setAttribute('data-header-position', 'fixed');
    body.setAttribute('data-container', 'wide');
    body.setAttribute('direction', 'ltr');
    const html = this.doc.documentElement;
    html.setAttribute('dir', 'ltr');
    html.className = 'ltr';
    this.applySidebarStyleForViewport();
  }

  private applySidebarStyleForViewport(): void {
    const w = this.doc.defaultView?.innerWidth ?? 1200;
    const body = this.doc.body;
    if (w >= 768 && w < 1024) {
      body.setAttribute('data-sidebar-style', 'mini');
    } else if (w <= 767) {
      body.setAttribute('data-sidebar-style', 'overlay');
    } else {
      body.setAttribute('data-sidebar-style', 'full');
    }
  }
}
