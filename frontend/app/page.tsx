"use client";

import { useEffect, useState } from "react";
import { ContextSnapshot } from "@/types/snapshot";
import SnapshotCard from "@/components/SnapshotCard";
import { Brain, RefreshCw, Activity, Boxes } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const POLL_INTERVAL_MS = 5_000;

export default function DashboardPage() {
    const [snapshots, setSnapshots] = useState<ContextSnapshot[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [lastRefresh, setLastRefresh] = useState<Date | null>(null);
    const [projectFilter, setProjectFilter] = useState("");

    const fetchSnapshots = async () => {
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
    };

    // Initial load + polling
    useEffect(() => {
        fetchSnapshots();
        const id = setInterval(fetchSnapshots, POLL_INTERVAL_MS);
        return () => clearInterval(id);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projectFilter]);

    const uniqueProjects = Array.from(
        new Set(snapshots.map((s) => s.project_name))
    );

    return (
        <div className="min-h-screen bg-[#0a0a0f] text-white">
            {/* ── Header ──────────────────────────────────────────────────────── */}
            <header className="sticky top-0 z-50 border-b border-white/10 bg-[#0a0a0f]/80 backdrop-blur-xl">
                <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-xl bg-violet-600/20 border border-violet-500/30">
                            <Brain className="h-6 w-6 text-violet-400" />
                        </div>
                        <div>
                            <h1 className="text-xl font-bold tracking-tight bg-gradient-to-r from-violet-400 to-cyan-400 bg-clip-text text-transparent">
                                ContextBridge
                            </h1>
                            <p className="text-xs text-white/40">Distributed AI Memory Plane</p>
                        </div>
                    </div>

                    <div className="flex items-center gap-3">
                        {/* Live indicator */}
                        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
                            <Activity className="h-3 w-3 text-emerald-400 animate-pulse" />
                            <span className="text-xs text-emerald-400 font-medium">Live</span>
                        </div>

                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={fetchSnapshots}
                            className="h-8 gap-1.5 text-white/60 hover:text-white hover:bg-white/5"
                        >
                            <RefreshCw className="h-3.5 w-3.5" />
                            Refresh
                        </Button>
                    </div>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-6 py-8 space-y-8">
                {/* ── Stats Row ───────────────────────────────────────────────── */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {[
                        { label: "Total Snapshots", value: snapshots.length, icon: Boxes, color: "violet" },
                        { label: "Projects Tracked", value: uniqueProjects.length, icon: Brain, color: "cyan" },
                        {
                            label: "Last Sync",
                            value: lastRefresh ? lastRefresh.toLocaleTimeString() : "—",
                            icon: Activity,
                            color: "emerald",
                        },
                        {
                            label: "Backend",
                            value: error ? "Offline" : "Online",
                            icon: RefreshCw,
                            color: error ? "red" : "emerald",
                        },
                    ].map((stat) => (
                        <div
                            key={stat.label}
                            className="rounded-xl border border-white/10 bg-white/[0.03] p-4 space-y-1"
                        >
                            <p className="text-xs text-white/40">{stat.label}</p>
                            <p className="text-2xl font-bold tracking-tight">{stat.value}</p>
                        </div>
                    ))}
                </div>

                {/* ── Project Filter ───────────────────────────────────────────── */}
                <div className="flex items-center gap-3 flex-wrap">
                    <span className="text-sm text-white/40">Filter:</span>
                    <Badge
                        id="filter-all"
                        onClick={() => setProjectFilter("")}
                        className={`cursor-pointer transition-all ${projectFilter === ""
                                ? "bg-violet-600 text-white hover:bg-violet-700"
                                : "bg-white/5 text-white/60 border-white/10 hover:bg-white/10"
                            }`}
                    >
                        All
                    </Badge>
                    {uniqueProjects.map((proj) => (
                        <Badge
                            key={proj}
                            id={`filter-${proj}`}
                            onClick={() => setProjectFilter(proj)}
                            className={`cursor-pointer transition-all ${projectFilter === proj
                                    ? "bg-violet-600 text-white hover:bg-violet-700"
                                    : "bg-white/5 text-white/60 border-white/10 hover:bg-white/10"
                                }`}
                        >
                            {proj}
                        </Badge>
                    ))}
                </div>

                {/* ── Content ─────────────────────────────────────────────────── */}
                {loading ? (
                    <div className="flex flex-col items-center justify-center py-32 gap-4 text-white/30">
                        <div className="h-8 w-8 rounded-full border-2 border-violet-500/50 border-t-violet-400 animate-spin" />
                        <p className="text-sm">Connecting to backend…</p>
                    </div>
                ) : error ? (
                    <div className="rounded-xl border border-red-500/20 bg-red-500/5 p-8 text-center space-y-2">
                        <p className="text-red-400 font-medium">Backend unreachable</p>
                        <p className="text-sm text-white/40">
                            Make sure the Spring Boot server is running on{" "}
                            <code className="text-cyan-400">{API}</code>
                        </p>
                        <p className="text-xs text-white/20 font-mono">{error}</p>
                    </div>
                ) : snapshots.length === 0 ? (
                    <div className="rounded-xl border border-white/10 bg-white/[0.02] p-16 text-center space-y-3">
                        <Brain className="h-12 w-12 mx-auto text-white/20" />
                        <p className="text-white/40 font-medium">No snapshots yet</p>
                        <p className="text-sm text-white/20">
                            Use the <code className="text-violet-400">checkpoint_state</code> MCP tool
                            from your IDE to save your first context snapshot.
                        </p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
                        {snapshots.map((snap, i) => (
                            <SnapshotCard key={`${snap.session_id}-${i}`} snapshot={snap} />
                        ))}
                    </div>
                )}

                {/* ── Footer ──────────────────────────────────────────────────── */}
                {lastRefresh && (
                    <p className="text-center text-xs text-white/20 pb-4">
                        Auto-refreshes every {POLL_INTERVAL_MS / 1000}s · Last synced{" "}
                        {lastRefresh.toLocaleTimeString()}
                    </p>
                )}
            </main>
        </div>
    );
}
