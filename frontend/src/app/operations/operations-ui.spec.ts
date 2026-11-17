import { goalTypeLabel, percent, taskPriorityLabel, taskStatusLabel } from './operations-ui';

describe('operations ui helpers', () => {
  it('translates task labels', () => {
    expect(taskPriorityLabel('HIGH')).toBe('Alta');
    expect(taskStatusLabel('IN_PROGRESS')).toBe('En curso');
  });

  it('translates goal types', () => {
    expect(goalTypeLabel('TASKS_COMPLETED')).toBe('Tareas completadas');
  });

  it('clamps progress to a display-safe percentage', () => {
    expect(percent(140)).toBe(100);
    expect(percent(-3)).toBe(0);
  });
});
