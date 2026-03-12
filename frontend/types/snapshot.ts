export interface CodeChange {
  file: string;
  action: string;
  summary: string;
  details: string;
}

export interface KeyDecision {
  decision: string;
  rationale: string;
  alternatives_considered: string;
}

export interface ContextSnapshot {
  timestamp: string;
  project_name: string;
  session_id: string;
  current_goal: string;
  active_files: string[];
  architectural_decisions: string;
  unresolved_issues: string;
  // Enriched fields (v2)
  conversation_summary: string;
  tech_stack: string[];
  code_changes: CodeChange[];
  key_decisions_log: KeyDecision[];
  progress_status: string;
  next_steps: string[];
  related_snapshots: string[];
}

export interface ProjectStat {
  project_name: string;
  total_sessions: number;
  last_active: string;
  latest_goal: string;
  latest_status: string;
  completed: number;
  in_progress: number;
  blocked: number;
  tech_stack: string[];
}

export interface StatsResponse {
  total_projects: number;
  projects: ProjectStat[];
}

export interface RecallResponse {
  query: string;
  results_count: number;
  snapshots: ContextSnapshot[];
}

export interface DecisionResult {
  project_name: string;
  session_id: string;
  timestamp: string;
  goal_context: string;
  decision: string;
  rationale: string;
  alternatives_considered: string;
}

export interface DecisionsResponse {
  query: string;
  results_count: number;
  decisions: DecisionResult[];
}
