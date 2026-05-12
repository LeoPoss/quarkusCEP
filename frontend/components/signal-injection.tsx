"use client";

import {
    toast,
    Button,
    Card,
    Separator,
    Input,
    Spinner,
} from "@heroui/react";
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
            toast.success("Signal sent", {
                description: signalName,
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            queryClient.invalidateQueries({ queryKey: ["esper", "signals"] });
        },
        onError: (error: any) => {
            toast.danger("Error", {
                description: error.message,
            });
        },
    });

    const handleInject = () => {
        if (!signalName.trim()) {
            toast.warning("Error", {
                description: "Signal name required",
            });
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
        <Card>
            <Card.Header>
                <Card.Title>Signal Injection</Card.Title>
            </Card.Header>
            <Card.Content className="flex flex-col gap-3">
                <div className="flex gap-2 items-center">
                    <Input variant="secondary"
                        placeholder="Signal Name"
                        value={signalName}
                        onChange={(e) => setSignalName(e.target.value)}
                        className="flex-1 font-mono"
                    />
                    <Input variant="secondary"
                        placeholder="Key"
                        value={paramKey}
                        onChange={(e) => setParamKey(e.target.value)}
                        className="w-1/4 font-mono"
                    />
                    <span className="text-default-300">=</span>
                    <Input variant="secondary"
                        placeholder="Value"
                        value={paramValue}
                        onChange={(e) => setParamValue(e.target.value)}
                        className="w-1/4 font-mono"
                    />
                </div>

                <Button
                    variant="secondary"
                    isPending={injectSignalMutation.isPending}
                    onPress={handleInject}
                    className="w-full font-medium"
                >
                    {({isPending}) => (
                        <>
                            {isPending && <Spinner color="current" size="sm" />}
                            Inject Signal
                        </>
                    )}
                </Button>

                {hasSignals && <Separator className="my-1" />}

                {/* Current signal states */}
                {hasSignals ? (
                    <div className="space-y-1.5">
                        <span className="text-[11px] font-medium text-default-500">
                            Current Signal States
                        </span>
                        {Object.entries(signalStates).map(([name, payload]) => (
                            <div
                                key={name}
                                className="flex items-center gap-2 px-2 py-1.5 rounded bg-default-50 font-mono text-xs"
                            >
                                <span className="font-semibold text-foreground shrink-0">
                                    {name}
                                </span>
                                {Object.keys(payload).length > 0 ? (
                                    <div className="flex flex-wrap gap-1.5">
                                        {Object.entries(payload).map(([k, v]) => (
                                            <span key={k} className="text-default-500">
                                                <span className="text-foreground">{k}</span>
                                                <span className="text-default-400">=</span>
                                                <span className="text-accent">"{v}"</span>
                                            </span>
                                        ))}
                                    </div>
                                ) : (
                                    <span className="text-default-400 italic">(no payload)</span>
                                )}
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-[10px] text-default-400 text-center italic py-1">
                        No signals injected yet
                    </div>
                )}
            </Card.Content>
        </Card>
    );
}
