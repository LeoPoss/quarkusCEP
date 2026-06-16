"use client";

import { Card, ScrollShadow, Spinner } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { Terminal } from "@phosphor-icons/react";
import { api } from "@/lib/api";

interface ExecutionLog {
    timestamp: number;
    type: string;
    constraintName: string;
    targetTask: string;
    message?: string;
    payload: Record<string, string>;
}

export default function ExecutionLogs() {
    const { data: logs = [], isLoading } = useQuery<ExecutionLog[]>({
        queryKey: ["esper", "execution-logs"],
        queryFn: async () => await api.get("esper/execution-logs").json(),
        refetchInterval: 1000,
    });

    return (
        <Card className="h-full">
            <Card.Header>
                <Card.Title>Automated Enforcement Log</Card.Title>
            </Card.Header>
            <Card.Content>
                <div className="bg-zinc-950 text-zinc-100 border border-zinc-800 rounded-lg p-3 font-mono text-xs shadow-inner h-52 flex flex-col justify-between">
                    <ScrollShadow className="flex-1 overflow-auto space-y-1.5 pr-2">
                        {isLoading ? (
                            <div className="flex items-center justify-center h-full">
                                <Spinner size="sm" color="success" />
                            </div>
                        ) : logs.length > 0 ? (
                            logs.map((log, i) => {
                                const timeStr = new Date(log.timestamp).toLocaleTimeString("de-DE", {
                                    hour: "2-digit",
                                    minute: "2-digit",
                                    second: "2-digit",
                                });
                                const payloadStr = log.payload && Object.keys(log.payload).length > 0 ? JSON.stringify(log.payload) : "";
                                return (
                                    <div key={i} className="leading-normal flex flex-col sm:flex-row sm:items-start gap-1 text-[11px]">
                                        <span className="text-zinc-500 shrink-0 select-none">[{timeStr}]</span>
                                        <div className="flex-1">
                                            {log.type === "TRIGGER" && (
                                                <>
                                                    <span className="text-amber-400 font-semibold">[TRIGGER] </span>
                                                    <span className="text-zinc-300">Constraint </span>
                                                    <span className="text-amber-400 font-semibold">"{log.constraintName}"</span>
                                                    <span className="text-zinc-300"> activated. Injected target task </span>
                                                    <span className="text-sky-400 font-semibold">"{log.targetTask}"</span>
                                                    {payloadStr && (
                                                        <>
                                                            <span className="text-zinc-300"> with payload: </span>
                                                            <span className="text-zinc-400 select-all font-mono">{payloadStr}</span>
                                                        </>
                                                    )}
                                                </>
                                            )}
                                            {log.type === "START" && (
                                                <>
                                                    <span className="text-blue-400 font-semibold">[WORKER] </span>
                                                    <span className="text-zinc-400">⚙️ {log.message}</span>
                                                </>
                                            )}
                                            {log.type === "COMPLETE" && (
                                                <>
                                                    <span className="text-emerald-400 font-semibold">[SUCCESS] </span>
                                                    <span className="text-zinc-300">✅ {log.message}</span>
                                                </>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        ) : (
                            <div className="text-zinc-500 h-full flex flex-col items-center justify-center gap-2">
                               Nothing yet...
                            </div>
                        )}
                    </ScrollShadow>
                </div>
            </Card.Content>
        </Card>
    );
}
