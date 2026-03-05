export interface ContextSnapshot {
  timestamp: string;
  project_name: string;
  session_id: string;
  current_goal: string;
  active_files: string[];
  architectural_decisions: string;
  unresolved_issues: string;
}
