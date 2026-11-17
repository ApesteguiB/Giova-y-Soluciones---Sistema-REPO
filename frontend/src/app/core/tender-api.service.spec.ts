import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CsrfService } from './csrf.service';
import { TenderApiService } from './tender-api.service';

describe('TenderApiService', () => {
  let api: TenderApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TenderApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CsrfService, useValue: { ensureToken: () => of({ token: 'test-token' }) } },
      ],
    });
    api = TestBed.inject(TenderApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serializes all list filters and pagination', () => {
    api
      .tenders({
        state: 'FOLLOWING',
        q: '  equipos médicos ',
        buyer: 'Municipalidad',
        region: 'Lima',
        from: '2026-09-01',
        to: '2026-09-30',
        minScore: 70,
        page: 2,
        size: 20,
      })
      .subscribe();

    const request = http.expectOne(
      '/api/v1/tenders?state=FOLLOWING&q=equipos%20m%C3%A9dicos&buyer=Municipalidad&region=Lima&from=2026-09-01&to=2026-09-30&minScore=70&page=2&size=20',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], totalItems: 0, page: 2, size: 20, totalPages: 0 });
  });

  it('sends state changes only after ensuring CSRF', () => {
    api
      .changeMatchState('process-1', {
        profilePublicId: 'profile-1',
        targetState: 'DISMISSED',
        note: '',
        version: 3,
      })
      .subscribe();

    const request = http.expectOne('/api/v1/tenders/process-1/match-state');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      profilePublicId: 'profile-1',
      targetState: 'DISMISSED',
      note: '',
      version: 3,
    });
    request.flush({});
  });
});
