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
import ky from "ky";
import { formatConstraint, Constraint } from "../utils/constraint-formatter";


const statusStyles: Record<string, string> = {
    FULFILLED: "border-l-green-500 bg-green-100 dark:bg-green-900",
    INIT: "border-l-gray-300 bg-gray-100 dark:bg-gray-800",
    TEMPORARY_VIOLATION: "border-l-amber-500 bg-amber-100 dark:bg-amber-900",
    PERMANENT_VIOLATION: "border-l-red-500 bg-red-100 dark:bg-red-900",
};

const fetchConstraints = async (): Promise<Constraint[]> => {
    return await ky.get("http://localhost:8080/constraints").json();
};

export default function ConstraintsList() {
    const { data: constraints = [], isLoading, error } = useQuery<Constraint[]>({
        queryKey: ["constraints"],
        queryFn: fetchConstraints,
        refetchInterval: 1000,
    });

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

    const formatConstraintText = (c: Constraint) => {
        return formatConstraint(c);
    };

    return (
        <Card className="h-full border-none shadow-sm">
            <CardHeader className="text-sm font-medium px-4 py-3 bg-gradient-to-b from-gray-50/80 to-gray-100/50 dark:from-gray-800/80 dark:to-gray-800/50 text-gray-700 dark:text-gray-200 flex justify-between items-center">
                <span>Active Constraints</span>
                <span className="text-xs font-mono opacity-60">{constraints.length}</span>
            </CardHeader>
            <CardBody className="p-0">
                {constraints.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-8 italic">No constraints defined</div>
                ) : (
                    <div className="max-h-48 overflow-y-auto">
                        <Accordion isCompact selectionMode="multiple" className="px-0 gap-0 divider-y divide-gray-100 dark:divide-gray-800">
                            {constraints.map((c) => (
                                <AccordionItem
                                    key={c.name}
                                    classNames={{
                                        base: `px-4 ${statusStyles[c.status] || statusStyles.INIT}`,
                                        title: "text-xs font-medium font-mono text-gray-800 dark:text-gray-200",
                                        subtitle: "hidden",
                                        content: "pt-0 pb-3",
                                        trigger: "py-3",
                                        indicator: "text-gray-400 text-small",
                                    }}
                                    title={`${c.name}: ${formatConstraintText(c)}`}
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
                                                        <pre className="text-[10px] bg-white dark:bg-black p-2 rounded border border-gray-100 dark:border-gray-800 overflow-x-auto whitespace-pre-wrap break-all text-gray-600 dark:text-gray-400 font-mono leading-relaxed">
                                                            {s.statement.trim()}
                                                        </pre>
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
