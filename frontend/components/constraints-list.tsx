"use client";

import {
    Accordion,
    AccordionItem,
    Card,
    CardBody,
    CardHeader,
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
    timer?: number;
    activationEvent?: { name: string; type: string };
    targetEvent?: { name: string; type: string };
    activationCondition?: { param: string; operator: string; value: string; timer?: number };
    targetCondition?: { param: string; operator: string; value: string; timer?: number };
    eplStatements?: { deploymentId: string; statement: string; type: string }[];
}

type Condition = {
    param: string;
    operator: string;
    value: string;
    timer?: number;
};


const statusStyles: Record<string, string> = {
    FULFILLED: "border-l-green-500 bg-green-100 dark:bg-green-900",
    INIT: "border-l-gray-300 bg-gray-100 dark:bg-gray-800",
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
            <Card className="h-full border-none shadow-sm">
                <CardBody className="flex justify-center py-4">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    if (error) {
        return (
            <Card className="h-full border-none shadow-sm">
                <CardBody className="text-red-500 text-sm">Error: {error.message}</CardBody>
            </Card>
        );
    }

    return (
        <Card className="h-full border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 border-b border-gray-100 dark:border-gray-800 text-gray-700 dark:text-gray-200 flex justify-between items-center">
                <span>Active Constraints</span>
                <span className="text-xs font-mono opacity-60">{constraints.length}</span>
            </CardHeader>
            <CardBody className="p-0">
                {constraints.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-8">
                        <p className="italic">No constraints defined yet</p>
                        <p className="text-[10px] text-gray-400 mt-1">Use the form below to create one.</p>
                    </div>
                ) : (
                    <div className="max-h-48 overflow-y-auto">
                        <Accordion isCompact selectionMode="multiple" className="px-0 gap-0 divider-y divide-gray-100 dark:divide-gray-800">
                            {constraints.map((c) => (
                                <AccordionItem
                                    key={c.name}
                                    classNames={{
                                        base: `px-4 ${statusStyles[c.status] || statusStyles.INIT}`,
                                        title: "text-xs font-medium font-mono text-gray-800 dark:text-gray-200",
                                        content: "pt-0 pb-3",
                                        trigger: "py-3",
                                        indicator: "text-gray-400 text-small",
                                    }}
                                    title={formatConstraintDisplay(c)}
                                    startContent={
                                        <div className="shrink-0">
                                            {c.status === "FULFILLED" && <CheckCircle size={14} className="text-green-500" weight="fill" />}
                                            {c.status === "TEMPORARY_VIOLATION" && <Warning size={14} className="text-amber-500" weight="fill" />}
                                            {c.status === "PERMANENT_VIOLATION" && <XCircle size={14} className="text-red-500" weight="fill" />}
                                            {(!c.status || c.status === "INIT") && <Circle size={14} className="text-gray-400" />}
                                        </div>
                                    }
                                >
                                    <div className="space-y-2 pl-2 border-l border-gray-200 dark:border-gray-700 ml-1">
                                        <div className="text-[10px] uppercase tracking-wider text-gray-500">
                                            Status: <span className="font-semibold text-gray-700 dark:text-gray-300">{c.status}</span>
                                        </div>
                                        {c.eplStatements && c.eplStatements.length > 0 ? (
                                            <div className="space-y-2">
                                                {c.eplStatements.map((s, i) => (
                                                    <div key={i}>
                                                        <div className="text-[9px] text-gray-400 mb-0.5 font-mono uppercase">{s.type}</div>
                                                        <ShikiHighlighter
                                                            className="text-[10px] border border-gray-100 dark:border-gray-800 rounded overflow-x-auto"
                                                            language={eql as any}
                                                            theme={resolvedTheme === "dark" ? "material-theme-darker" : "material-theme-lighter"}
                                                        >
                                                            {s.statement.trim()}
                                                        </ShikiHighlighter>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="text-gray-400 text-[10px] italic">No EPL statements</div>
                                        )}
                                    </div>
                                </AccordionItem>
                            ))}
                        </Accordion>
                    </div>
                )}
            </CardBody>
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
        if (!evtName) return <span className="text-gray-400">?</span>;

        const name = evtName;
        // Condition: [param operator value]
        const condition = (cond?.param && cond?.operator && cond?.value)
            ? <span className="text-gray-600 dark:text-gray-400">[{cond.param} {cond.operator} {cond.value}]</span>
            : null;

        // Timer: [0,t] specialized styling
        const timer = cond?.timer ? (
            <span className="text-xs align-sub ml-0.5 text-gray-500">
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
    const constraintTimer = c.timer ? (
        <span className="text-xs align-sub ml-0.5 text-gray-500">[0,{c.timer}]</span>
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
