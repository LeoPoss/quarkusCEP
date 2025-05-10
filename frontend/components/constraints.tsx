import { useQuery } from "@tanstack/react-query";
import ky from "ky";
import {
  Accordion,
  AccordionItem,
  Chip,
  Divider,
  Spinner,
  Tooltip,
} from "@heroui/react";
import * as React from "react";
import { LinkBreak } from "@phosphor-icons/react";
import ShikiHighlighter from "react-shiki";
import { useTheme } from "next-themes";

type Constraint = {
  name: string;
  eplStatements: EplStatement[];
  type: string;
  status: string;
  activationEvent: string;
  activationCondition: Condition;
  targetEvent: string;
  targetCondition: Condition;
};

type Condition = {
  param: string;
  operator: string;
  value: string;
};

type EplStatement = {
  deploymentId: string;
  statement: string;
  type: string;
};

function useConstraints() {
  return useQuery({
    refetchInterval: 1000,
    queryKey: ["constraints"],
    queryFn: async () => {
      return await ky<Constraint[]>("http://localhost:8080/constraints").json();
    },
  });
}

const statusChip: Record<
  string,
  | "success"
  | "default"
  | "primary"
  | "secondary"
  | "warning"
  | "danger"
  | undefined
> = {
  TEMPORARY_VIOLATION: "warning",
  PERMANENT_VIOLATION: "danger",
  FULFILLED: "success",
};

export default function Constraints() {
  const { theme, resolvedTheme } = useTheme();
  const constraints = useConstraints();

  return (
    <div>
      {constraints.status === "pending" ? (
        <Spinner />
      ) : constraints.status === "error" ? (
        <span>Error: {constraints.error.message}</span>
      ) : (
        <>
          {constraints.data.length == 0 ? (
            <div className="flex flex-col items-center gap-6">
              <LinkBreak className="text-neutral-300" size={96} />
              <span className="text-sm text-neutral-300">
                No constraints found
              </span>
            </div>
          ) : (
            <Accordion selectionMode="multiple">
              {constraints.data.map((c) => (
                <AccordionItem
                  key={c.name}
                  startContent={
                    <Chip color={statusChip[c.status]}>{c.status}</Chip>
                  }
                  subtitle={formatConstraintDisplay(c)}
                  title={<span className="font-medium">{c.name}</span>}
                >
                  {c.eplStatements.map((s) => (
                    <div
                      key={s.deploymentId}
                      className="bg-neutral-100 dark:bg-neutral-900"
                    >
                      <Divider />
                      <div className="p-2 grid grid-cols-7 gap-4 pl-8 ">
                        <Tooltip content={s.deploymentId}>
                          <Chip color="default" variant="dot">
                            {s.type}
                          </Chip>
                        </Tooltip>
                        <ShikiHighlighter
                          className="col-span-6 text-sm border"
                          language="sql"
                          theme={
                            resolvedTheme === "dark"
                              ? "material-theme-darker"
                              : "material-theme-lighter"
                          }
                        >
                          {s.statement.trim()}
                        </ShikiHighlighter>
                      </div>
                    </div>
                  ))}
                </AccordionItem>
              ))}
            </Accordion>
          )}
        </>
      )}
    </div>
  );
}

function formatConstraintDisplay(c?: Constraint | null): string {
  if (!c) return "";

  const formatCondition = (cond?: Condition | null): string =>
    !cond
      ? ""
      : [cond.param, cond.operator, cond.value]
          .map((p) => p?.trim())
          .filter(Boolean)
          .join(" ");

  const formatEvent = (event?: string | null, condition?: string): string => {
    const name = event?.trim();

    if (!name) return "";

    return condition ? `${name}[${condition}]` : name;
  };

  const type = c.type?.trim() || "";

  const activation = formatEvent(
    c.activationEvent,
    formatCondition(c.activationCondition),
  );
  const target = formatEvent(c.targetEvent, formatCondition(c.targetCondition));

  const events = [activation, target].filter(Boolean).join(", ");

  if (!type) return events;
  if (!events) return type;

  return `${type}(${events})`;
}
