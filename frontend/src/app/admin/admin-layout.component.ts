import { DestroyRef, Component, computed, effect, inject, untracked } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../core/auth.service';
import { navigationForRole } from '../core/role-navigation';
import { TenantBrandingService } from '../core/tenant-branding.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.css',
})
export class AdminLayoutComponent {
  readonly auth = inject(AuthService);
  readonly branding = inject(TenantBrandingService);
  readonly navigationItems = computed(() => navigationForRole(this.auth.session()?.roleCode));
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.updateDocumentTitle());

    effect((onCleanup) => {
      const session = this.auth.session();
      this.branding.clear();
      if (!session) {
        untracked(() => this.updateDocumentTitle());
        return;
      }

      const request: Subscription = this.branding.load().subscribe({
        next: () => this.updateDocumentTitle(),
        error: () => {
          this.branding.clear();
          this.updateDocumentTitle();
        },
      });
      onCleanup(() => request.unsubscribe());
    });
  }

  logout(): void {
    this.branding.clear();
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/trabajadores/login']),
      error: () => void this.router.navigate(['/trabajadores/login']),
    });
  }

  private updateDocumentTitle(): void {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) route = route.firstChild;
    const pageTitle = route.title ?? 'Operaciones';
    const tenantName = this.branding.branding()?.displayName;
    this.title.setTitle(`${pageTitle} | ${tenantName ?? 'GIOVA'}`);
  }
}
