"use client";

import {
    addToast,
    Button,
    Card,
    CardBody,
    CardHeader,
    Checkbox,
    Input,
    Select,
    SelectItem,
    SelectSection,
} from "@heroui/react";
import { FilePlusIcon } from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import ky from "ky";
import { useState } from "react";

import { cardHeader } from "./primitives";

// Constraints that only need one event (target = activation)
const singleEventConstraints = ["existence", "notexistence"];

export default function ConstraintQuickCreate() {
    const queryClient = useQueryClient();
    const [constraintType, setConstraintType] = useState("");
    const [name, setName] = useState("");
    const [activationEvent, setActivationEvent] = useState("");
    const [activationEventType, setActivationEventType] = useState<"task" | "signal">("task");
    const [targetEvent, setTargetEvent] = useState("");
    const [targetEventType, setTargetEventType] = useState<"task" | "signal">("task");
    const [timer, setTimer] = useState("");

    // Conditions
    const [showConditions, setShowConditions] = useState(false);
    const [actParam, setActParam] = useState("");
    const [actOperator, setActOperator] = useState("");
    const [actValue, setActValue] = useState("");
    const [tgtParam, setTgtParam] = useState("");
    const [tgtOperator, setTgtOperator] = useState("");
    const [tgtValue, setTgtValue] = useState("");

    const createConstraintMutation = useMutation({
        mutationFn: async (payload: Record<string, unknown>) => {
            return await ky.post(
                `http://localhost:8080/constraints/${constraintType.toLowerCase()}`,
                { json: payload }
            );
        },
        onSuccess: () => {
            addToast({
                title: "Constraint Created",
                description: `Constraint "${name}" created`,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["constraints"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            // Keep form values for rapid iteration
        },
        onError: (error) => {
            addToast({
                title: "Creation Failed",
                description: error.message,
                color: "danger",
            });
        },
    });

    const handleCreate = () => {
        if (!constraintType || !name.trim() || !activationEvent.trim()) {
            addToast({
                title: "Validation Error",
                description: "Please fill in all required fields",
                color: "warning",
            });
            return;
        }

        const needsTarget = !singleEventConstraints.includes(constraintType);
        if (needsTarget && !targetEvent.trim()) {
            addToast({
                title: "Validation Error",
                description: "Target event is required for this constraint type",
                color: "warning",
            });
            return;
        }

        const isSingleEvent = singleEventConstraints.includes(constraintType);

        const payload: Record<string, unknown> = {
            name: name.trim(),
            activationEvent: activationEvent.trim(),
            activationEventType: activationEventType,
            targetEvent: isSingleEvent ? activationEvent.trim() : targetEvent.trim(),
            targetEventType: isSingleEvent ? activationEventType : targetEventType,
            timer: timer ? parseInt(timer, 10) : null,
        };

        // Add conditions if provided
        if (actParam.trim() && actOperator && actValue.trim()) {
            payload.activationCondition = {
                param: actParam.trim(),
                operator: actOperator,
                value: actValue.trim(),
            };
        }
        if (tgtParam.trim() && tgtOperator && tgtValue.trim()) {
            payload.targetCondition = {
                param: tgtParam.trim(),
                operator: tgtOperator,
                value: tgtValue.trim(),
            };
        }

        createConstraintMutation.mutate(payload);
    };

    const needsTarget = constraintType && !singleEventConstraints.includes(constraintType);

    const EventTypeToggle = ({
        value,
        onChange,
        disabled = false,
    }: {
        value: "task" | "signal";
        onChange: (v: "task" | "signal") => void;
        disabled?: boolean;
    }) => (
        <div className={`flex bg-default-100 dark:bg-default-50 p-0.5 rounded text-xs ${disabled ? "opacity-50" : ""}`}>
            <button
                type="button"
                disabled={disabled}
                className={`px-2 py-1 rounded transition-colors ${value === "signal"
                    ? "bg-white dark:bg-default-200 shadow-sm"
                    : "text-foreground-500 hover:bg-default-200"
                    }`}
                onClick={() => onChange("signal")}
            >
                Sig
            </button>
            <button
                type="button"
                disabled={disabled}
                className={`px-2 py-1 rounded transition-colors ${value === "task"
                    ? "bg-white dark:bg-default-200 shadow-sm"
                    : "text-foreground-500 hover:bg-default-200"
                    }`}
                onClick={() => onChange("task")}
            >
                Task
            </button>
        </div>
    );

    return (
        <Card>
            <CardHeader className={cardHeader()}>
                <FilePlusIcon className="mr-4" size={32} />
                Quick Create Constraint
            </CardHeader>
            <CardBody>
                <div className="space-y-3">
                    <div className="grid grid-cols-2 gap-2">
                        <Select
                            aria-label="Constraint Type"
                            placeholder="Type"
                            size="sm"
                            selectedKeys={constraintType ? [constraintType] : []}
                            onChange={(e) => setConstraintType(e.target.value)}
                        >
                            <SelectSection title="Existence">
                                <SelectItem key="existence">Existence</SelectItem>
                                <SelectItem key="notexistence">NotExistence</SelectItem>
                            </SelectSection>
                            <SelectSection title="Relation">
                                <SelectItem key="response">Response</SelectItem>
                                <SelectItem key="precedence">Precedence</SelectItem>
                                <SelectItem key="respondedexistence">RespondedExist</SelectItem>
                            </SelectSection>
                            <SelectSection title="Negative">
                                <SelectItem key="notresponse">NotResponse</SelectItem>
                            </SelectSection>
                        </Select>
                        <Input
                            placeholder="Name"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            size="sm"
                            isRequired
                        />
                    </div>

                    {/* Events row */}
                    <div className="flex gap-2 items-center">
                        <Input
                            placeholder={needsTarget ? "Activation (A)" : "Event"}
                            value={activationEvent}
                            onChange={(e) => setActivationEvent(e.target.value)}
                            size="sm"
                            className="flex-1"
                            isRequired
                        />
                        <EventTypeToggle value={activationEventType} onChange={setActivationEventType} />

                        {needsTarget && (
                            <>
                                <span className="text-gray-400">→</span>
                                <Input
                                    placeholder="Target (B)"
                                    value={targetEvent}
                                    onChange={(e) => setTargetEvent(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                    isRequired
                                />
                                <EventTypeToggle value={targetEventType} onChange={setTargetEventType} />
                            </>
                        )}
                    </div>

                    {/* Timer */}
                    <Input
                        placeholder="Timer (sec) - optional"
                        value={timer}
                        onChange={(e) => setTimer(e.target.value)}
                        size="sm"
                        type="number"
                        min={1}
                    />

                    {/* Conditions toggle */}
                    <Checkbox size="sm" isSelected={showConditions} onValueChange={setShowConditions}>
                        <span className="text-xs">Add payload conditions</span>
                    </Checkbox>

                    {showConditions && (
                        <div className="space-y-2 p-2 bg-slate-50 dark:bg-slate-800/50 rounded border border-slate-200 dark:border-slate-700">
                            {/* Activation condition */}
                            <div className="flex gap-1 items-center text-xs">
                                <span className="w-8 text-gray-500">A:</span>
                                <Input
                                    placeholder="param"
                                    value={actParam}
                                    onChange={(e) => setActParam(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                />
                                <Select
                                    aria-label="Operator"
                                    placeholder="op"
                                    size="sm"
                                    className="w-16"
                                    selectedKeys={actOperator ? [actOperator] : []}
                                    onChange={(e) => setActOperator(e.target.value)}
                                >
                                    <SelectItem key="=">{"="}</SelectItem>
                                    <SelectItem key="!=">{"!="}</SelectItem>
                                    <SelectItem key=">">{">"}</SelectItem>
                                    <SelectItem key="<">{"<"}</SelectItem>
                                </Select>
                                <Input
                                    placeholder="value"
                                    value={actValue}
                                    onChange={(e) => setActValue(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                />
                            </div>
                            {/* Target condition */}
                            {needsTarget && (
                                <div className="flex gap-1 items-center text-xs">
                                    <span className="w-8 text-gray-500">B:</span>
                                    <Input
                                        placeholder="param"
                                        value={tgtParam}
                                        onChange={(e) => setTgtParam(e.target.value)}
                                        size="sm"
                                        className="flex-1"
                                    />
                                    <Select
                                        aria-label="Operator"
                                        placeholder="op"
                                        size="sm"
                                        className="w-16"
                                        selectedKeys={tgtOperator ? [tgtOperator] : []}
                                        onChange={(e) => setTgtOperator(e.target.value)}
                                    >
                                        <SelectItem key="=">{"="}</SelectItem>
                                        <SelectItem key="!=">{"!="}</SelectItem>
                                        <SelectItem key=">">{">"}</SelectItem>
                                        <SelectItem key="<">{"<"}</SelectItem>
                                    </Select>
                                    <Input
                                        placeholder="value"
                                        value={tgtValue}
                                        onChange={(e) => setTgtValue(e.target.value)}
                                        size="sm"
                                        className="flex-1"
                                    />
                                </div>
                            )}
                        </div>
                    )}

                    <Button
                        color="primary"
                        className="w-full"
                        startContent={<FilePlusIcon size={20} weight="fill" />}
                        isLoading={createConstraintMutation.isPending}
                        onPress={handleCreate}
                    >
                        Create Constraint
                    </Button>
                </div>
            </CardBody>
        </Card>
    );
}
