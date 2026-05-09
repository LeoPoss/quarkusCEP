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
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

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
    const [actTimer, setActTimer] = useState("");
    const [tgtTimer, setTgtTimer] = useState("");

    const createConstraintMutation = useMutation({
        mutationFn: async (payload: Record<string, unknown>) => {
            return await api.post(
                `constraints/${constraintType.toLowerCase()}`,
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
                description: "All fields required",
                color: "warning",
            });
            return;
        }

        const needsTarget = !singleEventConstraints.includes(constraintType);
        if (needsTarget && !targetEvent.trim()) {
            addToast({
                title: "Validation Error",
                description: "Target event required",
                color: "warning",
            });
            return;
        }

        const isSingleEvent = singleEventConstraints.includes(constraintType);

        const payload: Record<string, unknown> = {
            name: name.trim(),
            activationEvent: activationEvent.trim(),
            activationEventType: activationEventType,
            targetEvent: targetEvent.trim(),
            targetEventType: targetEventType,
            timer: timer ? parseInt(timer, 10) : null,
        };

        // Add conditions if provided
        if (actParam.trim() && actOperator && actValue.trim()) {
            payload.activationCondition = {
                param: actParam.trim(),
                operator: actOperator,
                value: actValue.trim(),
                timer: actTimer ? parseInt(actTimer, 10) : undefined,
            };
        }
        if (tgtParam.trim() && tgtOperator && tgtValue.trim()) {
            payload.targetCondition = {
                param: tgtParam.trim(),
                operator: tgtOperator,
                value: tgtValue.trim(),
                timer: tgtTimer ? parseInt(tgtTimer, 10) : undefined,
            };
        }

        createConstraintMutation.mutate(payload);
    };

    const handleDebugSeed = async () => {
        const debugConstraints = [
            {
                type: "notexistence",
                payload: {
                    name: "PreventOverheating",
                    targetEvent: "Temp",
                    targetEventType: "signal",
                    timer: null,
                    targetCondition: { param: "temp", operator: ">", value: "90", timer: 10 }
                }
            },
            {
                type: "response",
                payload: {
                    name: "StartCoolingMustHappen",
                    activationEvent: "Temp",
                    activationEventType: "signal",
                    targetEvent: "StartCooling",
                    targetEventType: "task",
                    timer: null,
                    activationCondition: { param: "temp", operator: ">", value: "80", timer: 10 },
                    targetCondition: { param: "user", operator: "==", value: "3" }
                }
            },
            {
                type: "precedence",
                payload: {
                    name: "BlockRestart",
                    activationEvent: "Temp",
                    activationEventType: "signal",
                    targetEvent: "Restart",
                    targetEventType: "task",
                    timer: null,
                    activationCondition: { param: "temp", operator: "<", value: "50", timer: 20 }
                }
            }
        ];

        for (const constraint of debugConstraints) {
            try {
                await api.post(
                    `constraints/${constraint.type}`,
                    { json: constraint.payload }
                );
                addToast({
                    title: "Debug Seed Created",
                    description: `Constraint "${constraint.payload.name}" created`,
                    color: "success",
                });
            } catch (error) {
                addToast({
                    title: "Debug Seed Failed",
                    description: `Failed to create "${constraint.payload.name}"`,
                    color: "danger",
                });
            }
        }
        queryClient.invalidateQueries({ queryKey: ["constraints"] });
        queryClient.invalidateQueries({ queryKey: ["analysis"] });
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
        <div className={`flex bg-default-100 h-[36px] dark:bg-default-50 rounded-md text-[10px] p-1 ${disabled ? "opacity-50" : ""}`}>
            <button
                type="button"
                disabled={disabled}
                className={`px-2 py-0.5 rounded transition-colors ${value === "signal"
                    ? "bg-white dark:bg-black font-medium text-foreground shadow-sm"
                    : "text-foreground-500 hover:text-foreground"
                    }`}
                onClick={() => onChange("signal")}
            >
                Sig
            </button>
            <button
                type="button"
                disabled={disabled}
                className={`px-2 py-0.5 rounded transition-colors ${value === "task"
                    ? "bg-white dark:bg-black font-medium text-foreground shadow-sm"
                    : "text-foreground-500 hover:text-foreground"
                    }`}
                onClick={() => onChange("task")}
            >
                Tsk
            </button>
        </div>
    );

    const inputClasses = {
        input: "font-mono text-xs bg-default-100 dark:bg-default-50",
        inputWrapper: "bg-default-100 dark:bg-default-50 shadow-none border-none hover:bg-default-200 h-9 min-h-9"
    };

    return (
        <Card className="border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 border-b border-gray-100 dark:border-gray-800 text-gray-700 dark:text-gray-200">
                Create Constraint
            </CardHeader>
            <CardBody className="p-4">
                <div className="space-y-3">
                    <div className="flex gap-2">
                        <Select
                            aria-label="Constraint Type"
                            placeholder="Type"
                            size="sm"
                            className="w-1/2"
                            classNames={{
                                trigger: "bg-default-100 dark:bg-default-50 shadow-none border-none h-9 min-h-9",
                                value: "text-xs font-mono"
                            }}
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
                            className="w-1/2"
                            classNames={inputClasses}
                            isRequired
                        />
                    </div>

                    {/* Events row */}
                    <div className="flex gap-2 items-center">
                        <div className="flex-1 flex gap-2 items-center">
                            <Input
                                placeholder={needsTarget ? "Activation (A)" : "Event"}
                                value={activationEvent}
                                onChange={(e) => setActivationEvent(e.target.value)}
                                size="sm"
                                className="flex-1"
                                classNames={inputClasses}
                                isRequired
                            />
                            <EventTypeToggle value={activationEventType} onChange={setActivationEventType} />
                        </div>

                        {needsTarget && (
                            <>
                                <span className="text-gray-300">→</span>
                                <div className="flex-1 flex gap-2 items-center">
                                    <Input
                                        placeholder="Target (B)"
                                        value={targetEvent}
                                        onChange={(e) => setTargetEvent(e.target.value)}
                                        size="sm"
                                        className="flex-1"
                                        classNames={inputClasses}
                                        isRequired
                                    />
                                    <EventTypeToggle value={targetEventType} onChange={setTargetEventType} />
                                </div>
                            </>
                        )}
                    </div>

                    {/* Timer + Conditions */}
                    <div className="flex items-center gap-4">
                        <Input
                            placeholder="Constraint Timer (s)"
                            value={timer}
                            onChange={(e) => setTimer(e.target.value)}
                            size="sm"
                            type="number"
                            min={1}
                            className="w-24"
                            classNames={inputClasses}
                        />
                        <Checkbox size="sm" isSelected={showConditions} onValueChange={setShowConditions}>
                            <span className="text-xs text-gray-500">Conditions</span>
                        </Checkbox>
                    </div>

                    {showConditions && (
                        <div className="space-y-2 p-3 bg-default-50 rounded-lg">
                            {/* Activation condition */}
                            <div className="flex gap-2 items-center text-xs">
                                <span className="w-18 text-gray-400 font-mono">Activation</span>
                                <Input
                                    placeholder="param"
                                    value={actParam}
                                    onChange={(e) => setActParam(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                    classNames={inputClasses}
                                />
                                <Select
                                    aria-label="Operator"
                                    placeholder="op"
                                    size="sm"
                                    className="w-20"
                                    classNames={{
                                        trigger: "bg-white dark:bg-black shadow-none border-none h-9 min-h-9",
                                        value: "text-[10px] font-mono"
                                    }}
                                    selectedKeys={actOperator ? [actOperator] : []}
                                    onChange={(e) => setActOperator(e.target.value)}
                                >
                                    <SelectItem key="=">{"="}</SelectItem>
                                    <SelectItem key="!=">{"!="}</SelectItem>
                                    <SelectItem key=">">{">"}</SelectItem>
                                    <SelectItem key="<">{"<"}</SelectItem>
                                </Select>
                                <Input
                                    placeholder="val"
                                    value={actValue}
                                    onChange={(e) => setActValue(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                    classNames={inputClasses}
                                />
                                <Input
                                    placeholder="time (s)"
                                    value={actTimer}
                                    onChange={(e) => setActTimer(e.target.value)}
                                    size="sm"
                                    type="number"
                                    className="w-20"
                                    classNames={inputClasses}
                                />
                            </div>
                            {/* Target condition */}
                            {needsTarget && (
                                <div className="flex gap-2 items-center text-xs">
                                    <span className="w-18 text-gray-400 font-mono">Target</span>
                                    <Input
                                        placeholder="param"
                                        value={tgtParam}
                                        onChange={(e) => setTgtParam(e.target.value)}
                                        size="sm"
                                        className="flex-1"
                                        classNames={inputClasses}
                                    />
                                    <Select
                                        aria-label="Operator"
                                        placeholder="op"
                                        size="sm"
                                        className="w-20"
                                        classNames={{
                                            trigger: "bg-white dark:bg-black shadow-none border-none h-9 min-h-9",
                                            value: "text-[10px] font-mono"
                                        }}
                                        selectedKeys={tgtOperator ? [tgtOperator] : []}
                                        onChange={(e) => setTgtOperator(e.target.value)}
                                    >
                                        <SelectItem key="=">{"="}</SelectItem>
                                        <SelectItem key="!=">{"!="}</SelectItem>
                                        <SelectItem key=">">{">"}</SelectItem>
                                        <SelectItem key="<">{"<"}</SelectItem>
                                    </Select>
                                    <Input
                                        placeholder="val"
                                        value={tgtValue}
                                        onChange={(e) => setTgtValue(e.target.value)}
                                        size="sm"
                                        className="flex-1"
                                        classNames={inputClasses}
                                    />
                                    <Input
                                        placeholder="time (s)"
                                        value={tgtTimer}
                                        onChange={(e) => setTgtTimer(e.target.value)}
                                        size="sm"
                                        type="number"
                                        className="w-20"
                                        classNames={inputClasses}
                                    />
                                </div>
                            )}
                        </div>
                    )}

                    <Button
                        color="primary"
                        variant="flat"
                        size="sm"
                        className="w-full font-medium"
                        isLoading={createConstraintMutation.isPending}
                        onPress={handleCreate}
                    >
                        Create Constraint
                    </Button>
                    <Button
                        color="secondary"
                        variant="ghost"
                        size="sm"
                        className="w-full font-medium"
                        onPress={handleDebugSeed}
                    >
                        Seed Debug Data
                    </Button>
                </div>
            </CardBody>
        </Card>
    );
}
