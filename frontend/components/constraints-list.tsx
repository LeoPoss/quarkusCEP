"use client";

import {
    Accordion,
    Card,
    Spinner,
} from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import React from "react";
import { useTheme } from "next-themes";
import ShikiHighlighter from "react-shiki";
import { api } from "@/lib/api";

import eql from "../langs/eql.tmLanguage.json";
import {
    CheckCircle,
    Warning,
    XCircle,
    Circle,
} from "@phosphor-icons/react";


export interface Constraint {
    name: string;
    type: string;
    status: string;
    withinPeriod?: number;
    activationEvent?: { name: string; type: string };
    targetEvent?: { name: string; type: string };
    activationCondition?: { param: string; operator: string; value: string; timer?: number };
    targetCondition?: { param: string; operator: string; value: string; timer?: number };
    eplStatements?: { deploymentId: string; statement: string; type: string }[];
}

const statusStyles: Record<string, string> = {
    FULFILLED: "border-l-green-500 bg-green-100 dark:bg-green-900",
    INIT: "border-l-default-300 bg-default-100 dark:bg-default-800",
    TEMPORARY_VIOLATION: "border-l-amber-500 bg-amber-100 dark:bg-amber-900",
    PERMANENT_VIOLATION: "border-l-red-500 bg-red-100 dark:bg-red-900",
};

const fetchConstraints = async (): Promise<Constraint[]> => {
    return await api.get("constraints").json();
};

export default function ConstraintsList() {
    const { data: constraints = [], isLoading, error } = useQuery<Constraint[]>({
        queryKey: ["constraints"],
        queryFn: fetchConstraints,
        refetchInterval: 1000,
    });

    const { resolvedTheme } = useTheme();

    if (isLoading) {
        return (
            <Card className="h-full">
                <Card.Content className="flex justify-center py-4">
                    <Spinner size="sm" color="accent" />
                </Card.Content>
            </Card>
        );
    }

    if (error) {
        return (
            <Card className="h-full">
                <Card.Content className="text-danger text-sm">Error: {error.message}</Card.Content>
            </Card>
        );
    }

    return (
        <Card className="h-full">
            <Card.Header>
                <div className="flex items-center justify-between w-full">
                    <Card.Title>Active Constraints</Card.Title>
                    <span className="text-xs font-mono opacity-60">{constraints.length}</span>
                </div>
            </Card.Header>
            <Card.Content className="p-0">
                {constraints.length === 0 ? (
                    <div className="text-xs text-default-500 text-center py-8">
                        <p className="italic">No constraints defined yet</p>
                        <p className="text-[10px] text-default-400 mt-1">Use the form below to create one.</p>
                    </div>
                ) : (
                    <div className="max-h-48 overflow-y-auto">
                        <Accordion allowsMultipleExpanded className="px-0 gap-0 divide-y divide-divider">
                            {constraints.map((c) => (
                                <Accordion.Item
                                    key={c.name}
                                    id={c.name}
                                    className={`px-4 ${statusStyles[c.status] || statusStyles.INIT}`}
                                >
                                    <Accordion.Heading>
                                        <Accordion.Trigger className="py-3">
                                            <div className="flex items-center gap-3 w-full">
                                                <div className="shrink-0">
                                                    {c.status === "FULFILLED" && <CheckCircle size={14} className="text-green-500" weight="fill" />}
                                                    {c.status === "TEMPORARY_VIOLATION" && <Warning size={14} className="text-amber-500" weight="fill" />}
                                                    {c.status === "PERMANENT_VIOLATION" && <XCircle size={14} className="text-red-500" weight="fill" />}
                                                    {(!c.status || c.status === "INIT") && <Circle size={14} className="text-default-400" />}
                                                </div>
                                                <span className="text-xs font-medium font-mono text-foreground">
                                                    {formatConstraintDisplay(c)}
                                                </span>
                                            </div>
                                            <Accordion.Indicator className="text-default-400 text-sm" />
                                        </Accordion.Trigger>
                                    </Accordion.Heading>
                                    <Accordion.Panel>
                                        <Accordion.Body className="pt-0 pb-3">
                                            <div className="space-y-2 pl-2 border-l border-divider ml-1">
                                                <div className="text-[10px]   text-default-500">
                                                    Status: <span className="font-semibold text-foreground">{c.status}</span>
                                                </div>
                                                {c.eplStatements && c.eplStatements.length > 0 ? (
                                                    <div className="space-y-2">
                                                        {c.eplStatements.map((s, i) => (
                                                            <div key={i}>
                                                                <div className="text-[9px] text-default-400 mb-0.5 font-mono ">{s.type}</div>
                                                                <ShikiHighlighter
                                                                    className="text-[10px] border border-divider rounded overflow-x-auto"
                                                                    language={eql as any}
                                                                    theme={resolvedTheme === "dark" ? "material-theme-darker" : "material-theme-lighter"}
                                                                >
                                                                    {s.statement.trim()}
                                                                </ShikiHighlighter>
                                                            </div>
                                                        ))}
                                                    </div>
                                                ) : (
                                                    <div className="text-default-400 text-[10px] italic">No EPL statements</div>
                                                )}
                                            </div>
                                        </Accordion.Body>
                                    </Accordion.Panel>
                                </Accordion.Item>
                            ))}
                        </Accordion>
                    </div>
                )}
            </Card.Content>
        </Card>
    );
}


function formatConstraintDisplay(c?: Constraint): React.ReactNode {
    if (!c) return "";
    const type = c.type.toUpperCase();

    const formatEvent = (
        evtName?: string,
        evtType?: string,
        cond?: { param: string; operator: string; value: string; timer?: number }
    ) => {
        if (!evtName) return <span className="text-default-400">?</span>;

        const name = evtName;
        // Condition: [param operator value]
        const condition = (cond?.param && cond?.operator && cond?.value)
            ? <span className="text-default-600 dark:text-default-400">[{cond.param} {cond.operator} {cond.value}]</span>
            : null;

        // Timer: [0,t] specialized styling
        const timer = cond?.timer ? (
            <span className="text-xs align-sub ml-0.5 text-default-500">
                [0,{cond.timer}]
            </span>
        ) : null;

        const isTask = evtType?.toLowerCase() === "task";

        if (isTask) {
            return (
                <span className="font-mono">
                    dis({name}{condition}){timer}
                </span>
            )
        }

        return (
            <span className="font-mono">
                {name}
                {condition}
                {timer}
            </span>
        );
    };

    const partA = formatEvent(c.activationEvent?.name, c.activationEvent?.type, c.activationCondition);
    const partB = formatEvent(c.targetEvent?.name, c.targetEvent?.type, c.targetCondition);

    // Parse top-level timer if needed, though user example focused on event timer.
    // Assuming top-level timer is handled similarly if it exists logic, but following user example style primarily.
    const constraintTimer = c.withinPeriod ? (
        <span className="text-xs align-sub ml-0.5 text-default-500">[0,{c.withinPeriod}]</span>
    ) : null;

    // Some constraints only have A (Existence, NotExistence)
    // Note: User wanted "RESPONSE(...)" -> Uppercase
    if (["EXISTENCE", "NOTEXISTENCE", "NOT_EXISTENCE"].includes(type)) {
        const subject = c.targetEvent?.name ? partB : partA;
        return (
            <span>
                {type}{constraintTimer}({subject})
            </span>
        );
    }

    return (
        <span>
            {type}{constraintTimer}({partA}, {partB})
        </span>
    );
}
