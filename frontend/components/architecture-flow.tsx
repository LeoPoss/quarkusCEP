"use client";

import { Card, CardBody, Chip, Tooltip } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useTheme } from "next-themes";
import ShikiHighlighter from "react-shiki";
import { api } from "@/lib/api";
import { CaretDown, CaretUp } from "@phosphor-icons/react";
import eql from "../langs/eql.tmLanguage.json";

interface TraceEvent {
    eventType: string;
    payload?: Record<string, string>;
    timestamp: number;
}

interface EplStatement {
    deploymentId: string;
    statement: string;
    type: string;
}

interface Constraint {
    name: string;
    type: string;
    status: string;
    activationEvent?: { name: string; type: string };
    targetEvent?: { name: string; type: string };
    activationCondition?: { param: string; operator: string; value: string; timer?: number };
    targetCondition?: { param: string; operator: string; value: string; timer?: number };
    eplStatements?: EplStatement[];
    autoExecute?: boolean;
}

interface FinishabilityResponse {
    canFinish: boolean;
    reasons: string[];
}

const statusColor: Record<string, "success" | "warning" | "danger" | "default"> = {
    FULFILLED: "success",
    TEMPORARY_VIOLATION: "warning",
    PERMANENT_VIOLATION: "danger",
    INIT: "default",
};

export default function ArchitectureFlow() {
    const { resolvedTheme } = useTheme();
    const [expandedConstraint, setExpandedConstraint] = useState<string | null>(null);
    const [signalChanges, setSignalChanges] = useState<Array<{name: string; payload: Record<string, string>; timestamp: number}>>([]);
    const prevSignalStates = useRef<Record<string, Record<string, string>>>({});

    const { data: trace = [] } = useQuery<TraceEvent[]>({
        queryKey: ["esper", "trace"],
        queryFn: () => api.get("esper/trace").json(),
        refetchInterval: 1000,
    });

    const { data: constraints = [] } = useQuery<Constraint[]>({
        queryKey: ["constraints"],
        queryFn: () => api.get("constraints").json(),
        refetchInterval: 1000,
    });

    const { data: finishability } = useQuery<FinishabilityResponse>({
        queryKey: ["analysis", "finishability"],
        queryFn: () => api.get("analysis/finishability").json(),
        refetchInterval: 1000,
    });

    const { data: signalStates = {} } = useQuery<Record<string, Record<string, string>>>({
        queryKey: ["esper", "signals"],
        queryFn: () => api.get("esper/signals").json(),
        refetchInterval: 1000,
    });

    // Track signal state changes for the timeline
    useEffect(() => {
        const prev = prevSignalStates.current;
        const newEntries: Array<{name: string; payload: Record<string, string>; timestamp: number}> = [];

        for (const [name, payload] of Object.entries(signalStates)) {
            const prevPayload = prev[name];
            if (!prevPayload || JSON.stringify(prevPayload) !== JSON.stringify(payload)) {
                newEntries.push({ name, payload, timestamp: Date.now() });
            }
        }

        if (newEntries.length > 0) {
            setSignalChanges((prev) => [...prev, ...newEntries].slice(-20));
        }

        prevSignalStates.current = JSON.parse(JSON.stringify(signalStates));
    }, [signalStates]);

    const feed = [
        ...trace.map((e) => ({ type: 'task' as const, name: e.eventType, payload: e.payload, timestamp: e.timestamp, id: `task-${e.eventType}-${JSON.stringify(e.payload ?? {})}-${e.timestamp}` })),
        ...signalChanges.map((s) => ({ type: 'signal' as const, name: s.name, payload: s.payload, timestamp: s.timestamp, id: `sig-${s.name}-${JSON.stringify(s.payload)}-${s.timestamp}` })),
    ].sort((a, b) => b.timestamp - a.timestamp);
    const fulfilledCount = constraints.filter((c) => c.status === "FULFILLED").length;
    const violatedCount = constraints.filter((c) => c.status === "PERMANENT_VIOLATION").length;
    const pendingCount = constraints.filter((c) => c.status === "TEMPORARY_VIOLATION" || c.status === "INIT").length;
    const total = fulfilledCount + pendingCount + violatedCount;

    return (
        <Card className="border-none shadow-sm overflow-visible">
            <CardBody className="p-0">
                <div className="grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-gray-100 dark:divide-gray-800">

                    {/* L1: Atomic Events */}
                    <div className="p-4">
                        <div className="flex items-center gap-2 mb-3">
                            <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                                L1: Atomic Events
                            </span>
                        </div>

                        <div className="max-h-56 overflow-y-auto space-y-0">
                            {trace.length > 0 || signalChanges.length > 0 ? (
                                <div className="relative">
                                    <div className="absolute left-[7px] top-0 bottom-0 w-px bg-gray-200 dark:bg-gray-700" />

                                    {feed.map((ev, i) => {
                                        const isTask = ev.type === 'task';
                                        const isTrigger = isTask && ev.payload?.["source"] === "autoexecute";
                                        const hasPayload = ev.payload && Object.keys(ev.payload).length > 0;
                                        const timeStr = new Date(ev.timestamp).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
                                        return (
                                            <div key={ev.id} className="flex items-start py-0.5 pl-1">
                                                <div className={`w-1.5 h-1.5 rounded-full ring-2 ring-background z-10 shrink-0 mt-1 ${
                                                    isTask ? "bg-blue-500" : "bg-purple-500"
                                                }`} />
                                                <div className="ml-2.5 flex-1 min-w-0 leading-tight flex items-baseline justify-between gap-2">
                                                    <span className="truncate">
                                                        <span className="text-[11px] font-mono text-gray-800 dark:text-gray-200">{ev.name}</span>
                                                        <span className={`ml-1.5 text-[8px] font-medium uppercase ${
                                                            isTask ? "text-blue-400" : "text-purple-400"
                                                        }`}>{isTask ? "task" : "sig"}</span>
                                                        {hasPayload && (
                                                            <span className="ml-1.5 text-[9px] text-gray-400 dark:text-gray-500">
                                                                {isTrigger
                                                                    ? `trigger=${ev.payload!["constraint"]}, ${Object.entries(ev.payload!).filter(([k]) => k !== "source" && k !== "constraint").map(([k, v]) => `${k}=${v}`).join(", ")}`
                                                                    : Object.entries(ev.payload!).map(([k, v]) => `${k}=${v}`).join(", ")}
                                                            </span>
                                                        )}
                                                    </span>
                                                    <span className="text-[9px] text-gray-400 dark:text-gray-500 shrink-0 tabular-nums">{timeStr}</span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="text-[11px] text-gray-400 italic text-center py-3">No events yet</div>
                            )}
                        </div>

                        <div className="flex justify-center mt-2 text-gray-300 dark:text-gray-600">
                            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                        </div>
                    </div>

                    {/* L2: Constraint Level */}
                    <div className="p-4 flex flex-col">
                        <div className="flex items-center gap-2 mb-3 shrink-0">
                            <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                                L2: Constraint Level
                            </span>
                            <span className="text-[10px] text-gray-400">({constraints.length})</span>
                        </div>

                        <div className="space-y-1 flex-1 overflow-y-auto max-h-64">
                            {constraints.length > 0 ? (
                                constraints.map((c) => (
                                    <div key={c.name}>
                                        <button
                                            onClick={() => setExpandedConstraint(expandedConstraint === c.name ? null : c.name)}
                                            className={`w-full text-left cursor-pointer transition-colors px-2.5 py-1.5 rounded-lg text-xs border
                                                ${expandedConstraint === c.name
                                                    ? "bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800"
                                                    : "bg-gray-50 dark:bg-gray-800/40 border-gray-100 dark:border-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700/50"
                                                }`}
                                        >
                                            <div className="flex items-center gap-2">
                                                <span className={`w-2 h-2 rounded-full shrink-0 ${
                                                    c.status === "FULFILLED" ? "bg-green-500" :
                                                    c.status === "TEMPORARY_VIOLATION" ? "bg-amber-500" :
                                                    c.status === "PERMANENT_VIOLATION" ? "bg-red-500" : "bg-gray-400"
                                                }`} />
                                                <span className="font-medium text-gray-800 dark:text-gray-200 truncate flex-1 min-w-0">{c.name}</span>
                                                <Chip size="sm" variant="flat" color={statusColor[c.status] ?? "default"} classNames={{ content: "text-[9px]" }}>
                                                    {c.status === "TEMPORARY_VIOLATION" ? "TEMP" : c.status === "PERMANENT_VIOLATION" ? "PERM" : c.status}
                                                </Chip>
                                                <span className="text-gray-400 shrink-0">
                                                    {expandedConstraint === c.name ? <CaretUp size={12} /> : <CaretDown size={12} />}
                                                </span>
                                            </div>
                                            <div className="text-[9px] text-gray-400 font-mono mt-0.5 ml-4 truncate">
                                                {mpDeclareFormula(c.type, c)}
                                            </div>
                                        </button>
                                        {expandedConstraint === c.name && c.eplStatements && c.eplStatements.length > 0 && (
                                            <div className="mt-1 ml-4 space-y-1 pl-2 border-l-2 border-gray-200 dark:border-gray-700">
                                                {c.eplStatements.map((s, i) => (
                                                    <div key={i}>
                                                        <div className="text-[8px] text-gray-400 mb-0.5 font-mono uppercase">{s.type}</div>
                                                        <ShikiHighlighter
                                                            className="text-[9px] border border-gray-100 dark:border-gray-800 rounded overflow-x-auto"
                                                            language={eql as any}
                                                            theme={resolvedTheme === "dark" ? "material-theme-darker" : "material-theme-lighter"}
                                                        >
                                                            {s.statement.trim()}
                                                        </ShikiHighlighter>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                ))
                            ) : (
                                <div className="text-[11px] text-gray-400 italic text-center py-3">No constraints yet</div>
                            )}
                        </div>

                        {/* Mini distribution bar */}
                        {total > 0 && (
                            <div className="flex gap-0.5 mt-2 shrink-0">
                                {fulfilledCount > 0 && (
                                    <Tooltip content={`${fulfilledCount} fulfilled`} placement="top" showArrow={false} offset={4} size="sm">
                                        <div
                                            className="h-1 rounded-full bg-green-500 cursor-help"
                                            style={{ width: `${(fulfilledCount / total) * 100}%` }}
                                        />
                                    </Tooltip>
                                )}
                                {pendingCount > 0 && (
                                    <Tooltip content={`${pendingCount} pending`} placement="top" showArrow={false} offset={4} size="sm">
                                        <div
                                            className="h-1 rounded-full bg-amber-500 cursor-help"
                                            style={{ width: `${(pendingCount / total) * 100}%` }}
                                        />
                                    </Tooltip>
                                )}
                                {violatedCount > 0 && (
                                    <Tooltip content={`${violatedCount} violated`} placement="top" showArrow={false} offset={4} size="sm">
                                        <div
                                            className="h-1 rounded-full bg-red-500 cursor-help"
                                            style={{ width: `${(violatedCount / total) * 100}%` }}
                                        />
                                    </Tooltip>
                                )}
                            </div>
                        )}

                        <div className="flex justify-center mt-2 text-gray-300 dark:text-gray-600 shrink-0">
                            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                        </div>
                    </div>

                    {/* L3: Process Level */}
                    <div className="p-4 flex flex-col">
                        <div className="flex items-center gap-2 mb-4">
                            <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                                L3: Process Level
                            </span>
                        </div>

                        <div className="flex-1 flex flex-col items-center justify-center gap-4">
                            <div className={`flex flex-col items-center gap-3 p-5 rounded-xl border w-full max-w-xs mx-auto ${
                                finishability?.canFinish
                                    ? "bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800"
                                    : "bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-800"
                            }`}>
                                <div className={`w-12 h-12 rounded-full flex items-center justify-center text-lg font-bold ${
                                    finishability?.canFinish ? "bg-green-500 text-white" : "bg-amber-500 text-white"
                                }`}>
                                    {finishability?.canFinish ? "✓" : "!"}
                                </div>
                                <div className="text-center">
                                    <div className="text-sm font-semibold text-gray-800 dark:text-gray-200">
                                        {finishability?.canFinish ? "Process finishable" : "Cannot finish"}
                                    </div>
                                    <div className="text-[11px] text-gray-500 dark:text-gray-400 mt-1 leading-relaxed">
                                        {constraints.length} constraint{constraints.length !== 1 ? "s" : ""}<br />
                                        <span className="text-green-600 dark:text-green-400">{fulfilledCount} fulfilled</span>
                                        {" · "}
                                        <span className="text-amber-600 dark:text-amber-400">{pendingCount} pending</span>
                                        {" · "}
                                        <span className="text-red-600 dark:text-red-400">{violatedCount} violated</span>
                                    </div>
                                </div>
                            </div>

                            {!finishability?.canFinish && finishability?.reasons && finishability.reasons.length > 0 && (
                                <div className="w-full max-w-xs mx-auto space-y-1.5">
                                    <span className="text-[10px] font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider block text-center">
                                        Blocking constraints
                                    </span>
                                    {finishability.reasons.slice(0, 3).map((reason, i) => (
                                        <div key={i} className="text-[10px] text-gray-600 dark:text-gray-400 font-mono leading-snug pl-2 border-l-2 border-amber-300 dark:border-amber-700 py-0.5">
                                            {reason}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </CardBody>
        </Card>
    );
}

function mpDeclareFormula(type: string, c?: Constraint): string {
    if (!c) return type;

    const formatEvent = (name?: string, evtType?: string,
        cond?: { param?: string; operator?: string; value?: string; timer?: number },
        isAuto?: boolean
    ): string => {
        if (!name) return "?";
        const condStr = cond?.param && cond?.operator && cond?.value
            ? `[${cond.param} ${cond.operator} ${cond.value}]` : "";
        const timer = cond?.timer ? `[0,${cond.timer}]` : "";
        const isTask = evtType?.toLowerCase() === "task";
        const prefix = isTask ? (isAuto ? "auto(" : "dis(") : "";
        const suffix = isTask ? ")" : "";
        return `${prefix}${name}${condStr}${timer}${suffix}`;
    };

    const evtA = formatEvent(c.activationEvent?.name, c.activationEvent?.type, c.activationCondition);
    const evtB = formatEvent(c.targetEvent?.name, c.targetEvent?.type, c.targetCondition, c.autoExecute);

    if (["EXISTENCE", "NOTEXISTENCE", "NOT_EXISTENCE"].includes(type)) {
        return `${type}(${c.targetEvent?.name ? evtB : evtA})`;
    }
    return `${type}(${evtA}, ${evtB})`;
}
