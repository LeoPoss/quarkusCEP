"use client";

import { Card, CardBody, CardHeader, Spinner } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";

interface TraceEvent {
    eventType: string;
    payload?: Record<string, string>;
}

const fetchTrace = async (): Promise<TraceEvent[]> => {
    return await ky.get("http://localhost:8080/esper/trace").json();
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
            <CardHeader className="text-sm font-medium px-4 py-3 flex justify-between items-center bg-gradient-to-b from-gray-50/80 to-gray-100/50 dark:from-gray-800/80 dark:to-gray-800/50 text-gray-700 dark:text-gray-200">
                <span>Execution Trace</span>
                <span className="text-xs font-mono opacity-60">{trace.length}</span>
            </CardHeader>
            <CardBody className="p-0">
                {trace.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-8 italic">No tasks executed</div>
                ) : (
                    <div className="h-48 overflow-y-auto font-mono text-xs">
                        {[...trace].reverse().map((event, index) => {
                            const originalIndex = trace.length - index;
                            const hasPayload = event.payload && Object.keys(event.payload).length > 0;
                            return (
                                <div
                                    key={index}
                                    className={`flex gap-3 py-1.5 px-4 ${index % 2 === 0 ? 'bg-transparent' : 'bg-gray-50/50 dark:bg-gray-800/30'}`}
                                >
                                    <span className="text-gray-400 w-4 text-right shrink-0 select-none">
                                        {originalIndex}
                                    </span>
                                    <div className="flex-1 min-w-0">
                                        <div className="text-gray-900 dark:text-gray-100 font-medium">
                                            {event.eventType}
                                        </div>
                                        {hasPayload && (
                                            <div className="text-gray-500 truncate mt-0.5 text-[10px]">
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
