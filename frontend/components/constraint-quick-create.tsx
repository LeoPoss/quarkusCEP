"use client";

import {
    toast,
    Button,
    Card,
    Checkbox,
    Input,
    Select,
    ListBox,
    Label,
    Header,
    Separator,
    Spinner,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip,
} from "@heroui/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

const singleEventConstraints = ["existence", "notexistence"];

export default function ConstraintQuickCreate() {
    const queryClient = useQueryClient();
    const [constraintType, setConstraintType] = useState<string | null>(null);
    const [name, setName] = useState("");
    const [activationEvent, setActivationEvent] = useState("");
    const [activationEventType, setActivationEventType] = useState<"task" | "signal">("task");
    const [targetEvent, setTargetEvent] = useState("");
    const [targetEventType, setTargetEventType] = useState<"task" | "signal">("task");
    const [timer, setTimer] = useState("");

    // Conditions
    const [showConditions, setShowConditions] = useState(false);
    const [actParam, setActParam] = useState("");
    const [actOperator, setActOperator] = useState<string | null>(null);
    const [actValue, setActValue] = useState("");
    const [tgtParam, setTgtParam] = useState("");
    const [tgtOperator, setTgtOperator] = useState<string | null>(null);
    const [tgtValue, setTgtValue] = useState("");
    const [actTimer, setActTimer] = useState("");
    const [tgtTimer, setTgtTimer] = useState("");
    const [autoExecute, setAutoExecute] = useState(false);
    const [autoExecutePayload, setAutoExecutePayload] = useState("");

    const createConstraintMutation = useMutation({
        mutationFn: async (payload: Record<string, unknown>) => {
            return await api.post(
                `constraints/${(constraintType as string).toLowerCase()}`,
                { json: payload }
            );
        },
        onSuccess: () => {
            toast.success("Constraint Created", {
                description: `Constraint "${name}" created`,
            });
            queryClient.invalidateQueries({ queryKey: ["constraints"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
        },
        onError: (error: any) => {
            toast.danger("Creation Failed", {
                description: error.message,
            });
        },
    });

    const handleCreate = () => {
        if (!constraintType || !name.trim() || !activationEvent.trim()) {
            toast.warning("Validation Error", {
                description: "All fields required",
            });
            return;
        }

        const needsTarget = !singleEventConstraints.includes(constraintType);
        if (needsTarget && !targetEvent.trim()) {
            toast.warning("Validation Error", {
                description: "Target event required",
            });
            return;
        }
        const isSingleEvent = singleEventConstraints.includes(constraintType);

        const payload: Record<string, unknown> = {
            name: name.trim(),
            activationEvent: isSingleEvent ? null : activationEvent.trim(),
            activationEventType: isSingleEvent ? null : activationEventType,
            targetEvent: isSingleEvent ? activationEvent.trim() : targetEvent.trim(),
            targetEventType: isSingleEvent ? activationEventType : targetEventType,
            timer: timer ? parseInt(timer, 10) : null,
            autoExecute: isSingleEvent ? false : autoExecute,
            autoExecutePayload: (isSingleEvent ? false : autoExecute) ? autoExecutePayload.trim() : null,
        };

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
                    name: "StartCoolingManual",
                    activationEvent: "Temp",
                    activationEventType: "signal",
                    targetEvent: "StartCooling",
                    targetEventType: "task",
                    timer: 10,
                    activationCondition: { param: "temp", operator: ">", value: "80", timer: 10 },
                    targetCondition: { param: "user", operator: "==", value: "3" },
                    autoExecute: false,
                }
            },
            {
                type: "response",
                payload: {
                    name: "StartCoolingAuto",
                    activationEvent: "Temp",
                    activationEventType: "signal",
                    targetEvent: "StartCooling",
                    targetEventType: "task",
                    timer: 20,
                    activationCondition: { param: "temp", operator: ">", value: "80", timer: 10 },
                    targetCondition: { param: "user", operator: "==", value: "3" },
                    autoExecute: true,
                    autoExecutePayload: "temp=${temp}, user=3",
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
                toast.success("Debug Seed Created", {
                    description: `Constraint "${constraint.payload.name}" created`,
                });
            } catch (error: any) {
                toast.danger("Debug Seed Failed", {
                    description: `Failed to create "${constraint.payload.name}": ${error.message}`,
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
        <ToggleButtonGroup
            isDisabled={disabled}
            selectedKeys={[value]}
            onSelectionChange={(keys) => {
                const v = Array.from(keys)[0];
                if (v === "signal" || v === "task") onChange(v);
            }}
            className="shrink-0"
        >
            <ToggleButton id="signal" className="text-xs h-8">Sig</ToggleButton>
            <ToggleButton id="task" className="text-xs h-8">Tsk</ToggleButton>
        </ToggleButtonGroup>
    );

    return (
        <Card>
            <Card.Header>
                <Card.Title>Create Constraint</Card.Title>
            </Card.Header>
            <Card.Content>
                <div className="space-y-3">
                    <div className="flex gap-2">
                        <Select
                            variant="secondary" placeholder="Type"
                            className="w-1/2"
                            selectedKey={constraintType}
                            onSelectionChange={(k) => setConstraintType(k as string)}
                        >
                            <Select.Trigger>
                                <Select.Value className="text-xs font-mono" />
                                <Select.Indicator />
                            </Select.Trigger>
                            <Select.Popover>
                                <ListBox>
                                    <ListBox.Section>
                                        <Header>Existence</Header>
                                        <ListBox.Item id="existence">Existence</ListBox.Item>
                                        <ListBox.Item id="notexistence">NotExistence</ListBox.Item>
                                    </ListBox.Section>
                                    <Separator />
                                    <ListBox.Section>
                                        <Header>Relation</Header>
                                        <ListBox.Item id="response">Response</ListBox.Item>
                                        <ListBox.Item id="precedence">Precedence</ListBox.Item>
                                        <ListBox.Item id="respondedexistence">RespondedExist</ListBox.Item>
                                    </ListBox.Section>
                                    <Separator />
                                    <ListBox.Section>
                                        <Header>Negative</Header>
                                        <ListBox.Item id="notresponse">NotResponse</ListBox.Item>
                                    </ListBox.Section>
                                </ListBox>
                            </Select.Popover>
                        </Select>
                        <Input variant="secondary"
                            placeholder="Name"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className="w-1/2"
                        />
                    </div>

                    <div className={`${needsTarget ? "grid grid-cols-[1fr_auto_1fr] gap-1 items-center" : "flex gap-2 items-center"}`}>
                        <div className="flex gap-1 items-center min-w-0">
                            <Input variant="secondary"
                                placeholder={needsTarget ? "Activation" : "Event"}
                                value={activationEvent}
                                onChange={(e) => setActivationEvent(e.target.value)}
                                className="flex-1 min-w-0"
                            />
                            <EventTypeToggle value={activationEventType} onChange={setActivationEventType} />
                        </div>

                        {needsTarget && (
                            <>
                                <span className="text-default-300">→</span>
                                <div className="flex gap-1 items-center min-w-0">
                                    <Input variant="secondary"
                                        placeholder="Target"
                                        value={targetEvent}
                                        onChange={(e) => setTargetEvent(e.target.value)}
                                        className="flex-1 min-w-0"
                                    />
                                    <EventTypeToggle value={targetEventType} onChange={setTargetEventType} />
                                </div>
                            </>
                        )}
                    </div>

                    <div className="flex items-center gap-4">
                        <Input variant="secondary"
                            placeholder="Timer (s)"
                            value={timer}
                            onChange={(e) => setTimer(e.target.value)}
                            type="number"
                            min={1}
                            className="w-32"
                        />
                        <Checkbox id="show-conditions" variant={"secondary"} isSelected={showConditions} onChange={setShowConditions}>
                            <Checkbox.Control>
                                <Checkbox.Indicator />
                            </Checkbox.Control>
                            <Checkbox.Content>
                                <Label htmlFor="show-conditions" className="text-xs text-default-500">Conditions</Label>
                            </Checkbox.Content>
                        </Checkbox>
                        {needsTarget && (
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Checkbox id="auto-execute" variant={"secondary"} isSelected={autoExecute} onChange={setAutoExecute}>
                                        <Checkbox.Control>
                                            <Checkbox.Indicator />
                                        </Checkbox.Control>
                                        <Checkbox.Content>
                                            <Label htmlFor="auto-execute" className="text-xs text-warning">Auto</Label>
                                        </Checkbox.Content>
                                    </Checkbox>
                                </Tooltip.Trigger>
                                <Tooltip.Content placement="top">
                                    Use this for the system to execute the task, after finishing, the target event is injected.
                                </Tooltip.Content>
                            </Tooltip>
                        )}
                    </div>

                    {needsTarget && autoExecute && (
                        <Input
                            variant="secondary"
                            placeholder="Auto Payload (e.g. temp=${temp}, user=3)"
                            value={autoExecutePayload}
                            onChange={(e) => setAutoExecutePayload(e.target.value)}
                            className="font-mono text-xs w-full"
                        />
                    )}

                    {constraintType && name && activationEvent && (
                        <div className="text-xs font-mono text-default-500 dark:text-default-400 bg-default-50 rounded px-2.5 py-1.5 border border-divider leading-relaxed overflow-x-auto">
                            {constraintType.toUpperCase()}({singleEventConstraints.includes(constraintType)
                                ? `${activationEvent}${actParam ? `[${actParam} ${actOperator} ${actValue}]` : ""}${actTimer ? `[0,${actTimer}]` : ""}`
                                : `${activationEvent}${actParam ? `[${actParam} ${actOperator} ${actValue}]` : ""}${actTimer ? `[0,${actTimer}]` : ""}, ${autoExecute ? "auto(" : "dis("}${targetEvent}${tgtParam ? `[${tgtParam} ${tgtOperator} ${tgtValue}]` : ""}${tgtTimer ? `[0,${tgtTimer}]` : ""})`
                            })
                        </div>
                    )}

                    {showConditions && (
                        <div className="space-y-2 p-3 bg-default-50 rounded-lg">
                            <div className="flex gap-1 items-center">
                                <span className="text-default-400 font-mono text-xs w-24 shrink-0">Activation</span>
                                <Input variant="secondary" placeholder="param" value={actParam} onChange={(e) => setActParam(e.target.value)} className="flex-1 min-w-0" />
                                <Select variant="secondary" placeholder="op" className="w-20 shrink-0" selectedKey={actOperator} onSelectionChange={(k) => setActOperator(k as string)}>
                                    <Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger>
                                    <Select.Popover><ListBox><ListBox.Item id="=">=</ListBox.Item><ListBox.Item id="!=">!=</ListBox.Item><ListBox.Item id=">">{">"}</ListBox.Item><ListBox.Item id="<">{"<"}</ListBox.Item></ListBox></Select.Popover>
                                </Select>
                                <Input variant="secondary" placeholder="val" value={actValue} onChange={(e) => setActValue(e.target.value)} className="w-16 shrink-0" />
                                <Input variant="secondary" placeholder="time (s)" value={actTimer} onChange={(e) => setActTimer(e.target.value)} type="number" className="w-24 shrink-0" />
                            </div>
                            {needsTarget && (
                                <div className="flex gap-1 items-center">
                                    <span className="text-default-400 font-mono text-xs w-24 shrink-0">Target</span>
                                    <Input variant="secondary" placeholder="param" value={tgtParam} onChange={(e) => setTgtParam(e.target.value)} className="flex-1 min-w-0" />
                                    <Select variant="secondary" placeholder="op" className="w-20 shrink-0" selectedKey={tgtOperator} onSelectionChange={(k) => setTgtOperator(k as string)}>
                                        <Select.Trigger><Select.Value /><Select.Indicator /></Select.Trigger>
                                        <Select.Popover><ListBox><ListBox.Item id="=">=</ListBox.Item><ListBox.Item id="!=">!=</ListBox.Item><ListBox.Item id=">">{">"}</ListBox.Item><ListBox.Item id="<">{"<"}</ListBox.Item></ListBox></Select.Popover>
                                    </Select>
                                    <Input variant="secondary" placeholder="val" value={tgtValue} onChange={(e) => setTgtValue(e.target.value)} className="w-16 shrink-0" />
                                    <Input variant="secondary" placeholder="time (s)" value={tgtTimer} onChange={(e) => setTgtTimer(e.target.value)} type="number" className="w-24 shrink-0" />
                                </div>
                            )}
                        </div>
                    )}

                    <Button variant="primary" isPending={createConstraintMutation.isPending} onPress={handleCreate} className="w-full font-medium">
                        {({isPending}) => (
                            <>
                                {isPending && <Spinner color="current" size="sm" />}
                                Create Constraint
                            </>
                        )}
                    </Button>
                    <Button variant="secondary" onPress={handleDebugSeed} className="w-full font-medium">
                        Seed Debug Data
                    </Button>
                </div>
            </Card.Content>
        </Card>
    );
}
