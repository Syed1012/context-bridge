"use client";

import { ContextSnapshot } from "@/types/snapshot";
import {
    FileCode2,
    Lightbulb,
    GitCommitHorizontal,
    ListChecks,
    Clock,
    ChevronDown,
    ChevronUp,
    MessageSquareText,
    Cpu,
} from "lucide-react";
import { useState } from "react";

interface Props {
    snapshot: ContextSnapshot;
}

const STATUS_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
    completed: { bg: "bg-emerald-500/10", text: "text-emerald-400", dot: "bg-emerald-400" },
    in_progress: { bg: "bg-violet-500/10", text: "text-violet-400", dot: "bg-violet-400" },
    blocked: { bg: "bg-rose-500/10", text: "text-rose-400", dot: "bg-rose-400" },
    paused: { bg: "bg-amber-500/10", text: "text-amber-400", dot: "bg-amber-400" },
};

export default function SnapshotCard({ snapshot }: Props) {
    const [expanded, setExpanded] = useState(false);
    const date = new Date(snapshot.timestamp);
    const status = snapshot.progress_status ?? "in_progress";
    const style = STATUS_STYLES[status] ?? STATUS_STYLES.in_progress;

    return (
        <div
            className="glass glass-hover rounded-2xl p-5 space-y-4 transition-all duration-300 cursor-pointer fade-in-up"
            onClick={() => setExpanded(!expanded)}
        >
            {/* Header */}
            <div className="flex items-start justify-between gap-3">
                <div className="space-y-1.5 flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="px-2.5 py-0.5 rounded-lg bg-violet-500/10 border border-violet-500/20 text-violet-400 text-xs font-mono">
                            {snapshot.project_name}
                        </span>
                        <span className={`px-2 py-0.5 rounded-full ${style.bg} ${style.text} text-[10px] font-medium flex items-center gap-1.5`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${style.dot} status-pulse`} />
                            {status.replace(/_/g, " ")}
                        </span>
                    </div>
                    <h3 className="text-sm font-semibold text-white/90 leading-snug">
                        {snapshot.current_goal || "No goal specified"}
                    </h3>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                    <span className="text-[10px] text-white/30 font-mono flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        {formatTimeAgo(date)}
                    </span>
                    {expanded ? (
                        <ChevronUp className="h-4 w-4 text-white/20" />
                    ) : (
                        <ChevronDown className="h-4 w-4 text-white/20" />
                    )}
                </div>
            </div>

            {/* Summary (always visible) */}
            {snapshot.conversation_summary && (
                <div className="flex gap-2 text-xs">
                    <MessageSquareText className="h-3.5 w-3.5 text-cyan-400/50 shrink-0 mt-0.5" />
                    <p className={`text-white/50 leading-relaxed ${expanded ? "" : "line-clamp-2"}`}>
                        {snapshot.conversation_summary}
                    </p>
                </div>
            )}

            {/* Tech Stack Pills */}
            {snapshot.tech_stack?.length > 0 && (
                <div className="flex items-center gap-1.5 flex-wrap">
                    <Cpu className="h-3 w-3 text-white/20" />
                    {snapshot.tech_stack.map((tech) => (
                        <span key={tech} className="px-2 py-0.5 rounded-md bg-cyan-500/5 border border-cyan-500/10 text-cyan-400/60 text-[10px] font-mono">
                            {tech}
                        </span>
                    ))}
                </div>
            )}

            {/* Expanded Details */}
            {expanded && (
                <div className="space-y-4 pt-2 border-t border-white/5">
                    {/* Code Changes */}
                    {snapshot.code_changes?.length > 0 && (
                        <Section icon={GitCommitHorizontal} label="Code Changes" color="text-emerald-400/50">
                            <div className="space-y-2">
                                {snapshot.code_changes.map((change, i) => (
                                    <div key={i} className="rounded-lg bg-white/[0.02] border border-white/5 p-3 space-y-1">
                                        <div className="flex items-center gap-2">
                                            <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${
                                                change.action === "created" ? "bg-emerald-500/10 text-emerald-400" :
                                                change.action === "deleted" ? "bg-rose-500/10 text-rose-400" :
                                                "bg-amber-500/10 text-amber-400"
                                            }`}>
                                                {change.action}
                                            </span>
                                            <span className="text-xs text-cyan-400/70 font-mono truncate">
                                                {change.file}
                                            </span>
                                        </div>
                                        <p className="text-[11px] text-white/50">{change.summary}</p>
                                        {change.details && (
                                            <p className="text-[10px] text-white/30 leading-relaxed">{change.details}</p>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </Section>
                    )}

                    {/* Key Decisions */}
                    {snapshot.key_decisions_log?.length > 0 && (
                        <Section icon={Lightbulb} label="Key Decisions" color="text-amber-400/50">
                            <div className="space-y-2">
                                {snapshot.key_decisions_log.map((d, i) => (
                                    <div key={i} className="rounded-lg bg-white/[0.02] border border-white/5 p-3 space-y-1">
                                        <p className="text-xs text-white/70 font-medium">{d.decision}</p>
                                        {d.rationale && (
                                            <p className="text-[11px] text-white/40">
                                                <span className="text-white/20">Why:</span> {d.rationale}
                                            </p>
                                        )}
                                        {d.alternatives_considered && (
                                            <p className="text-[10px] text-white/25">
                                                Alternatives: {d.alternatives_considered}
                                            </p>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </Section>
                    )}

                    {/* Active Files */}
                    {snapshot.active_files?.length > 0 && (
                        <Section icon={FileCode2} label="Active Files" color="text-cyan-400/50">
                            <div className="flex flex-wrap gap-1">
                                {snapshot.active_files.map((f) => (
                                    <span key={f} className="px-2 py-0.5 rounded-md bg-white/[0.03] text-cyan-400/60 font-mono text-[10px] border border-white/5">
                                        {f.split("/").pop()}
                                    </span>
                                ))}
                            </div>
                        </Section>
                    )}

                    {/* Next Steps */}
                    {snapshot.next_steps?.length > 0 && (
                        <Section icon={ListChecks} label="Next Steps" color="text-violet-400/50">
                            <ol className="space-y-1">
                                {snapshot.next_steps.map((step, i) => (
                                    <li key={i} className="text-[11px] text-white/50 flex gap-2">
                                        <span className="text-violet-400/40 font-mono shrink-0">{i + 1}.</span>
                                        {step}
                                    </li>
                                ))}
                            </ol>
                        </Section>
                    )}

                    {/* Timestamp / Session */}
                    <div className="flex items-center justify-between text-[10px] text-white/15 font-mono pt-1">
                        <span>{date.toLocaleString()}</span>
                        <span>session: {snapshot.session_id?.slice(0, 12)}…</span>
                    </div>
                </div>
            )}
        </div>
    );
}

function Section({ icon: Icon, label, color, children }: {
    icon: React.ComponentType<{ className?: string }>;
    label: string;
    color: string;
    children: React.ReactNode;
}) {
    return (
        <div className="space-y-2">
            <div className="flex items-center gap-1.5">
                <Icon className={`h-3 w-3 ${color}`} />
                <span className="font-medium uppercase tracking-wider text-[10px] text-white/30">
                    {label}
                </span>
            </div>
            {children}
        </div>
    );
}

function formatTimeAgo(date: Date): string {
    const diffMs = Date.now() - date.getTime();
    const diffSec = Math.floor(diffMs / 1000);
    if (diffSec < 60) return `${diffSec}s ago`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h ago`;
    return `${Math.floor(diffHr / 24)}d ago`;
}
