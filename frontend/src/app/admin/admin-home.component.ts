import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css',
})
export class AdminHomeComponent {
  private readonly auth = inject(AuthService);
  readonly isAdmin = computed(() => this.auth.session()?.roleCode === 'ADMIN');
  readonly isAccountant = computed(() => this.auth.session()?.roleCode === 'ACCOUNTANT');
  readonly canSales = computed(() => this.auth.session() !== null);
}
