"use client";

import { addToast, Button, Card, CardBody, CardHeader, Divider, Input, Spinner } from "@heroui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

export default function SignalInjection() {
    const queryClient = useQueryClient();
    const [signalName, setSignalName] = useState("");
    const [paramKey, setParamKey] = useState("");
    const [paramValue, setParamValue] = useState("");

    const { data: signalStates = {} } = useQuery<Record<string, Record<string, string>>>({
        queryKey: ["esper", "signals"],
        queryFn: async () => await api.get("esper/signals").json(),
        refetchInterval: 1000,
    });

    const hasSignals = Object.keys(signalStates).length > 0;

    const injectSignalMutation = useMutation({
        mutationFn: async (event: { eventType: string; payload?: Record<string, string> }) => {
            return await api.post("esper/event", { json: event });
        },
        onSuccess: () => {
            addToast({
                title: "Signal sent",
                description: signalName,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            queryClient.invalidateQueries({ queryKey: ["esper", "signals"] });
        },
        onError: (error) => {
            addToast({ title: "Error", description: error.message, color: "danger" });
        },
    });

    const handleInject = () => {
        if (!signalName.trim()) {
            addToast({ title: "Error", description: "Signal name required", color: "warning" });
            return;
        }

        const payload: Record<string, string> = {};
        if (paramKey.trim() && paramValue.trim()) {
            payload[paramKey.trim()] = paramValue.trim();
        }

        injectSignalMutation.mutate({
            eventType: signalName.trim(),
            payload: Object.keys(payload).length > 0 ? payload : undefined,
        });
    };

    return (
        <Card className="border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 border-b border-gray-100 dark:border-gray-800 text-gray-700 dark:text-gray-200">
                Signal Injection
            </CardHeader>
            <CardBody className="p-4 flex flex-col gap-3">
                <div className="flex gap-2 items-center">
                    <Input
                        size="sm"
                        placeholder="Signal Name"
                        value={signalName}
                        onChange={(e) => setSignalName(e.target.value)}
                        className="flex-1 font-mono text-sm"
                        classNames={{
                            input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-none hover:bg-default-200 transition-all"
                        }}
                    />
                    <Input
                        size="sm"
                        placeholder="Payload Key"
                        value={paramKey}
                        onChange={(e) => setParamKey(e.target.value)}
                        className="w-1/4 font-mono"
                        classNames={{
                            input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-none hover:bg-default-200 transition-all"
                        }}
                    />
                    <span className="text-gray-300">=</span>
                    <Input
                        size="sm"
                        placeholder="Payload Value"
                        value={paramValue}
                        onChange={(e) => setParamValue(e.target.value)}
                        className="w-1/4 font-mono"
                        classNames={{
                            input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-none hover:bg-default-200 transition-all"
                        }}
                    />
                </div>

                <Button
                    size="sm"
                    color="secondary"
                    variant="flat"
                    isLoading={injectSignalMutation.isPending}
                    onPress={handleInject}
                    className="w-full font-medium"
                >
                    Inject Signal
                </Button>

                {hasSignals && <Divider className="my-1" />}

                {/* Current signal states */}
                {hasSignals ? (
                    <div className="space-y-1.5">
                        <span className="text-[11px] font-medium text-gray-600 dark:text-gray-400">
                            Current Signal States
                        </span>
                        {Object.entries(signalStates).map(([name, payload]) => (
                            <div
                                key={name}
                                className="flex items-center gap-2 px-2 py-1.5 rounded bg-gray-50 dark:bg-gray-800/40 font-mono text-xs"
                            >
                                <span className="font-semibold text-gray-800 dark:text-gray-200 shrink-0">
                                    {name}
                                </span>
                                {Object.keys(payload).length > 0 ? (
                                    <div className="flex flex-wrap gap-1.5">
                                        {Object.entries(payload).map(([k, v]) => (
                                            <span key={k} className="text-gray-500 dark:text-gray-400">
                                                <span className="text-gray-700 dark:text-gray-300">{k}</span>
                                                <span className="text-gray-400">=</span>
                                                <span className="text-blue-600 dark:text-blue-400">"{v}"</span>
                                            </span>
                                        ))}
                                    </div>
                                ) : (
                                    <span className="text-gray-400 italic">(no payload)</span>
                                )}
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-[10px] text-gray-400 text-center italic py-1">
                        No signals injected yet
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
