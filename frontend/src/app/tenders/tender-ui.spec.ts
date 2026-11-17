import {
  relativeFreshness,
  scoreClass,
  staleData,
  syncStatusLabel,
  tenderStateLabel,
} from './tender-ui';

describe('tender ui helpers', () => {
  it('translates match and sync states', () => {
    expect(tenderStateLabel('FOLLOWING')).toBe('En seguimiento');
    expect(syncStatusLabel('FAILED')).toBe('Fallida');
  });

  it('groups deterministic scores for display', () => {
    expect(scoreClass(85)).toBe('high');
    expect(scoreClass(60)).toBe('medium');
    expect(scoreClass(20)).toBe('low');
  });

  it('flags missing and old source updates as stale', () => {
    const now = Date.parse('2026-09-15T12:00:00Z');
    expect(staleData(null, now)).toBe(true);
    expect(staleData('2026-09-14T11:59:00Z', now)).toBe(true);
    expect(staleData('2026-09-15T11:30:00Z', now)).toBe(false);
    expect(relativeFreshness('2026-09-15T10:00:00Z', now)).toBe('Actualizado hace 2 horas');
  });
});
