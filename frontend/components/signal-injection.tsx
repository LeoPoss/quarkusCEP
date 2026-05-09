"use client";

import { addToast, Button, Card, CardBody, CardHeader, Input } from "@heroui/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

export default function SignalInjection() {
    const queryClient = useQueryClient();
    const [signalName, setSignalName] = useState("");
    const [paramKey, setParamKey] = useState("");
    const [paramValue, setParamValue] = useState("");

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
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-b border-default-200 dark:border-default-700 hover:bg-default-200 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20 transition-all"
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
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-b border-default-200 dark:border-default-700 hover:bg-default-200 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20 transition-all"
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
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-b border-default-200 dark:border-default-700 hover:bg-default-200 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20 transition-all"
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
            </CardBody>
        </Card>
    );
}
