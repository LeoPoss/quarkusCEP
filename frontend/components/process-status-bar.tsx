"use client";

import { Card, CardBody, CardHeader, Chip, Spinner, Tooltip } from "@heroui/react";
import {
    CheckCircleIcon,
    WarningCircleIcon,
    GaugeIcon,
    ClockIcon,
    XCircleIcon,
} from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";

import { cardHeader } from "./primitives";

interface FinishabilityResponse {
    canFinish: boolean;
    reasons: string[];
}

interface Constraint {
    name: string;
    status: string;
    type: string;
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
            <Card>
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
        <Card className="h-full">
            <CardHeader className={cardHeader()}>
                <GaugeIcon className="mr-2" size={24} />
                <span className="text-sm">Status</span>
            </CardHeader>
            <CardBody className="p-3">
                <div className="space-y-3">
                    {/* Finishability */}
                    <Tooltip
                        content={
                            finishability?.canFinish
                                ? "Process can complete"
                                : finishability?.reasons?.slice(0, 3).join(", ") || "Cannot finish"
                        }
                    >
                        <div className={`flex items-center gap-2 p-2 rounded-lg cursor-help ${finishability?.canFinish
                            ? "bg-green-50 dark:bg-green-900/20"
                            : "bg-amber-50 dark:bg-amber-900/20"
                            }`}>
                            {finishability?.canFinish ? (
                                <CheckCircleIcon size={20} weight="fill" className="text-green-500" />
                            ) : (
                                <WarningCircleIcon size={20} weight="fill" className="text-amber-500" />
                            )}
                            <span className="text-sm font-medium">
                                {finishability?.canFinish ? "Can Finish" : "Blocked"}
                            </span>
                        </div>
                    </Tooltip>

                    {/* Constraint counts */}
                    <div className="grid grid-cols-3 gap-2 text-center">
                        <div className="flex flex-col items-center gap-1 p-2 rounded-lg bg-slate-50 dark:bg-slate-800/50">
                            <CheckCircleIcon size={18} weight="fill" className="text-green-500" />
                            <span className="text-lg font-bold text-green-600 dark:text-green-400">
                                {fulfilledCount}
                            </span>
                            <span className="text-[10px] text-gray-500">Fulfilled</span>
                        </div>
                        <div className="flex flex-col items-center gap-1 p-2 rounded-lg bg-slate-50 dark:bg-slate-800/50">
                            <ClockIcon size={18} weight="fill" className="text-amber-500" />
                            <span className="text-lg font-bold text-amber-600 dark:text-amber-400">
                                {pendingCount}
                            </span>
                            <span className="text-[10px] text-gray-500">Pending</span>
                        </div>
                        <div className="flex flex-col items-center gap-1 p-2 rounded-lg bg-slate-50 dark:bg-slate-800/50">
                            <XCircleIcon size={18} weight="fill" className="text-red-500" />
                            <span className="text-lg font-bold text-red-600 dark:text-red-400">
                                {violatedCount}
                            </span>
                            <span className="text-[10px] text-gray-500">Violated</span>
                        </div>
                    </div>
                </div>
            </CardBody>
        </Card>
    );
}
