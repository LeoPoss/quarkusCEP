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
                <Card.Title>Task Log</Card.Title>
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
                                return (
                                    <div key={i} className="leading-normal flex gap-2 text-[11px]">
                                        <span className="text-zinc-500 shrink-0 select-none">[{timeStr}]</span>
                                        {log.type === "START" && (
                                            <span className="text-blue-400">
                                                {log.message}
                                            </span>
                                        )}
                                        {log.type === "COMPLETE" && (
                                            <span className="text-emerald-400">
                                                {log.message}
                                            </span>
                                        )}
                                    </div>
                                );
                            })
                        ) : (
                            <div className="text-zinc-500 h-full flex flex-col items-center justify-center gap-2">
                               Nothing to see here...
                            </div>
                        )}
                    </ScrollShadow>
                </div>
            </Card.Content>
        </Card>
    );
}
