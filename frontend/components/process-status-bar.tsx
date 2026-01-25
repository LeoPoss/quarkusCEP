"use client";

import { Card, CardBody, CardHeader, Spinner } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";

interface FinishabilityResponse {
    canFinish: boolean;
    reasons: string[];
}

interface Constraint {
    name: string;
    status: string;
}

const fetchFinishability = async (): Promise<FinishabilityResponse> => {
    return await ky.get("http://localhost:8080/analysis/finishability").json();
};

const fetchConstraints = async (): Promise<Constraint[]> => {
    return await ky.get("http://localhost:8080/constraints").json();
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
            <Card className="h-full border-none shadow-sm">
                <CardBody className="flex justify-center py-4">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    const fulfilledCount = constraints.filter((c) => c.status === "FULFILLED").length;
    const violatedCount = constraints.filter((c) => c.status === "PERMANENT_VIOLATION").length;
    const pendingCount = constraints.filter((c) => c.status === "TEMPORARY_VIOLATION" || c.status === "INIT").length;

    return (
        <Card className="h-full border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 bg-gradient-to-b from-gray-50/80 to-gray-100/50 dark:from-gray-800/80 dark:to-gray-800/50 text-gray-700 dark:text-gray-200">
                Process Status
            </CardHeader>
            <CardBody className="p-4 space-y-3">
                {/* Finishability */}
                <div className="flex flex-col gap-1">
                    <div className="flex items-center justify-between">
                        <span className="text-xs text-gray-600 dark:text-gray-400">Finishable</span>
                        <span className={`text-xs font-mono font-medium ${finishability?.canFinish ? "text-green-600 dark:text-green-400" : "text-amber-600 dark:text-amber-400"}`}>
                            {finishability?.canFinish ? "YES" : "NO"}
                        </span>
                    </div>
                    {/* Inline reasons if not finishable */}
                    {!finishability?.canFinish && finishability?.reasons && finishability.reasons.length > 0 && (
                        <div className="mt-1 pl-2 border-l-2 border-amber-200 dark:border-amber-900/50 space-y-1">
                            {finishability.reasons.slice(0, 3).map((reason, idx) => (
                                <div key={idx} className="text-[10px] text-gray-500 leading-tight">
                                    {reason}
                                </div>
                            ))}
                            {finishability.reasons.length > 3 && (
                                <div className="text-[10px] text-gray-400 italic">
                                    + {finishability.reasons.length - 3} more...
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Constraint counts */}
                <div className="text-xs">
                    <div className="flex justify-between bg-green-50 dark:bg-green-900/50 p-1">
                        <span className="text-green-600 dark:text-green-400">Fulfilled</span>
                        <span className="font-mono text-green-900 dark:text-green-100">{fulfilledCount}</span>
                    </div>
                    <div className="flex justify-between bg-amber-50 dark:bg-amber-900/50 p-1">
                        <span className="text-amber-600 dark:text-amber-400">Pending</span>
                        <span className="font-mono text-amber-900 dark:text-amber-100">{pendingCount}</span>
                    </div>
                    <div className="flex justify-between bg-red-50 dark:bg-red-900/50 p-1">
                        <span className="text-red-600 dark:text-red-400">Violated</span>
                        <span className="font-mono text-red-900 dark:text-red-100">{violatedCount}</span>
                    </div>
                </div>
            </CardBody>
        </Card>
    );
}
