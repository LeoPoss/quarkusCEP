"use client";

import { Card, CardBody, CardHeader, Spinner } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

interface TraceEvent {
    eventType: string;
    payload?: Record<string, string>;
}

const fetchTrace = async (): Promise<TraceEvent[]> => {
    return await api.get("esper/trace").json();
};

export default function TraceViewer() {
    const { data: trace = [], isLoading, error } = useQuery<TraceEvent[]>({
        queryKey: ["esper", "trace"],
        queryFn: fetchTrace,
        refetchInterval: 1000,
    });

    if (isLoading) {
        return (
            <Card className="h-full border-none shadow-sm">
                <CardBody className="flex justify-center py-4">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    if (error) {
        return (
            <Card className="h-full border-none shadow-sm">
                <CardBody className="text-red-600 text-xs">Error: {error.message}</CardBody>
            </Card>
        );
    }

    return (
        <Card className="h-full border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 border-b border-gray-100 dark:border-gray-800 text-gray-700 dark:text-gray-200 flex justify-between items-center">
                <span>Execution Trace</span>
                <span className="text-xs font-mono opacity-60">{trace.length}</span>
            </CardHeader>
            <CardBody className="p-0">
                {trace.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-8">
                        <p className="italic">No tasks executed yet</p>
                        <p className="text-[10px] text-gray-400 mt-1">Send events or complete tasks to populate the trace.</p>
                    </div>
                ) : (
                    <div className="h-48 overflow-y-auto text-xs">
                        {[...trace].reverse().map((event, index) => {
                            const hasPayload = event.payload && Object.keys(event.payload).length > 0;
                            const isLast = index === trace.length - 1;
                            return (
                                <div key={index} className="flex gap-3 px-4">
                                    {/* Timeline column */}
                                    <div className="flex flex-col items-center shrink-0 pt-2">
                                        <div className="w-2 h-2 rounded-full bg-gray-400 dark:bg-gray-500 ring-2 ring-background z-10" />
                                        {!isLast && <div className="w-px flex-1 bg-gray-200 dark:bg-gray-700" />}
                                    </div>
                                    {/* Content */}
                                    <div className={`flex-1 min-w-0 pb-3 ${isLast ? '' : ''}`}>
                                        <div className="text-gray-900 dark:text-gray-100 font-mono font-medium text-[11px]">
                                            {event.eventType}
                                        </div>
                                        {hasPayload && (
                                            <div className="text-gray-500 truncate mt-0.5 text-[10px] font-mono">
                                                {JSON.stringify(event.payload).replace(/["{}]/g, '').replace(/:/g, ': ').replace(/,/g, ', ')}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
