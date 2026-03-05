"use client";

import { ContextSnapshot } from "@/types/snapshot";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { FileCode2, Lightbulb, AlertTriangle, Clock } from "lucide-react";

interface Props {
    snapshot: ContextSnapshot;
}

export default function SnapshotCard({ snapshot }: Props) {
    const date = new Date(snapshot.timestamp);
    const timeAgo = formatTimeAgo(date);

    return (
        <Card
            id={`snapshot-${snapshot.session_id}`}
            className="group relative overflow-hidden border-white/10 bg-white/[0.03] hover:bg-white/[0.06] hover:border-violet-500/30 transition-all duration-300 hover:shadow-lg hover:shadow-violet-500/5"
        >
            {/* Gradient glow on hover */}
            <div className="absolute inset-0 bg-gradient-to-br from-violet-600/0 to-cyan-600/0 group-hover:from-violet-600/5 group-hover:to-cyan-600/5 transition-all duration-500 pointer-events-none" />

            <CardHeader className="pb-3 space-y-1">
                <div className="flex items-start justify-between gap-2">
                    <Badge
                        variant="outline"
                        className="border-violet-500/30 text-violet-400 bg-violet-500/10 text-xs font-mono"
                    >
                        {snapshot.project_name}
                    </Badge>
                    <div className="flex items-center gap-1 text-white/30 text-xs shrink-0">
                        <Clock className="h-3 w-3" />
                        <span>{timeAgo}</span>
                    </div>
                </div>

                <CardTitle className="text-sm font-semibold text-white/90 leading-snug">
                    {snapshot.current_goal || "No goal specified"}
                </CardTitle>
                <CardDescription className="text-xs text-white/30 font-mono truncate">
                    session: {snapshot.session_id}
                </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4 text-xs">
                {/* ── Active Files ─────────────────────────────────────── */}
                {snapshot.active_files?.length > 0 && (
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1.5 text-white/40">
                            <FileCode2 className="h-3 w-3" />
                            <span className="font-medium uppercase tracking-wider text-[10px]">
                                Active Files
                            </span>
                        </div>
                        <div className="flex flex-wrap gap-1">
                            {snapshot.active_files.map((f) => (
                                <span
                                    key={f}
                                    className="px-2 py-0.5 rounded-md bg-white/5 text-cyan-400/80 font-mono text-[10px] border border-white/5"
                                >
                                    {f.split("/").pop()}
                                </span>
                            ))}
                        </div>
                    </div>
                )}

                {/* ── Architectural Decisions ───────────────────────────── */}
                {snapshot.architectural_decisions && (
                    <>
                        <Separator className="bg-white/5" />
                        <div className="space-y-1.5">
                            <div className="flex items-center gap-1.5 text-white/40">
                                <Lightbulb className="h-3 w-3" />
                                <span className="font-medium uppercase tracking-wider text-[10px]">
                                    Decisions
                                </span>
                            </div>
                            <p className="text-white/60 leading-relaxed line-clamp-3">
                                {snapshot.architectural_decisions}
                            </p>
                        </div>
                    </>
                )}

                {/* ── Unresolved Issues ─────────────────────────────────── */}
                {snapshot.unresolved_issues && (
                    <>
                        <Separator className="bg-white/5" />
                        <div className="space-y-1.5">
                            <div className="flex items-center gap-1.5 text-amber-400/60">
                                <AlertTriangle className="h-3 w-3" />
                                <span className="font-medium uppercase tracking-wider text-[10px] text-white/40">
                                    Open Issues
                                </span>
                            </div>
                            <p className="text-amber-400/70 leading-relaxed line-clamp-3">
                                {snapshot.unresolved_issues}
                            </p>
                        </div>
                    </>
                )}

                {/* ── Timestamp ─────────────────────────────────────────── */}
                <div className="pt-1 text-white/20 font-mono text-[10px]">
                    {date.toLocaleString()}
                </div>
            </CardContent>
        </Card>
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
