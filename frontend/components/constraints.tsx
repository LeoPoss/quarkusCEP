import { useQuery } from "@tanstack/react-query";
import ky from "ky";
import {
  Accordion,
  AccordionItem,
  Chip,
  Spinner,
  Tooltip,
} from "@heroui/react";
import * as React from "react";
import { LinkBreak } from "@phosphor-icons/react";
import ShikiHighlighter from "react-shiki";

type Constraint = {
  name: string;
  eplStatements: EplStatement[];
  type: string;
  status: string;
  activationEvent: string;
  targetEvent: string;
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
              <LinkBreak className="w-24 h-24 text-gray-300" />
              <span className="text-sm text-gray-300">
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
                  subtitle={
                    <span>
                      {c.type}({c.activationEvent},{c.targetEvent})
                    </span>
                  }
                  title={c.name}
                >
                  {c.eplStatements.map((s) => (
                    <div
                      key={s.deploymentId}
                      className="my-2 grid grid-cols-4 gap-4"
                    >
                      <div className="w-64">
                        <Tooltip content={s.deploymentId}>
                          <Chip variant="faded">{s.type}</Chip>
                        </Tooltip>
                      </div>
                      <ShikiHighlighter
                        className="col-span-3 text-sm"
                        language="sql"
                        theme="ayu-dark"
                      >
                        {s.statement.trim()}
                      </ShikiHighlighter>
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
