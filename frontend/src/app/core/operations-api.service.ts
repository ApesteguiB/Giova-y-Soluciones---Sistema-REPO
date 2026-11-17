import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type GoalType =
  'QUOTATIONS_ISSUED' | 'QUOTATION_AMOUNT' | 'TENDERS_FOLLOWED' | 'TASKS_COMPLETED';

export interface TaskView {
  publicId: string;
  title: string;
  description: string | null;
  assignedToWorkerId: number;
  assignedToDisplayName: string;
  createdByWorkerId: number;
  createdByDisplayName: string;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  dueAt: string | null;
  completedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TaskPage {
  items: TaskView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface TaskInput {
  title: string;
  description: string;
  assignedToWorkerId: number | null;
  relatedEntityType: string;
  relatedEntityId: string;
  priority: TaskPriority;
  dueAt: string | null;
}

export interface TaskUpdate extends TaskInput {
  version: number;
}

export interface TaskAssignee {
  id: number;
  displayName: string;
  roleCode: string;
}

export interface GoalView {
  publicId: string;
  workerId: number;
  workerDisplayName: string;
  goalType: GoalType;
  periodStart: string;
  periodEnd: string;
  targetValue: number;
  currentValue: number;
  progressPercent: number;
  notes: string | null;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface GoalPage {
  items: GoalView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface GoalInput {
  workerId: number;
  goalType: GoalType;
  periodStart: string;
  periodEnd: string;
  targetValue: number;
  notes: string;
  active: boolean;
}

export interface GoalUpdate extends GoalInput {
  version: number;
}

export interface AuditView {
  eventId: string;
  actorWorkerId: number | null;
  actorDisplayName: string | null;
  roleSnapshot: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  result: 'SUCCESS' | 'FAILURE';
  requestId: string | null;
  summary: string | null;
  createdAt: string;
}

export interface AuditPage {
  items: AuditView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class OperationsApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  private write<T>(request: () => Observable<T>): Observable<T> {
    return this.csrf.ensureToken().pipe(switchMap(() => request()));
  }

  tasks(
    query: {
      q?: string;
      status?: TaskStatus;
      assignedToWorkerId?: number;
      page?: number;
      size?: number;
    } = {},
  ) {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.assignedToWorkerId)
      params = params.set('assignedToWorkerId', query.assignedToWorkerId);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<TaskPage>('/api/v1/tasks', { params });
  }

  task(publicId: string) {
    return this.http.get<TaskView>(`/api/v1/tasks/${publicId}`);
  }

  taskAssignees() {
    return this.http.get<TaskAssignee[]>('/api/v1/tasks/assignees');
  }

  createTask(input: TaskInput) {
    return this.write(() => this.http.post<TaskView>('/api/v1/tasks', input));
  }

  updateTask(publicId: string, input: TaskUpdate) {
    return this.write(() => this.http.put<TaskView>(`/api/v1/tasks/${publicId}`, input));
  }

  changeTaskStatus(publicId: string, targetStatus: TaskStatus, version: number) {
    return this.write(() =>
      this.http.post<TaskView>(`/api/v1/tasks/${publicId}/status`, { targetStatus, version }),
    );
  }

  goals(
    query: {
      workerId?: number;
      goalType?: GoalType;
      active?: boolean;
      page?: number;
      size?: number;
    } = {},
  ) {
    let params = new HttpParams();
    if (query.workerId) params = params.set('workerId', query.workerId);
    if (query.goalType) params = params.set('goalType', query.goalType);
    if (query.active !== undefined) params = params.set('active', query.active);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<GoalPage>('/api/v1/goals', { params });
  }

  goal(publicId: string) {
    return this.http.get<GoalView>(`/api/v1/goals/${publicId}`);
  }

  createGoal(input: GoalInput) {
    return this.write(() => this.http.post<GoalView>('/api/v1/goals', input));
  }

  updateGoal(publicId: string, input: GoalUpdate) {
    return this.write(() => this.http.put<GoalView>(`/api/v1/goals/${publicId}`, input));
  }

  auditEvents(
    query: {
      q?: string;
      action?: string;
      result?: 'SUCCESS' | 'FAILURE';
      page?: number;
      size?: number;
    } = {},
  ) {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.action?.trim()) params = params.set('action', query.action.trim());
    if (query.result) params = params.set('result', query.result);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<AuditPage>('/api/v1/admin/audit-events', { params });
  }

  downloadReport(
    kind: 'quotations' | 'accounting',
    query: { from?: string; to?: string; status?: string; currency?: string } = {},
  ) {
    let params = new HttpParams();
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.status) params = params.set('status', query.status);
    if (query.currency) params = params.set('currency', query.currency);
    return this.http.get(`/api/v1/reports/${kind}.xlsx`, {
      params,
      responseType: 'blob',
      observe: 'response',
    });
  }
}
