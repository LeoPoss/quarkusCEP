"use client";

import {
    Accordion,
    AccordionItem,
    Card,
    CardBody,
    CardHeader,
    Chip,
    Spinner,
} from "@heroui/react";
import { ListBulletsIcon } from "@phosphor-icons/react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";
import { useTheme } from "next-themes";

import eql from "../langs/eql.tmLanguage.json";

import { cardHeader } from "./primitives";
import ShikiHighlighter from "react-shiki";

interface EplStatement {
    deploymentId: string;
    statement: string;
    type: string;
}

interface Constraint {
    name: string;
    type: string;
    status: string;
    activationEvent?: { name: string; type: string };
    targetEvent?: { name: string; type: string };
    eplStatements?: EplStatement[];
}

const statusBgColors: Record<string, string> = {
    FULFILLED: "bg-green-50 dark:bg-green-900/30 border-l-4 border-l-green-500",
    INIT: "bg-slate-50 dark:bg-slate-800/50 border-l-4 border-l-slate-400",
    TEMPORARY_VIOLATION: "bg-amber-50 dark:bg-amber-900/30 border-l-4 border-l-amber-500",
    PERMANENT_VIOLATION: "bg-red-50 dark:bg-red-900/30 border-l-4 border-l-red-500",
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
            <Card className="h-full">
                <CardBody className="flex justify-center py-4">
                    <Spinner size="sm" />
                </CardBody>
            </Card>
        );
    }

    if (error) {
        return (
            <Card className="h-full">
                <CardBody className="text-red-500 text-sm">Error: {error.message}</CardBody>
            </Card>
        );
    }

    const formatConstraint = (c: Constraint) => {
        const act = c.activationEvent?.name || "?";
        const tgt = c.targetEvent?.name || "?";
        if (c.type === "EXISTENCE" || c.type === "NOT_EXISTENCE") {
            return tgt;
        }
        return `${act} → ${tgt}`;
    };

    return (
        <Card className="h-full">
            <CardHeader className={cardHeader()}>
                <ListBulletsIcon className="mr-2" size={24} />
                <span className="text-sm">Constraints</span>
                {constraints.length > 0 && (
                    <Chip size="sm" variant="flat" className="ml-auto">{constraints.length}</Chip>
                )}
            </CardHeader>
            <CardBody className="p-2">
                {constraints.length === 0 ? (
                    <div className="text-xs text-gray-500 text-center py-4">No constraints</div>
                ) : (
                    <div className="max-h-48 overflow-y-auto space-y-1 pr-1">
                        <Accordion isCompact selectionMode="multiple" className="gap-1">
                            {constraints.map((c) => (
                                <AccordionItem
                                    key={c.name}
                                    classNames={{
                                        base: `${statusBgColors[c.status] || statusBgColors.INIT} px-2`,
                                        title: "text-xs font-medium",
                                        subtitle: "text-[10px]",
                                        content: "text-xs pt-0 pb-2",
                                        trigger: "py-2",
                                    }}
                                    title={c.name}
                                    subtitle={`${c.type.replace(/_/g, " ")} • ${formatConstraint(c)}`}
                                >
                                    {c.eplStatements && c.eplStatements.length > 0 ? (
                                        <div className="space-y-1">
                                            {c.eplStatements.map((s, i) => (
                                                <div key={i} className="bg-white/50 dark:bg-black/20 p-1.5 rounded text-[10px]">
                                                    <span className="text-gray-500">{s.type}:</span>
                                                    <code className="block whitespace-pre-wrap break-all text-gray-700 dark:text-gray-300 mt-0.5">
                                                        <ShikiHighlighter
                                                            language={eql as any}
                                                            theme="material-theme-darker"
                                                        >
                                                            {s.statement.trim()}
                                                        </ShikiHighlighter>
                                                    </code>
                                                </div>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="text-gray-500">No EPL statements</div>
                                    )}
                                </AccordionItem>
                            ))}
                        </Accordion>
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
