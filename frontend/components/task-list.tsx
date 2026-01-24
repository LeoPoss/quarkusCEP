"use client";

import {
    addToast,
    Button,
    Card,
    CardBody,
    CardHeader,
    Chip,
    Input,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
} from "@heroui/react";
import {
    CheckCircleIcon,
    PlayIcon,
    ProhibitIcon,
    WarningIcon,
    ListChecksIcon,
} from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import ky from "ky";
import { useState } from "react";

import { cardHeader } from "./primitives";

interface TaskAnalysis {
    task: string;
    isUnsafe: boolean;
    unsafeConditions?: Record<string, string>;
}

interface TraceEvent {
    eventType: string;
    payload?: Record<string, string>;
}

const fetchTasks = async (): Promise<TaskAnalysis[]> => {
    return await ky.get("http://localhost:8080/analysis/allowed-tasks").json();
};

const fetchTrace = async (): Promise<TraceEvent[]> => {
    return await ky.get("http://localhost:8080/esper/trace").json();
};

export default function TaskList() {
    const queryClient = useQueryClient();
    const [payloadKey, setPayloadKey] = useState("");
    const [payloadValue, setPayloadValue] = useState("");

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
            return await ky.post("http://localhost:8080/esper/event", {
                json: { eventType: taskName, payload },
            });
        },
        onSuccess: (_, { taskName }) => {
            addToast({
                title: "Task Executed",
                description: `Task "${taskName}" completed`,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            setPayloadKey("");
            setPayloadValue("");
        },
        onError: (error) => {
            addToast({
                title: "Execution Failed",
                description: `Failed: ${error.message}`,
                color: "danger",
            });
        },
    });

    const handleExecute = (taskName: string) => {
        const payload = payloadKey.trim() && payloadValue.trim()
            ? { [payloadKey.trim()]: payloadValue.trim() }
            : undefined;
        executeTaskMutation.mutate({ taskName, payload });
    };

    const isLoading = loadingTasks || loadingTrace;

    if (isLoading) {
        return (
            <Card>
                <CardBody className="flex justify-center py-8">
                    <Spinner />
                </CardBody>
            </Card>
        );
    }

    if (tasksError) {
        return (
            <Card>
                <CardBody className="text-red-500">
                    Error loading tasks: {tasksError.message}
                </CardBody>
            </Card>
        );
    }

    const uniqueTasks = Array.from(new Set(tasks.map((t) => t.task))).sort();

    const getExecutionCount = (taskName: string) =>
        trace.filter((t) => t.eventType === taskName).length;

    const getTaskState = (taskName: string) => {
        const taskAnalysis = tasks.filter((t) => t.task === taskName);
        const hasUnsafe = taskAnalysis.some((t) => t.isUnsafe);
        if (!hasUnsafe) return "READY";
        const hasConditions = taskAnalysis.some(
            (t) => t.isUnsafe && t.unsafeConditions && Object.keys(t.unsafeConditions).length > 0
        );
        if (hasConditions) return "CONDITIONAL";
        return "BLOCKED";
    };

    const getRestrictions = (taskName: string) => {
        const taskAnalysis = tasks.filter((t) => t.task === taskName && t.isUnsafe);
        if (taskAnalysis.length === 0) return null;
        const conditions = taskAnalysis
            .filter((t) => t.unsafeConditions)
            .flatMap((t) => Object.entries(t.unsafeConditions || {}))
            .map(([key, value]) => `${key}=${value}`);
        return conditions.length > 0 ? conditions : ["Constraint violation"];
    };

    const stateConfig: Record<string, { color: "success" | "warning" | "danger"; icon: React.ReactNode; label: string }> = {
        READY: { color: "success", icon: <CheckCircleIcon size={16} weight="fill" />, label: "Ready" },
        BLOCKED: { color: "danger", icon: <ProhibitIcon size={16} weight="fill" />, label: "Blocked" },
        CONDITIONAL: { color: "warning", icon: <WarningIcon size={16} weight="fill" />, label: "Conditional" },
    };

    return (
        <Card>
            <CardHeader className={cardHeader()}>
                <ListChecksIcon className="mr-4" size={32} />
                Tasklist
                {uniqueTasks.length > 0 && (
                    <Chip size="sm" variant="flat" className="ml-2">{uniqueTasks.length}</Chip>
                )}
            </CardHeader>
            <CardBody className="p-0">
                <Table aria-label="Task list" removeWrapper classNames={{ th: "bg-slate-100 dark:bg-slate-800 text-xs" }}>
                    <TableHeader>
                        <TableColumn>Task</TableColumn>
                        <TableColumn>State</TableColumn>
                        <TableColumn>Action</TableColumn>
                        <TableColumn>Restrictions</TableColumn>
                    </TableHeader>
                    <TableBody emptyContent="No tasks. Create constraints to define tasks.">
                        {uniqueTasks.map((taskName) => {
                            const state = getTaskState(taskName);
                            const config = stateConfig[state];
                            const restrictions = getRestrictions(taskName);
                            const isEnabled = state === "READY" || state === "CONDITIONAL";
                            const executionCount = getExecutionCount(taskName);

                            return (
                                <TableRow key={taskName}>
                                    <TableCell>
                                        <div className="flex items-center gap-2">
                                            <span className="font-medium">{taskName}</span>
                                        </div>
                                    </TableCell>
                                    <TableCell>
                                        <Chip color={config.color} variant="flat" size="sm" startContent={config.icon}>
                                            {config.label}
                                        </Chip>
                                    </TableCell>
                                    <TableCell>
                                        {isEnabled ? (
                                            <div className="flex items-center gap-1">
                                                <Input
                                                    size="sm"
                                                    placeholder="key"
                                                    value={payloadKey}
                                                    onChange={(e) => setPayloadKey(e.target.value)}
                                                    className="w-16"
                                                />
                                                <Input
                                                    size="sm"
                                                    placeholder="value"
                                                    value={payloadValue}
                                                    onChange={(e) => setPayloadValue(e.target.value)}
                                                    className="w-20"
                                                />
                                                <Button
                                                    size="sm"
                                                    color="primary"
                                                    variant="flat"
                                                    isIconOnly
                                                    isLoading={executeTaskMutation.isPending && executeTaskMutation.variables?.taskName === taskName}
                                                    onPress={() => handleExecute(taskName)}
                                                >
                                                    <PlayIcon size={16} weight="fill" />
                                                </Button>
                                            </div>
                                        ) : (
                                            <span className="text-gray-400">—</span>
                                        )}
                                    </TableCell>
                                    <TableCell>
                                        {restrictions ? (
                                            <Tooltip content={restrictions.join(", ")} placement="left">
                                                <span className="text-sm text-red-600 dark:text-red-400 cursor-help">
                                                    {restrictions.slice(0, 2).join(", ")}{restrictions.length > 2 && "..."}
                                                </span>
                                            </Tooltip>
                                        ) : (
                                            <span className="text-green-600 dark:text-green-400 text-sm">None</span>
                                        )}
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </CardBody>
        </Card>
    );
}
