"use client";

import { addToast, Button, Card, CardBody, CardHeader, Input } from "@heroui/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import ky from "ky";
import { useState } from "react";

interface PayloadField {
    id: string;
    key: string;
    value: string;
}

export default function SignalInjection() {
    const queryClient = useQueryClient();
    const [signalName, setSignalName] = useState("");
    const [payloadFields, setPayloadFields] = useState<PayloadField[]>([]);

    const injectSignalMutation = useMutation({
        mutationFn: async (event: { eventType: string; payload?: Record<string, string> }) => {
            return await ky.post("http://localhost:8080/esper/event", { json: event });
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

    const addPayloadField = () => {
        setPayloadFields([...payloadFields, { id: Date.now().toString(), key: "", value: "" }]);
    };

    const removePayloadField = (id: string) => {
        setPayloadFields(payloadFields.filter((f) => f.id !== id));
    };

    const updatePayloadField = (id: string, field: "key" | "value", value: string) => {
        setPayloadFields(payloadFields.map((f) => (f.id === id ? { ...f, [field]: value } : f)));
    };

    const handleInject = () => {
        if (!signalName.trim()) {
            addToast({ title: "Error", description: "Signal name required", color: "warning" });
            return;
        }
        const payload: Record<string, string> = {};
        payloadFields.forEach((f) => {
            if (f.key.trim() && f.value.trim()) {
                payload[f.key.trim()] = f.value.trim();
            }
        });
        injectSignalMutation.mutate({
            eventType: signalName.trim(),
            payload: Object.keys(payload).length > 0 ? payload : undefined,
        });
    };

    return (
        <Card className="border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 bg-gradient-to-b from-gray-50/80 to-gray-100/50 dark:from-gray-800/80 dark:to-gray-800/50 text-gray-700 dark:text-gray-200">
                Signal Injection
            </CardHeader>
            <CardBody className="p-4 space-y-3">
                <div className="flex gap-2">
                    <Input
                        size="sm"
                        placeholder="Signal Name"
                        value={signalName}
                        onChange={(e) => setSignalName(e.target.value)}
                        className="flex-1 font-mono text-sm"
                        classNames={{
                            input: "font-mono bg-default-100 dark:bg-default-50",
                            inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-none hover:bg-default-200"
                        }}
                    />
                    <Button size="sm" variant="flat" onPress={addPayloadField} className="bg-default-100 dark:bg-default-50 text-default-600">
                        + Param
                    </Button>
                </div>
                {payloadFields.length > 0 && (
                    <div className="space-y-2 pl-3 border-l-2 border-default-100 dark:border-default-50">
                        {payloadFields.map((field) => (
                            <div key={field.id} className="flex gap-2 items-center font-mono text-xs">
                                <Input
                                    size="sm"
                                    placeholder="key"
                                    value={field.key}
                                    onChange={(e) => updatePayloadField(field.id, "key", e.target.value)}
                                    className="flex-1"
                                    classNames={{
                                        input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                                        inputWrapper: "h-8 min-h-8 bg-default-100 dark:bg-default-50 shadow-none border-none"
                                    }}
                                />
                                <span className="text-gray-300">=</span>
                                <Input
                                    size="sm"
                                    placeholder="value"
                                    value={field.value}
                                    onChange={(e) => updatePayloadField(field.id, "value", e.target.value)}
                                    className="flex-1"
                                    classNames={{
                                        input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                                        inputWrapper: "h-8 min-h-8 bg-default-100 dark:bg-default-50 shadow-none border-none"
                                    }}
                                />
                                <button
                                    className="text-gray-400 hover:text-red-500 px-1 transition-colors"
                                    onClick={() => removePayloadField(field.id)}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                )}
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
