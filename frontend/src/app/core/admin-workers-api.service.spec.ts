import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminWorkersApiService, WorkerProvisioned } from './admin-workers-api.service';

describe('AdminWorkersApiService', () => {
  let service: AdminWorkersApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminWorkersApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a company subaccount and returns the one-time password payload', () => {
    const temporary: WorkerProvisioned = {
      worker: {
        workerId: 15,
        username: 'seller@example.test',
        displayName: 'Vendedora',
        roleCode: 'SELLER',
        enabled: true,
        version: 0,
      },
      temporaryPassword: 'one-time-value',
    };
    let received: WorkerProvisioned | undefined;
    service
      .create({ username: 'seller@example.test', displayName: 'Vendedora', roleCode: 'SELLER' })
      .subscribe((value) => (received = value));

    const request = http.expectOne('/api/v1/admin/workers');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.roleCode).toBe('SELLER');
    request.flush(temporary);
    expect(received).toEqual(temporary);
  });

  it('uses tenant-scoped worker routes for edit, status, and reset', () => {
    service.update(15, 'Vendedora actualizada', 2).subscribe();
    const update = http.expectOne('/api/v1/admin/workers/15');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ displayName: 'Vendedora actualizada', version: 2 });
    update.flush({});

    service.setEnabled(15, false, 3).subscribe();
    const status = http.expectOne('/api/v1/admin/workers/15/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ enabled: false, version: 3 });
    status.flush({});

    service.resetPassword(15).subscribe();
    const reset = http.expectOne('/api/v1/admin/workers/15/password-reset');
    expect(reset.request.method).toBe('POST');
    reset.flush({});
  });
});
