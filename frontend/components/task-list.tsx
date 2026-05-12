"use client";

import {
    toast,
    Button,
    Card,
    Input,
    Spinner,
    Table,
    Chip,
} from "@heroui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

interface TaskAnalysis {
    task: string;
    isUnsafe: boolean;
    unsafeConditions?: Record<string, string>;
    violatedBy?: string[];
}

interface TraceEvent {
    eventType: string;
    payload?: Record<string, string>;
    timestamp: number;
}

const fetchTasks = async (): Promise<TaskAnalysis[]> => {
    return await api.get("analysis/allowed-tasks").json();
};

const fetchTrace = async (): Promise<TraceEvent[]> => {
    return await api.get("esper/trace").json();
};

export default function TaskList() {
    const queryClient = useQueryClient();
    const [taskPayloads, setTaskPayloads] = useState<Record<string, { key: string; value: string }>>({});

    const {
        data: tasks = [],
        isLoading: loadingTasks,
        error: tasksError,
    } = useQuery<TaskAnalysis[]>({
        queryKey: ["analysis", "allowed-tasks"],
        queryFn: fetchTasks,
        refetchInterval: 1000,
    });

    const {
        data: trace = [],
        isLoading: loadingTrace,
    } = useQuery<TraceEvent[]>({
        queryKey: ["esper", "trace"],
        queryFn: fetchTrace,
        refetchInterval: 1000,
    });

    const executeTaskMutation = useMutation({
        mutationFn: async ({ taskName, payload }: { taskName: string; payload?: Record<string, string> }) => {
            return await api.post("esper/event", {
                json: { eventType: taskName, payload },
            });
        },
        onSuccess: (_, { taskName }) => {
            toast.success("Executed", {
                description: taskName,
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            setTaskPayloads((prev) => {
                const next = { ...prev };
                delete next[taskName];
                return next;
            });
        },
        onError: (error: any) => {
            toast.danger("Failed", {
                description: error.message,
            });
        },
    });

    const handleExecute = (taskName: string) => {
        const p = taskPayloads[taskName];
        const payload = p?.key?.trim() && p?.value?.trim()
            ? { [p.key.trim()]: p.value.trim() }
            : undefined;
        executeTaskMutation.mutate({ taskName, payload });
    };

    const isLoading = loadingTasks || loadingTrace;

    if (isLoading) {
        return (
            <Card className="h-full">
                <Card.Content className="flex justify-center py-8">
                    <Spinner size="sm" color="accent" />
                </Card.Content>
            </Card>
        );
    }

    if (tasksError) {
        return (
            <Card className="h-full">
                <Card.Content className="text-danger">
                    Error: {tasksError.message}
                </Card.Content>
            </Card>
        );
    }

    const uniqueTasks = Array.from(new Set(tasks.map((t) => t.task))).sort();

    const getTaskState = (taskName: string) => {
        const taskAnalysis = tasks.filter((t) => t.task === taskName);
        const hasUnsafe = taskAnalysis.some((t) => t.isUnsafe);
        return hasUnsafe ? "BLOCKED" : "READY";
    };

    const getRestrictions = (taskName: string): { label: string; tooltip: string; count: number } | null => {
        const taskAnalysis = tasks.filter((t) => t.task === taskName && t.isUnsafe);
        if (taskAnalysis.length === 0) return null;

        const lines: string[] = [];
        for (const t of taskAnalysis) {
            const constraintName = t.violatedBy?.[0] ?? "unknown";
            if (t.unsafeConditions && Object.keys(t.unsafeConditions).length > 0) {
                for (const [param, detail] of Object.entries(t.unsafeConditions)) {
                    lines.push(`${param} ${detail} (from: ${constraintName})`);
                }
            } else {
                lines.push(`Violates constraint: ${constraintName}`);
            }
        }

        return {
            label: lines.length === 1 ? "1 requirement" : `${lines.length} requirements`,
            tooltip: lines.join("\n"),
            count: lines.length,
        };
    };

    return (
        <Card className="h-full">
            <Card.Header>
                <div className="flex items-center justify-between w-full">
                    <Card.Title>Task Overview</Card.Title>
                    <span className="text-xs text-default-500 font-mono">Total: {uniqueTasks.length}</span>
                </div>
            </Card.Header>
            <Card.Content className="p-0 overflow-auto">
                <Table
                    className="min-h-[300px]"
                >
                    <Table.ScrollContainer>
                        <Table.Content aria-label="Task list">
                            <Table.Header>
                                <Table.Column isRowHeader className="bg-transparent border-b border-divider text-xs font-semibold text-default-500 font-mono py-3 px-4">Task</Table.Column>
                                <Table.Column className="bg-transparent border-b border-divider text-xs font-semibold text-default-500 font-mono py-3 px-4">Status</Table.Column>
                                <Table.Column className="bg-transparent border-b border-divider text-xs font-semibold text-default-500 font-mono py-3 px-4">Payload & Action</Table.Column>
                                <Table.Column className="bg-transparent border-b border-divider text-xs font-semibold text-default-500 font-mono py-3 px-4">Requirements</Table.Column>
                            </Table.Header>
                            <Table.Body renderEmptyState={() => (
                                <div className="py-8 text-center">
                                    <p className="text-default-500 text-sm">No tasks defined yet</p>
                                    <p className="text-xs text-default-400 mt-1">Create a constraint with activation/target events to register tasks.</p>
                                </div>
                            )}>
                                {uniqueTasks.map((taskName) => {
                                    const state = getTaskState(taskName);
                                    const restrictions = getRestrictions(taskName);
                                    const isEnabled = state === "READY";

                                    return (
                                        <Table.Row key={taskName} className="hover:bg-default-50 transition-colors">
                                            <Table.Cell className="py-3 border-b border-divider/50 text-sm px-4">
                                                <span className="font-mono font-medium text-foreground">{taskName}</span>
                                            </Table.Cell>
                                            <Table.Cell className="py-3 border-b border-divider/50 text-sm px-4">
                                                <Chip
                                                    variant="soft"
                                                    size="sm"
                                                    color={state === "READY" ? "success" : "danger"}
                                                >
                                                    {state}
                                                </Chip>
                                            </Table.Cell>
                                            <Table.Cell className="py-3 border-b border-divider/50 text-sm px-4">
                                                {isEnabled ? (
                                                    <div className="flex items-center gap-2">
                                                        <Input variant="secondary"
                                                            placeholder="key"
                                                            value={taskPayloads[taskName]?.key ?? ""}
                                                            onChange={(e) =>
                                                                setTaskPayloads((prev) => ({
                                                                    ...prev,
                                                                    [taskName]: { key: e.target.value, value: prev[taskName]?.value ?? "" },
                                                                }))
                                                            }
                                                            className="w-20 font-mono"
                                                        />
                                                        <span className="text-default-300">=</span>
                                                        <Input variant="secondary"
                                                            placeholder="value"
                                                            value={taskPayloads[taskName]?.value ?? ""}
                                                            onChange={(e) =>
                                                                setTaskPayloads((prev) => ({
                                                                    ...prev,
                                                                    [taskName]: { key: prev[taskName]?.key ?? "", value: e.target.value },
                                                                }))
                                                            }
                                                            className="w-20 font-mono"
                                                        />
                                                        <Button
                                                            size="sm"
                                                            variant="primary"
                                                            className="min-w-0 px-4"
                                                            isPending={executeTaskMutation.isPending && executeTaskMutation.variables?.taskName === taskName}
                                                            onPress={() => handleExecute(taskName)}
                                                        >
                                                            {({isPending}) => (
                                                                <>
                                                                    {isPending && <Spinner color="current" size="sm" />}
                                                                    Complete Task
                                                                </>
                                                            )}
                                                        </Button>
                                                    </div>
                                                ) : (
                                                    <span className="text-default-300 italic text-xs pl-2">Unavailable</span>
                                                )}
                                            </Table.Cell>
                                            <Table.Cell className="py-3 border-b border-divider/50 text-sm px-4">
                                                {restrictions ? (
                                                    <div className="text-[10px] font-mono text-danger leading-tight space-y-0.5">
                                                        {restrictions.tooltip.split("\n").map((line, i) => (
                                                            <div key={i}>{line}</div>
                                                        ))}
                                                    </div>
                                                ) : (
                                                    <span className="text-default-300 text-xs pl-2">-</span>
                                                )}
                                            </Table.Cell>
                                        </Table.Row>
                                    );
                                })}
                            </Table.Body>
                        </Table.Content>
                    </Table.ScrollContainer>
                </Table>
            </Card.Content>
        </Card>
    );
}
