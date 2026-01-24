"use client";

import { addToast, Button, Card, CardBody, CardHeader, Input } from "@heroui/react";
import { PlusIcon, TrashIcon, WaveformIcon } from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import ky from "ky";
import { useState } from "react";

import { cardHeader } from "./primitives";

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
                title: "Signal Injected",
                description: `Signal "${signalName}" sent`,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
        },
        onError: (error) => {
            addToast({ title: "Failed", description: error.message, color: "danger" });
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
        <Card>
            <CardHeader className={cardHeader()}>
                <WaveformIcon className="mr-2" size={24} />
                <span className="text-sm">Signal Injection</span>
            </CardHeader>
            <CardBody className="p-2 space-y-2">
                <div className="flex gap-2">
                    <Input
                        size="sm"
                        placeholder="Signal name"
                        value={signalName}
                        onChange={(e) => setSignalName(e.target.value)}
                        className="flex-1"
                    />
                    <Button size="sm" variant="light" isIconOnly onPress={addPayloadField}>
                        <PlusIcon size={16} />
                    </Button>
                    <Button
                        size="sm"
                        color="primary"
                        isLoading={injectSignalMutation.isPending}
                        onPress={handleInject}
                    >
                        Inject
                    </Button>
                </div>
                {payloadFields.length > 0 && (
                    <div className="space-y-1">
                        {payloadFields.map((field) => (
                            <div key={field.id} className="flex gap-1 items-center">
                                <Input
                                    size="sm"
                                    placeholder="key"
                                    value={field.key}
                                    onChange={(e) => updatePayloadField(field.id, "key", e.target.value)}
                                    className="flex-1"
                                />
                                <span className="text-gray-400">=</span>
                                <Input
                                    size="sm"
                                    placeholder="value"
                                    value={field.value}
                                    onChange={(e) => updatePayloadField(field.id, "value", e.target.value)}
                                    className="flex-1"
                                />
                                <Button
                                    isIconOnly
                                    size="sm"
                                    variant="light"
                                    color="danger"
                                    onPress={() => removePayloadField(field.id)}
                                >
                                    <TrashIcon size={14} />
                                </Button>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
