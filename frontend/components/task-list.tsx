"use client";

import {
    addToast,
    Button,
    Card,
    CardBody,
    CardHeader,
    Input,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
    Chip,
} from "@heroui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";

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
    return await api.get("analysis/allowed-tasks").json();
};

const fetchTrace = async (): Promise<TraceEvent[]> => {
    return await api.get("esper/trace").json();
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
            return await api.post("esper/event", {
                json: { eventType: taskName, payload },
            });
        },
        onSuccess: (_, { taskName }) => {
            addToast({
                title: "Executed",
                description: taskName,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["esper", "trace"] });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            setPayloadKey("");
            setPayloadValue("");
        },
        onError: (error) => {
            addToast({
                title: "Failed",
                description: error.message,
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
            <Card className="border-none shadow-sm h-full">
                <CardBody className="flex justify-center py-8">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    if (tasksError) {
        return (
            <Card className="border-none shadow-sm h-full">
                <CardBody className="text-red-600">
                    Error: {tasksError.message}
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

    return (
        <Card className="border-none shadow-sm h-full">
            <CardHeader className="text-sm font-medium px-4 py-3 border-b border-gray-100 dark:border-gray-800 text-gray-700 dark:text-gray-200 flex justify-between items-center">
                <span>Task Overview</span>
                <span className="text-xs text-gray-500 font-mono">Total: {uniqueTasks.length}</span>
            </CardHeader>
            <CardBody className="p-0 overflow-auto">
                <Table
                    aria-label="Task list"
                    removeWrapper
                    classNames={{
                        th: "bg-transparent border-b border-gray-100 dark:border-gray-800 text-xs font-semibold text-gray-500 dark:text-gray-400 font-mono py-3 px-4",
                        td: "py-3 border-b border-gray-50 dark:border-gray-800/50 text-sm px-4",
                        base: "min-h-[300px]"
                    }}
                >
                    <TableHeader>
                        <TableColumn>Task</TableColumn>
                        <TableColumn>Status</TableColumn>
                        <TableColumn>Payload & Action</TableColumn>
                        <TableColumn>Requirements</TableColumn>
                    </TableHeader>
                    <TableBody emptyContent={
                        <div className="py-8 text-center">
                            <p className="text-gray-500 text-sm">No tasks defined yet</p>
                            <p className="text-xs text-gray-400 mt-1">Create a constraint with activation/target events to register tasks.</p>
                        </div>
                    }>
                        {uniqueTasks.map((taskName) => {
                            const state = getTaskState(taskName);
                            const restrictions = getRestrictions(taskName);
                            const isEnabled = state === "READY" || state === "CONDITIONAL";

                            return (
                                <TableRow key={taskName} className="hover:bg-gray-50/50 dark:hover:bg-gray-800/30 transition-colors">
                                    <TableCell>
                                        <span className="font-mono font-medium text-gray-800 dark:text-gray-200">{taskName}</span>
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            size="sm"
                                            variant="flat"
                                            color={state === "READY" ? "success" : state === "CONDITIONAL" ? "warning" : "danger"}
                                            classNames={{ content: "text-[10px] font-mono font-medium" }}
                                        >
                                            {state}
                                        </Chip>
                                    </TableCell>
                                    <TableCell>
                                        {isEnabled ? (
                                            <div className="flex items-center gap-2">
                                                <Input
                                                    size="sm"
                                                    placeholder="key"
                                                    value={payloadKey}
                                                    onChange={(e) => setPayloadKey(e.target.value)}
                                                    className="w-20"
                                                    classNames={{
                                                        input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                                                        inputWrapper: "h-8 min-h-8 bg-default-100 dark:bg-default-50 shadow-none border-none"
                                                    }}
                                                />
                                                <span className="text-gray-300">=</span>
                                                <Input
                                                    size="sm"
                                                    placeholder="value"
                                                    value={payloadValue}
                                                    onChange={(e) => setPayloadValue(e.target.value)}
                                                    className="w-20"
                                                    classNames={{
                                                        input: "font-mono text-xs bg-default-100 dark:bg-default-50",
                                                        inputWrapper: "h-8 min-h-8 bg-default-100 dark:bg-default-50 shadow-none border-none"
                                                    }}
                                                />
                                                <Button
                                                    size="sm"
                                                    variant="flat"
                                                    className="min-w-0 px-4 h-8 bg-gray-900 dark:bg-gray-100 text-white dark:text-black font-medium text-xs rounded shadow-none hover:opacity-90"
                                                    isLoading={executeTaskMutation.isPending && executeTaskMutation.variables?.taskName === taskName}
                                                    onPress={() => handleExecute(taskName)}
                                                >
                                                    Finish
                                                </Button>
                                            </div>
                                        ) : (
                                            <span className="text-gray-300 dark:text-gray-700 italic text-xs pl-2">Unavailable</span>
                                        )}
                                    </TableCell>
                                    <TableCell>
                                        {restrictions ? (
                                            <Tooltip content={restrictions.join(", ")} placement="left">
                                                <span className="text-xs font-mono text-red-600 dark:text-red-400 cursor-help border-b border-dotted border-red-300 dark:border-red-700 hover:border-solid">
                                                    {restrictions.length} condition{restrictions.length > 1 ? 's' : ''}
                                                </span>
                                            </Tooltip>
                                        ) : (
                                            <span className="text-gray-300 text-xs pl-2">-</span>
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
