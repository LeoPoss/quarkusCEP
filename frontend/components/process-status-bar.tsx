"use client";

import { Card, Spinner } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

interface FinishabilityResponse {
    canFinish: boolean;
    reasons: string[];
}

interface Constraint {
    name: string;
    status: string;
}

const fetchFinishability = async (): Promise<FinishabilityResponse> => {
    return await api.get("analysis/finishability").json();
};

const fetchConstraints = async (): Promise<Constraint[]> => {
    return await api.get("constraints").json();
};

export default function ProcessStatusBar() {
    const { data: finishability, isLoading: loadingFinish } = useQuery<FinishabilityResponse>({
        queryKey: ["analysis", "finishability"],
        queryFn: fetchFinishability,
        refetchInterval: 1000,
    });

    const { data: constraints = [], isLoading: loadingConstraints } = useQuery<Constraint[]>({
        queryKey: ["constraints"],
        queryFn: fetchConstraints,
        refetchInterval: 1000,
    });

    const isLoading = loadingFinish || loadingConstraints;

    if (isLoading) {
        return (
            <Card className="h-full">
                <Card.Content className="flex justify-center py-4">
                    <Spinner size="sm" color="accent" />
                </Card.Content>
            </Card>
        );
    }

    const fulfilledCount = constraints.filter((c) => c.status === "FULFILLED").length;
    const violatedCount = constraints.filter((c) => c.status === "PERMANENT_VIOLATION").length;
    const pendingCount = constraints.filter((c) => c.status === "TEMPORARY_VIOLATION" || c.status === "INIT").length;
    const total = fulfilledCount + pendingCount + violatedCount;

    return (
        <Card className="h-full">
            <Card.Header>
                <Card.Title>Process Status</Card.Title>
            </Card.Header>
            <Card.Content className="space-y-4">
                {/* Mini donut + finishability in a row */}
                <div className="flex items-center gap-4">
                    {/* Donut chart */}
                    <div className="relative shrink-0">
                        <svg viewBox="0 0 36 36" className="w-14 h-14 -rotate-90">
                            {total > 0 && (
                                <>
                                    <circle cx="18" cy="18" r="15.9" fill="none"
                                        stroke="currentColor" strokeWidth="3"
                                        className="text-default-200 dark:text-default-800" />
                                    <circle cx="18" cy="18" r="15.9" fill="none"
                                        stroke="#22c55e" strokeWidth="3"
                                        strokeDasharray={`${(fulfilledCount / total) * 100} 100`}
                                        strokeLinecap="butt"
                                        strokeDashoffset="0" />
                                    <circle cx="18" cy="18" r="15.9" fill="none"
                                        stroke="#f59e0b" strokeWidth="3"
                                        strokeDasharray={`${(pendingCount / total) * 100} 100`}
                                        strokeLinecap="butt"
                                        strokeDashoffset={`${-(fulfilledCount / total) * 100}`} />
                                    <circle cx="18" cy="18" r="15.9" fill="none"
                                        stroke="#ef4444" strokeWidth="3"
                                        strokeDasharray={`${(violatedCount / total) * 100} 100`}
                                        strokeLinecap="butt"
                                        strokeDashoffset={`${-((fulfilledCount + pendingCount) / total) * 100}`} />
                                </>
                            )}
                            {total === 0 && (
                                <circle cx="18" cy="18" r="15.9" fill="none"
                                    stroke="currentColor" strokeWidth="3"
                                    className="text-default-200 dark:text-default-800" />
                            )}
                        </svg>
                        <div className="absolute inset-0 flex items-center justify-center">
                            <span className="text-xs font-mono font-semibold">{total}</span>
                        </div>
                    </div>

                    {/* Finishability status */}
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                            <span className="text-xs text-default-600 dark:text-default-400">Finishable</span>
                            <span className={`text-xs font-mono font-medium ${finishability?.canFinish ? "text-success" : "text-warning"}`}>
                                {finishability?.canFinish ? "YES" : "NO"}
                            </span>
                        </div>
                        {!finishability?.canFinish && finishability?.reasons && finishability.reasons.length > 0 && (
                            <div className="mt-1 pl-2 border-l-2 border-warning/20 space-y-1">
                                {finishability.reasons.slice(0, 3).map((reason, idx) => (
                                    <div key={idx} className="text-[10px] text-default-500 leading-tight">
                                        {reason}
                                    </div>
                                ))}
                                {finishability.reasons.length > 3 && (
                                    <div className="text-[10px] text-default-400 italic">
                                        + {finishability.reasons.length - 3} more...
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>

                {/* Stacked bar */}
                {total > 0 && (
                    <div className="space-y-1.5">
                        <div className="flex h-2 rounded-full overflow-hidden bg-default-100 dark:bg-default-800">
                            {fulfilledCount > 0 && (
                                <div className="bg-success transition-all" style={{ width: `${(fulfilledCount / total) * 100}%` }} />
                            )}
                            {pendingCount > 0 && (
                                <div className="bg-warning transition-all" style={{ width: `${(pendingCount / total) * 100}%` }} />
                            )}
                            {violatedCount > 0 && (
                                <div className="bg-danger transition-all" style={{ width: `${(violatedCount / total) * 100}%` }} />
                            )}
                        </div>
                        <div className="flex justify-between text-[10px] text-default-500">
                            <span>{fulfilledCount} fulfilled</span>
                            <span>{pendingCount} pending</span>
                            <span>{violatedCount} violated</span>
                        </div>
                    </div>
                )}
            </Card.Content>
        </Card>
    );
}
