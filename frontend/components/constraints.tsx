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
import { LinkBreakIcon } from "@phosphor-icons/react";
import ShikiHighlighter from "react-shiki";
import { useTheme } from "next-themes";

type Event = {
  name: string;
  type: 'SIGNAL' | 'TASK';
  timer?: number;
};

type Constraint = {
  name: string;
  eplStatements: EplStatement[];
  type: string;
  status: string;
  withinPeriod?: number;
  activationEvent: Event | null;
  activationCondition: Condition | null;
  targetEvent: Event | null;
  targetCondition: Condition | null;
};

type Condition = {
  param: string;
  operator: string;
  value: string;
  timer?: number;
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

const statusCircle: Record<string, string> = {
  TEMPORARY_VIOLATION: "text-red-500",
  PERMANENT_VIOLATION: "text-yellow-500",
  FULFILLED: "text-green-500",
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
              <LinkBreakIcon className="text-neutral-300" size={96} />
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
                    <>
                      {/*<Circle
                        className={statusCircle[c.status]}
                        size={32}
                        weight="duotone"
                      />*/}
                      <Chip color={statusChip[c.status]}>{c.status}</Chip>
                    </>
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
                      <div className="p-2 grid grid-cols-4 gap-4 pl-8 ">
                        <Tooltip content={s.deploymentId}>
                          <Chip color="default" variant="dot">
                            {s.type}
                          </Chip>
                        </Tooltip>
                        <ShikiHighlighter
                          className="col-span-3 text-sm border"
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

function formatConstraintDisplay(c?: Constraint | null): React.ReactNode {
  if (!c) return "";

  const formatCondition = (cond?: Condition | null): string =>
    !cond
      ? ""
      : [cond.param, cond.operator, cond.value]
          .map((p) => p?.trim())
          .filter(Boolean)
          .join(" ");

  const formatEvent = (event?: Event | null, condition?: string, timer?: number): React.ReactNode => {
    if (!event?.name) return "";
    
    const name = event.name.trim();
    const type = event.type ? `:${event.type.toLowerCase()}` : '';
    const conditionPart = condition ? `[${condition}]` : '';
    
    return (
      <>
        {name}{conditionPart}{type}
      </>
    );
  };
  
  const formatTimer = (time: number) => (
    <span className="text-xs align-sub">[0,{time}]</span>
  );

  const type = c.type?.trim() || "";
  const withinPeriod = c.withinPeriod;
  const targetTimer = c.targetCondition?.timer;

  const activation = formatEvent(
    c.activationEvent,
    formatCondition(c.activationCondition)
  );
  
  const target = formatEvent(
    c.targetEvent, 
    formatCondition(c.targetCondition)
  );

  const events = [activation, target].filter(Boolean);
  const formattedEvents = [];
  
  // Add activation event if exists
  if (activation) {
    formattedEvents.push(activation);
  }
  
  // Add target event with its timer if exists
  if (target) {
    formattedEvents.push(target);
    if (targetTimer !== undefined) {
      formattedEvents.push(formatTimer(targetTimer));
    }
  }

  if (!type) return <>{formattedEvents.length > 0 ? formattedEvents : ''}</>;
  if (formattedEvents.length === 0) return <>{type || ''}</>;

  const timerSuffix = withinPeriod !== undefined ? formatTimer(withinPeriod) : null;

  return (
    <span>
      {type}{timerSuffix}({formattedEvents.reduce((result: React.ReactNode[], event, index, array) => {
        result.push(event);
        if (index < array.length - 1 && !React.isValidElement(event)) {
          result.push(", ");
        }
        return result;
      }, [])})
    </span>
  );
}
