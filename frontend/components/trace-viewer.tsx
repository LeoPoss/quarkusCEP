"use client";

import { Card, CardBody, CardHeader, Chip, Spinner, Tooltip } from "@heroui/react";
import { ClockCounterClockwiseIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";

import { cardHeader } from "./primitives";

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
            <Card>
                <CardBody className="flex justify-center py-4">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    if (error) {
        return (
            <Card>
                <CardBody className="text-red-500 text-sm">Error: {error.message}</CardBody>
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader className={cardHeader()}>
                <ClockCounterClockwiseIcon className="mr-2" size={24} />
                <span className="text-sm">Trace</span>
                {trace.length > 0 && (
                    <Chip size="sm" variant="flat" className="ml-auto">{trace.length}</Chip>
                )}
            </CardHeader>
            <CardBody className="p-2">
                {trace.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-2">No events</div>
                ) : (
                    <div className="h-48 overflow-y-auto space-y-1 pr-1">
                        {[...trace].reverse().map((event, index) => {
                            const originalIndex = trace.length - index;
                            const hasPayload = event.payload && Object.keys(event.payload).length > 0;
                            return (
                                <div
                                    key={index}
                                    className="flex items-start gap-2 text-xs p-1.5 rounded bg-slate-50 dark:bg-slate-800/50"
                                >
                                    <span className="text-gray-400 font-mono w-4 shrink-0">
                                        {originalIndex}
                                    </span>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-1">
                                            <span
                                                className={`w-2 h-2 rounded-full shrink-0 ${hasPayload ? "bg-green-500" : "bg-blue-500"
                                                    }`}
                                            />
                                            <span className="font-medium truncate">{event.eventType}</span>
                                        </div>
                                        {hasPayload && (
                                            <code className="text-[10px] text-gray-500 dark:text-gray-400 block mt-0.5 truncate">
                                                {JSON.stringify(event.payload)}
                                            </code>
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
