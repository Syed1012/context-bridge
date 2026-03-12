"use client";

import { useEffect, useState, useCallback } from "react";
import { ContextSnapshot, ProjectStat, StatsResponse, RecallResponse } from "@/types/snapshot";
import SnapshotCard from "@/components/SnapshotCard";
import {
    Brain,
    RefreshCw,
    Activity,
    Boxes,
    Search,
    BarChart3,
    Terminal,
    Copy,
    Check,
    Zap,
    ArrowRight,
    Sparkles,
    FolderGit2,
    CheckCircle2,
    Clock,
    AlertCircle,
} from "lucide-react";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:9090";
const POLL_INTERVAL_MS = 5_000;

type Tab = "snapshots" | "search" | "setup";

export default function DashboardPage() {
    const [snapshots, setSnapshots] = useState<ContextSnapshot[]>([]);
    const [stats, setStats] = useState<ProjectStat[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [lastRefresh, setLastRefresh] = useState<Date | null>(null);
    const [projectFilter, setProjectFilter] = useState("");
    const [activeTab, setActiveTab] = useState<Tab>("snapshots");

    // Search state
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState<ContextSnapshot[]>([]);
    const [searching, setSearching] = useState(false);
    const [hasSearched, setHasSearched] = useState(false);

    // Setup state
    const [copied, setCopied] = useState(false);

    const fetchSnapshots = useCallback(async () => {
        try {
            const url = projectFilter
                ? `${API}/api/snapshots?project=${encodeURIComponent(projectFilter)}`
                : `${API}/api/snapshots`;
            const res = await fetch(url);
            if (!res.ok) throw new Error(`Backend returned ${res.status}`);
            const data: ContextSnapshot[] = await res.json();
            setSnapshots(data);
            setError(null);
            setLastRefresh(new Date());
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : String(err);
            setError(message);
        } finally {
            setLoading(false);
        }
    }, [projectFilter]);

    const fetchStats = useCallback(async () => {
        try {
            const res = await fetch(`${API}/api/stats`);
            if (!res.ok) return;
            const data: StatsResponse = await res.json();
            setStats(data.projects);
        } catch {
            // Stats are non-critical
        }
    }, []);

    const handleSearch = async () => {
        if (!searchQuery.trim()) return;
        setSearching(true);
        setHasSearched(true);
        try {
            const res = await fetch(
                `${API}/api/snapshots/recall?q=${encodeURIComponent(searchQuery)}&limit=10`
            );
            if (!res.ok) throw new Error("Search failed");
            const data: RecallResponse = await res.json();
            setSearchResults(data.snapshots);
        } catch {
            setSearchResults([]);
        } finally {
            setSearching(false);
        }
    };

    const copyMcpConfig = () => {
        const config = JSON.stringify({
            servers: {
                "context-bridge": {
                    type: "sse",
                    url: `${API}/mcp/sse`
                }
            }
        }, null, 2);
        navigator.clipboard.writeText(config);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    useEffect(() => {
        fetchSnapshots();
        fetchStats();
        const id = setInterval(() => {
            fetchSnapshots();
            fetchStats();
        }, POLL_INTERVAL_MS);
        return () => clearInterval(id);
    }, [fetchSnapshots, fetchStats]);

    const uniqueProjects = Array.from(new Set(snapshots.map((s) => s.project_name)));

    const totalCompleted = stats.reduce((sum, p) => sum + p.completed, 0);
    const totalBlocked = stats.reduce((sum, p) => sum + p.blocked, 0);

    return (
        <div className="min-h-screen bg-[#06060b] text-white">
            {/* ── Header ──────────────────────────────────────────────────── */}
            <header className="sticky top-0 z-50 border-b border-white/[0.06] bg-[#06060b]/80 backdrop-blur-xl">
                <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-xl bg-violet-600/15 border border-violet-500/20 glow-violet">
                            <Brain className="h-6 w-6 text-violet-400" />
                        </div>
                        <div>
                            <h1 className="text-xl font-bold tracking-tight gradient-text">
                                ContextBridge
                            </h1>
                            <p className="text-[10px] text-white/30 tracking-wide uppercase">
                                AI Memory Plane
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center gap-3">
                        <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full ${
                            error ? "bg-rose-500/10 border border-rose-500/20" : "bg-emerald-500/10 border border-emerald-500/20"
                        }`}>
                            <Activity className={`h-3 w-3 ${error ? "text-rose-400" : "text-emerald-400 status-pulse"}`} />
                            <span className={`text-xs font-medium ${error ? "text-rose-400" : "text-emerald-400"}`}>
                                {error ? "Offline" : "Live"}
                            </span>
                        </div>
                        <button
                            onClick={() => { fetchSnapshots(); fetchStats(); }}
                            className="p-2 rounded-lg text-white/40 hover:text-white hover:bg-white/5 transition-all"
                        >
                            <RefreshCw className="h-4 w-4" />
                        </button>
                    </div>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-6 py-8 space-y-8">
                {/* ── Stats Row ────────────────────────────────────────────── */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    <StatCard label="Total Snapshots" value={snapshots.length} icon={Boxes} gradient="from-violet-500/20 to-violet-500/0" />
                    <StatCard label="Projects" value={stats.length || uniqueProjects.length} icon={FolderGit2} gradient="from-cyan-500/20 to-cyan-500/0" />
                    <StatCard label="Completed" value={totalCompleted} icon={CheckCircle2} gradient="from-emerald-500/20 to-emerald-500/0" />
                    <StatCard label="Blocked" value={totalBlocked} icon={AlertCircle} gradient="from-rose-500/20 to-rose-500/0" />
                </div>

                {/* ── Tabs ─────────────────────────────────────────────────── */}
                <div className="flex items-center gap-2">
                    <TabButton active={activeTab === "snapshots"} onClick={() => setActiveTab("snapshots")} icon={Boxes} label="Snapshots" />
                    <TabButton active={activeTab === "search"} onClick={() => setActiveTab("search")} icon={Search} label="Recall" />
                    <TabButton active={activeTab === "setup"} onClick={() => setActiveTab("setup")} icon={Terminal} label="Setup" />
                </div>

                {/* ── Tab: Snapshots ───────────────────────────────────────── */}
                {activeTab === "snapshots" && (
                    <div className="space-y-6 fade-in-up">
                        {/* Project Filter */}
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-xs text-white/25 uppercase tracking-widest font-medium">Filter</span>
                            <FilterPill active={projectFilter === ""} onClick={() => setProjectFilter("")} label="All" />
                            {uniqueProjects.map((proj) => (
                                <FilterPill key={proj} active={projectFilter === proj} onClick={() => setProjectFilter(proj)} label={proj} />
                            ))}
                        </div>

                        {/* Project Stats Cards */}
                        {stats.length > 0 && !projectFilter && (
                            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                                {stats.map((proj) => (
                                    <ProjectStatCard key={proj.project_name} project={proj} onClick={() => {
                                        setProjectFilter(proj.project_name);
                                    }} />
                                ))}
                            </div>
                        )}

                        {/* Snapshot Cards */}
                        {loading ? (
                            <LoadingState />
                        ) : error ? (
                            <ErrorState error={error} api={API} />
                        ) : snapshots.length === 0 ? (
                            <EmptyState onSetup={() => setActiveTab("setup")} />
                        ) : (
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                                {snapshots.map((snap, i) => (
                                    <SnapshotCard key={`${snap.session_id}-${i}`} snapshot={snap} />
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* ── Tab: Search ──────────────────────────────────────────── */}
                {activeTab === "search" && (
                    <div className="space-y-6 fade-in-up">
                        <div className="text-center space-y-2 py-4">
                            <div className="inline-flex p-3 rounded-2xl bg-violet-500/10 border border-violet-500/15 mb-3">
                                <Sparkles className="h-6 w-6 text-violet-400" />
                            </div>
                            <h2 className="text-lg font-semibold text-white/90">Recall Your AI Memory</h2>
                            <p className="text-sm text-white/40 max-w-md mx-auto">
                                Search across all your sessions using natural language.
                                Find how you implemented something, decisions you made, or context from any project.
                            </p>
                        </div>

                        <div className="flex gap-3 max-w-2xl mx-auto">
                            <div className="flex-1 relative">
                                <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-white/20" />
                                <input
                                    type="text"
                                    placeholder="e.g. &quot;How did I implement session management?&quot;"
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                                    className="w-full search-input rounded-xl py-3 pl-11 pr-4 text-sm text-white/80 placeholder:text-white/20"
                                />
                            </div>
                            <button
                                onClick={handleSearch}
                                disabled={searching || !searchQuery.trim()}
                                className="px-5 py-3 rounded-xl bg-violet-600 hover:bg-violet-500 disabled:opacity-40 disabled:cursor-not-allowed text-sm font-medium transition-all flex items-center gap-2"
                            >
                                {searching ? (
                                    <div className="h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                                ) : (
                                    <Search className="h-4 w-4" />
                                )}
                                Search
                            </button>
                        </div>

                        {/* Search Results */}
                        {hasSearched && !searching && (
                            <div className="space-y-4 max-w-4xl mx-auto">
                                <p className="text-xs text-white/30">
                                    {searchResults.length} result{searchResults.length !== 1 ? "s" : ""} for &quot;{searchQuery}&quot;
                                </p>
                                {searchResults.length === 0 ? (
                                    <div className="glass rounded-xl p-8 text-center space-y-2">
                                        <p className="text-white/40 text-sm">No matching context found</p>
                                        <p className="text-white/20 text-xs">
                                            Only checkpointed sessions are searchable — make sure to use{" "}
                                            <code className="text-violet-400">checkpoint_state</code> during sessions.
                                        </p>
                                    </div>
                                ) : (
                                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                                        {searchResults.map((snap, i) => (
                                            <SnapshotCard key={`search-${i}`} snapshot={snap} />
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* ── Tab: Setup ───────────────────────────────────────────── */}
                {activeTab === "setup" && (
                    <div className="space-y-8 fade-in-up max-w-3xl mx-auto">
                        <div className="text-center space-y-2 py-4">
                            <div className="inline-flex p-3 rounded-2xl bg-cyan-500/10 border border-cyan-500/15 mb-3">
                                <Zap className="h-6 w-6 text-cyan-400" />
                            </div>
                            <h2 className="text-lg font-semibold text-white/90">Connect Your IDE</h2>
                            <p className="text-sm text-white/40 max-w-md mx-auto">
                                Add ContextBridge to your AI coding assistant in 2 minutes.
                            </p>
                        </div>

                        {/* Steps */}
                        <div className="space-y-4">
                            <SetupStep
                                step={1}
                                title="Create MCP config"
                                description={`Create a .vscode/mcp.json file in your project root`}
                            >
                                <div className="relative">
                                    <pre className="rounded-lg bg-black/40 border border-white/5 p-4 text-xs font-mono text-cyan-400/80 overflow-x-auto">
{`{
  "servers": {
    "context-bridge": {
      "type": "sse",
      "url": "${API}/mcp/sse"
    }
  }
}`}
                                    </pre>
                                    <button
                                        onClick={copyMcpConfig}
                                        className="absolute top-3 right-3 p-1.5 rounded-md bg-white/5 hover:bg-white/10 transition-all"
                                    >
                                        {copied ? (
                                            <Check className="h-3.5 w-3.5 text-emerald-400" />
                                        ) : (
                                            <Copy className="h-3.5 w-3.5 text-white/30" />
                                        )}
                                    </button>
                                </div>
                            </SetupStep>

                            <SetupStep
                                step={2}
                                title="Start a chat with your AI"
                                description="Your AI coding assistant now sees 3 new tools"
                            >
                                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                    <ToolCard
                                        name="checkpoint_state"
                                        description="Save context"
                                        color="text-violet-400"
                                        bg="bg-violet-500/5"
                                    />
                                    <ToolCard
                                        name="restore_state"
                                        description="Resume work"
                                        color="text-cyan-400"
                                        bg="bg-cyan-500/5"
                                    />
                                    <ToolCard
                                        name="recall"
                                        description="Search memory"
                                        color="text-emerald-400"
                                        bg="bg-emerald-500/5"
                                    />
                                </div>
                            </SetupStep>

                            <SetupStep
                                step={3}
                                title="That's it!"
                                description="Your AI now has persistent memory. Context is auto-saved and searchable."
                            >
                                <button
                                    onClick={() => setActiveTab("snapshots")}
                                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600/20 border border-violet-500/20 text-violet-400 text-sm hover:bg-violet-600/30 transition-all"
                                >
                                    Go to Dashboard <ArrowRight className="h-4 w-4" />
                                </button>
                            </SetupStep>
                        </div>
                    </div>
                )}

                {/* ── Footer ──────────────────────────────────────────────── */}
                {lastRefresh && (
                    <p className="text-center text-[10px] text-white/15 pb-4 font-mono">
                        Auto-refreshes · Last synced {lastRefresh.toLocaleTimeString()}
                    </p>
                )}
            </main>
        </div>
    );
}

/* ── Sub-components ──────────────────────────────────────────────────── */

function StatCard({ label, value, icon: Icon, gradient }: {
    label: string; value: number | string;
    icon: React.ComponentType<{ className?: string }>;
    gradient: string;
}) {
    return (
        <div className="glass rounded-xl p-4 space-y-1 relative overflow-hidden">
            <div className={`absolute inset-0 bg-gradient-to-br ${gradient} pointer-events-none`} />
            <div className="relative flex items-center justify-between">
                <div>
                    <p className="text-[10px] text-white/30 uppercase tracking-wider">{label}</p>
                    <p className="text-2xl font-bold tracking-tight">{value}</p>
                </div>
                <Icon className="h-5 w-5 text-white/10" />
            </div>
        </div>
    );
}

function TabButton({ active, onClick, icon: Icon, label }: {
    active: boolean; onClick: () => void;
    icon: React.ComponentType<{ className?: string }>; label: string;
}) {
    return (
        <button
            onClick={onClick}
            className={`px-4 py-2 rounded-lg border text-xs font-medium transition-all flex items-center gap-2 ${
                active ? "tab-active" : "tab-inactive"
            }`}
        >
            <Icon className="h-3.5 w-3.5" /> {label}
        </button>
    );
}

function FilterPill({ active, onClick, label }: {
    active: boolean; onClick: () => void; label: string;
}) {
    return (
        <button
            onClick={onClick}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-all border ${
                active
                    ? "bg-violet-600/20 border-violet-500/30 text-violet-300"
                    : "bg-white/[0.02] border-white/[0.06] text-white/40 hover:text-white/60 hover:bg-white/[0.04]"
            }`}
        >
            {label}
        </button>
    );
}

function ProjectStatCard({ project, onClick }: { project: ProjectStat; onClick: () => void }) {
    const status = project.latest_status ?? "unknown";
    const style = {
        completed: { dot: "bg-emerald-400", text: "text-emerald-400" },
        in_progress: { dot: "bg-violet-400", text: "text-violet-400" },
        blocked: { dot: "bg-rose-400", text: "text-rose-400" },
    }[status] ?? { dot: "bg-white/30", text: "text-white/40" };

    return (
        <button onClick={onClick} className="glass glass-hover rounded-xl p-4 text-left space-y-3 w-full transition-all">
            <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-white/80">{project.project_name}</span>
                <span className={`flex items-center gap-1.5 text-[10px] ${style.text}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />
                    {status.replace(/_/g, " ")}
                </span>
            </div>
            {project.latest_goal && (
                <p className="text-xs text-white/40 line-clamp-1">{project.latest_goal}</p>
            )}
            <div className="flex items-center gap-4 text-[10px] text-white/25">
                <span className="flex items-center gap-1"><BarChart3 className="h-3 w-3" />{project.total_sessions} sessions</span>
                <span className="flex items-center gap-1"><CheckCircle2 className="h-3 w-3" />{project.completed} done</span>
                <span className="flex items-center gap-1"><Clock className="h-3 w-3" />{formatTimeAgoShort(project.last_active)}</span>
            </div>
            {project.tech_stack?.length > 0 && (
                <div className="flex gap-1 flex-wrap">
                    {project.tech_stack.slice(0, 4).map((t) => (
                        <span key={t} className="px-1.5 py-0.5 rounded bg-cyan-500/5 border border-cyan-500/8 text-cyan-400/40 text-[9px] font-mono">{t}</span>
                    ))}
                </div>
            )}
        </button>
    );
}

function SetupStep({ step, title, description, children }: {
    step: number; title: string; description: string; children: React.ReactNode;
}) {
    return (
        <div className="glass rounded-xl p-5 space-y-3">
            <div className="flex items-center gap-3">
                <span className="w-7 h-7 rounded-full bg-violet-500/15 border border-violet-500/20 flex items-center justify-center text-xs font-bold text-violet-400">
                    {step}
                </span>
                <div>
                    <h3 className="text-sm font-semibold text-white/85">{title}</h3>
                    <p className="text-xs text-white/30">{description}</p>
                </div>
            </div>
            <div className="pl-10">{children}</div>
        </div>
    );
}

function ToolCard({ name, description, color, bg }: {
    name: string; description: string; color: string; bg: string;
}) {
    return (
        <div className={`rounded-lg ${bg} border border-white/5 p-3 text-center space-y-1`}>
            <p className={`text-xs font-mono ${color}`}>{name}</p>
            <p className="text-[10px] text-white/30">{description}</p>
        </div>
    );
}

function LoadingState() {
    return (
        <div className="flex flex-col items-center justify-center py-32 gap-4 text-white/30">
            <div className="h-8 w-8 rounded-full border-2 border-violet-500/30 border-t-violet-400 animate-spin" />
            <p className="text-sm">Connecting to backend…</p>
        </div>
    );
}

function ErrorState({ error, api }: { error: string; api: string }) {
    return (
        <div className="glass rounded-xl p-8 text-center space-y-2 border-rose-500/20">
            <p className="text-rose-400 font-medium text-sm">Backend unreachable</p>
            <p className="text-xs text-white/30">
                Make sure the Spring Boot server is running on{" "}
                <code className="text-cyan-400">{api}</code>
            </p>
            <p className="text-[10px] text-white/15 font-mono">{error}</p>
        </div>
    );
}

function EmptyState({ onSetup }: { onSetup: () => void }) {
    return (
        <div className="glass rounded-xl p-16 text-center space-y-4">
            <Brain className="h-12 w-12 mx-auto text-white/10" />
            <div className="space-y-1">
                <p className="text-white/40 font-medium text-sm">No snapshots yet</p>
                <p className="text-xs text-white/20 max-w-sm mx-auto">
                    Connect your IDE and use <code className="text-violet-400">checkpoint_state</code> to save your first context.
                </p>
            </div>
            <button onClick={onSetup} className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600/20 border border-violet-500/20 text-violet-400 text-sm hover:bg-violet-600/30 transition-all">
                Setup Guide <ArrowRight className="h-4 w-4" />
            </button>
        </div>
    );
}

function formatTimeAgoShort(timestamp: string): string {
    const diffMs = Date.now() - new Date(timestamp).getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 60) return `${diffMin}m`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h`;
    return `${Math.floor(diffHr / 24)}d`;
}
