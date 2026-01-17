"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import ky from "ky";
import {
    addToast,
    Button,
    Card,
    CardBody,
    CardHeader,
    Chip,
    Divider,
    Input,
    Select,
    SelectItem,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
} from "@heroui/react";
import * as React from "react";
import { useState, useMemo } from "react";
import {
    ArrowRightIcon,
    CheckCircleIcon,
    CircleIcon,
    ClockIcon,
    FunnelIcon,
    InfoIcon,
    ListChecksIcon,
    MagnifyingGlassIcon,
    PlayIcon,
    ProhibitIcon,
    WarningCircleIcon,
    XCircleIcon,
} from "@phosphor-icons/react";

interface TaskAnalysis {
    task: string;
    isUnsafe: boolean;
    unsafeConditions?: Record<string, string>;
}

interface FinishabilityResponse {
    canFinish: boolean;
    reasons: string[];
}

const fetchTasks = async (): Promise<TaskAnalysis[]> => {
    return await ky.get("http://localhost:8080/analysis/allowed-tasks").json();
};

const fetchFinishability = async (): Promise<FinishabilityResponse> => {
    return await ky.get("http://localhost:8080/analysis/finishability").json();
};

type FilterType = "all" | "available" | "restricted";
type SortType = "name" | "status";

export default function TaskListDashboard() {
    const [searchQuery, setSearchQuery] = useState("");
    const [filter, setFilter] = useState<FilterType>("all");
    const [sortBy, setSortBy] = useState<SortType>("status");
    const queryClient = useQueryClient();

    const {
        data: tasks = [],
        isLoading: isLoadingTasks,
        error: tasksError,
    } = useQuery<TaskAnalysis[]>({
        queryKey: ["analysis", "allowed-tasks"],
        queryFn: fetchTasks,
        refetchInterval: 1000,
    });

    const {
        data: finishability,
        isLoading: isLoadingFinishability,
    } = useQuery<FinishabilityResponse>({
        queryKey: ["analysis", "finishability"],
        queryFn: fetchFinishability,
        refetchInterval: 1000,
    });

    const completeTaskMutation = useMutation({
        mutationFn: async (taskName: string) => {
            return await ky.post("http://localhost:8080/esper/event", {
                json: { eventType: taskName },
            });
        },
        onSuccess: (_, taskName) => {
            addToast({
                title: "Task Executed",
                description: `Task "${taskName}" has been successfully executed.`,
                color: "success",
            });
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            queryClient.invalidateQueries({ queryKey: ["constraints"] });
        },
        onError: (error, taskName) => {
            addToast({
                title: "Execution Failed",
                description: `Failed to execute "${taskName}": ${error.message}`,
                color: "danger",
            });
        },
    });

    // Consolidate tasks by name (since API can return duplicates with different conditions)
    const consolidatedTasks = useMemo(() => {
        const taskMap = new Map<string, TaskAnalysis>();

        tasks.forEach((task) => {
            const existing = taskMap.get(task.task);
            if (!existing) {
                taskMap.set(task.task, { ...task });
            } else if (task.isUnsafe) {
                existing.isUnsafe = true;
                existing.unsafeConditions = {
                    ...(existing.unsafeConditions || {}),
                    ...(task.unsafeConditions || {}),
                };
            }
        });

        return Array.from(taskMap.values());
    }, [tasks]);

    // Filter and sort tasks
    const filteredTasks = useMemo(() => {
        let result = consolidatedTasks;

        if (searchQuery.trim()) {
            const query = searchQuery.toLowerCase();
            result = result.filter((task) =>
                task.task.toLowerCase().includes(query)
            );
        }

        if (filter === "available") {
            result = result.filter((task) => !task.isUnsafe);
        } else if (filter === "restricted") {
            result = result.filter((task) => task.isUnsafe);
        }

        result = [...result].sort((a, b) => {
            if (sortBy === "name") {
                return a.task.localeCompare(b.task);
            } else {
                if (a.isUnsafe === b.isUnsafe) {
                    return a.task.localeCompare(b.task);
                }
                return a.isUnsafe ? 1 : -1;
            }
        });

        return result;
    }, [consolidatedTasks, searchQuery, filter, sortBy]);

    const availableCount = consolidatedTasks.filter((t) => !t.isUnsafe).length;
    const restrictedCount = consolidatedTasks.filter((t) => t.isUnsafe).length;

    if (isLoadingTasks) {
        return (
            <div className="flex flex-col justify-center items-center min-h-[400px] gap-4">
                <Spinner size="lg" color="default" />
                <p className="text-sm text-slate-500 dark:text-slate-400">Loading process state...</p>
            </div>
        );
    }

    if (tasksError) {
        return (
            <Card className="border border-red-200 dark:border-red-900/50 bg-red-50/50 dark:bg-red-950/20">
                <CardBody className="py-8">
                    <div className="flex flex-col items-center gap-3 text-center">
                        <div className="p-3 rounded-full bg-red-100 dark:bg-red-900/30">
                            <XCircleIcon size={28} weight="fill" className="text-red-600 dark:text-red-400" />
                        </div>
                        <div>
                            <p className="font-semibold text-red-700 dark:text-red-300">Connection Error</p>
                            <p className="text-sm text-red-600/80 dark:text-red-400/80 mt-1">
                                Unable to retrieve process state: {tasksError.message}
                            </p>
                        </div>
                    </div>
                </CardBody>
            </Card>
        );
    }

    return (
        <div className="space-y-6 max-w-6xl mx-auto">
            {/* Academic Header */}
            <div className="border-b border-slate-200 dark:border-slate-700 pb-6">
                <div className="flex items-start justify-between">
                    <div>
                        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100 tracking-tight">
                            Process Task Analysis
                        </h1>
                        <p className="text-slate-500 dark:text-slate-400 mt-1 text-sm">
                            Declarative Process Mining — Available Task Enumeration
                        </p>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-400 dark:text-slate-500">
                        <ClockIcon size={14} />
                        <span>Auto-refresh: 1s</span>
                    </div>
                </div>
            </div>

            {/* Process State Summary */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                {/* Finishability Status */}
                <Card className={`col-span-1 lg:col-span-2 border ${finishability?.canFinish
                        ? "border-emerald-200 dark:border-emerald-800/50 bg-emerald-50/30 dark:bg-emerald-950/20"
                        : "border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-800/30"
                    }`}>
                    <CardBody className="py-5 px-6">
                        <div className="flex items-start gap-4">
                            <div className={`p-2.5 rounded-lg ${finishability?.canFinish
                                    ? "bg-emerald-100 dark:bg-emerald-900/40"
                                    : "bg-slate-100 dark:bg-slate-700"
                                }`}>
                                {finishability?.canFinish ? (
                                    <CheckCircleIcon
                                        size={24}
                                        weight="fill"
                                        className="text-emerald-600 dark:text-emerald-400"
                                    />
                                ) : (
                                    <ClockIcon
                                        size={24}
                                        weight="fill"
                                        className="text-slate-500 dark:text-slate-400"
                                    />
                                )}
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2">
                                    <h3 className="font-medium text-slate-800 dark:text-slate-200">
                                        Process Finishability
                                    </h3>
                                    <Chip
                                        size="sm"
                                        variant="flat"
                                        color={finishability?.canFinish ? "success" : "default"}
                                        className="h-5"
                                    >
                                        {finishability?.canFinish ? "Satisfiable" : "Pending"}
                                    </Chip>
                                </div>
                                {finishability?.canFinish ? (
                                    <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
                                        All mandatory constraints can be satisfied. The process may terminate successfully.
                                    </p>
                                ) : (
                                    <div className="mt-2">
                                        {finishability?.reasons && finishability.reasons.length > 0 ? (
                                            <div className="space-y-1.5">
                                                <p className="text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wide">
                                                    Outstanding Requirements
                                                </p>
                                                <ul className="space-y-1">
                                                    {finishability.reasons.map((reason, idx) => (
                                                        <li
                                                            key={idx}
                                                            className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-400"
                                                        >
                                                            <ArrowRightIcon size={12} className="mt-1.5 shrink-0 text-slate-400" />
                                                            <span className="font-mono text-xs bg-slate-100 dark:bg-slate-700 px-2 py-1 rounded">
                                                                {reason}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            </div>
                                        ) : (
                                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                                Awaiting additional task completions to satisfy constraints.
                                            </p>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    </CardBody>
                </Card>

                {/* Task Statistics */}
                <Card className="border border-slate-200 dark:border-slate-700">
                    <CardBody className="py-4 px-5">
                        <h3 className="text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wide mb-3">
                            Task Distribution
                        </h3>
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-slate-400" />
                                    <span className="text-sm text-slate-600 dark:text-slate-400">Total</span>
                                </div>
                                <span className="text-lg font-semibold text-slate-800 dark:text-slate-200">
                                    {consolidatedTasks.length}
                                </span>
                            </div>
                            <Divider className="my-2" />
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-emerald-500" />
                                    <span className="text-sm text-slate-600 dark:text-slate-400">Enabled</span>
                                </div>
                                <span className="text-lg font-semibold text-emerald-600 dark:text-emerald-400">
                                    {availableCount}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-amber-500" />
                                    <span className="text-sm text-slate-600 dark:text-slate-400">Blocked</span>
                                </div>
                                <span className="text-lg font-semibold text-amber-600 dark:text-amber-400">
                                    {restrictedCount}
                                </span>
                            </div>
                        </div>
                    </CardBody>
                </Card>
            </div>

            {/* Task List Section */}
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <h2 className="text-lg font-medium text-slate-800 dark:text-slate-200">
                        Available Tasks
                    </h2>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                        Showing {filteredTasks.length} of {consolidatedTasks.length} tasks
                    </p>
                </div>

                {/* Filters */}
                <Card className="border border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-800/30">
                    <CardBody className="py-3 px-4">
                        <div className="flex flex-wrap items-center gap-3">
                            <Input
                                className="max-w-xs"
                                placeholder="Search by task name..."
                                size="sm"
                                variant="bordered"
                                startContent={<MagnifyingGlassIcon className="text-slate-400" size={16} />}
                                value={searchQuery}
                                onValueChange={setSearchQuery}
                                classNames={{
                                    inputWrapper: "bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-600",
                                }}
                            />
                            <Divider orientation="vertical" className="h-6" />
                            <div className="flex items-center gap-2">
                                <span className="text-xs text-slate-500 dark:text-slate-400">Filter:</span>
                                <Select
                                    className="w-32"
                                    size="sm"
                                    variant="bordered"
                                    selectedKeys={[filter]}
                                    onSelectionChange={(keys) => setFilter(Array.from(keys)[0] as FilterType)}
                                    aria-label="Filter tasks"
                                    classNames={{
                                        trigger: "bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-600 h-8 min-h-8",
                                    }}
                                >
                                    <SelectItem key="all">All</SelectItem>
                                    <SelectItem key="available">Enabled</SelectItem>
                                    <SelectItem key="restricted">Blocked</SelectItem>
                                </Select>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-xs text-slate-500 dark:text-slate-400">Sort:</span>
                                <Select
                                    className="w-32"
                                    size="sm"
                                    variant="bordered"
                                    selectedKeys={[sortBy]}
                                    onSelectionChange={(keys) => setSortBy(Array.from(keys)[0] as SortType)}
                                    aria-label="Sort tasks"
                                    classNames={{
                                        trigger: "bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-600 h-8 min-h-8",
                                    }}
                                >
                                    <SelectItem key="status">By Status</SelectItem>
                                    <SelectItem key="name">By Name</SelectItem>
                                </Select>
                            </div>
                        </div>
                    </CardBody>
                </Card>

                {/* Task Table */}
                <Card className="border border-slate-200 dark:border-slate-700 overflow-hidden">
                    {filteredTasks.length === 0 ? (
                        <CardBody className="py-16">
                            <div className="flex flex-col items-center justify-center text-center">
                                <div className="p-4 rounded-full bg-slate-100 dark:bg-slate-800 mb-4">
                                    <ListChecksIcon size={32} className="text-slate-400" />
                                </div>
                                <p className="font-medium text-slate-600 dark:text-slate-300">No Tasks Found</p>
                                <p className="text-sm text-slate-400 dark:text-slate-500 mt-1 max-w-xs">
                                    {searchQuery
                                        ? "No tasks match your search criteria. Try adjusting the filters."
                                        : "No tasks are currently available in the process model."}
                                </p>
                            </div>
                        </CardBody>
                    ) : (
                        <Table
                            aria-label="Process task list"
                            removeWrapper
                            classNames={{
                                base: "min-h-[200px]",
                                th: "bg-slate-50 dark:bg-slate-800/80 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 py-3 px-4 first:pl-6 last:pr-6",
                                td: "py-3.5 px-4 first:pl-6 last:pr-6 border-b border-slate-100 dark:border-slate-800",
                                tr: "hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors",
                            }}
                        >
                            <TableHeader>
                                <TableColumn>Task Identifier</TableColumn>
                                <TableColumn width={120}>Status</TableColumn>
                                <TableColumn>Constraint Violations</TableColumn>
                                <TableColumn width={120}>Action</TableColumn>
                            </TableHeader>
                            <TableBody>
                                {filteredTasks.map((task) => (
                                    <TableRow key={task.task}>
                                        <TableCell>
                                            <div className="flex items-center gap-3">
                                                <div className={`w-1.5 h-1.5 rounded-full ${task.isUnsafe ? 'bg-amber-500' : 'bg-emerald-500'
                                                    }`} />
                                                <span className="font-mono text-sm text-slate-700 dark:text-slate-300">
                                                    {task.task}
                                                </span>
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            {task.isUnsafe ? (
                                                <div className="flex items-center gap-1.5 text-amber-600 dark:text-amber-400">
                                                    <ProhibitIcon size={14} weight="bold" />
                                                    <span className="text-xs font-medium">Blocked</span>
                                                </div>
                                            ) : (
                                                <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
                                                    <CheckCircleIcon size={14} weight="fill" />
                                                    <span className="text-xs font-medium">Enabled</span>
                                                </div>
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            {task.unsafeConditions && Object.keys(task.unsafeConditions).length > 0 ? (
                                                <div className="flex flex-wrap gap-1.5">
                                                    {Object.entries(task.unsafeConditions).map(([param, value]) => (
                                                        <Tooltip
                                                            key={param}
                                                            content={
                                                                <div className="text-xs">
                                                                    <span className="text-slate-400">Constraint:</span>{" "}
                                                                    <span className="font-mono">{param} = "{value}"</span>
                                                                </div>
                                                            }
                                                        >
                                                            <Chip
                                                                size="sm"
                                                                variant="flat"
                                                                className="text-xs h-5 bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 border-0"
                                                            >
                                                                {param}
                                                            </Chip>
                                                        </Tooltip>
                                                    ))}
                                                </div>
                                            ) : (
                                                <span className="text-xs text-slate-400 dark:text-slate-500 italic">
                                                    None
                                                </span>
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            <Button
                                                size="sm"
                                                color={task.isUnsafe ? "default" : "primary"}
                                                variant={task.isUnsafe ? "flat" : "solid"}
                                                isDisabled={task.isUnsafe || completeTaskMutation.isPending}
                                                isLoading={completeTaskMutation.isPending && completeTaskMutation.variables === task.task}
                                                startContent={!completeTaskMutation.isPending && <PlayIcon size={14} weight="fill" />}
                                                onPress={() => completeTaskMutation.mutate(task.task)}
                                                className={`h-7 text-xs font-medium ${task.isUnsafe
                                                        ? "bg-slate-100 dark:bg-slate-700 text-slate-400"
                                                        : ""
                                                    }`}
                                            >
                                                Execute
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </Card>

                {/* Footer Note */}
                <div className="flex items-start gap-2 text-xs text-slate-400 dark:text-slate-500 px-1">
                    <InfoIcon size={14} className="mt-0.5 shrink-0" />
                    <p>
                        Tasks are derived from the declarative process model. Blocked tasks violate one or more
                        constraints and cannot be executed until the constraint conditions are satisfied.
                    </p>
                </div>
            </div>
        </div>
    );
}
