import { Card, CardBody, CardHeader, Chip, Spinner } from "@heroui/react";
import {
  CheckCircleIcon,
  CompassRoseIcon,
  XCircleIcon,
} from "@phosphor-icons/react";
import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import ky from "ky";

import { cardHeader } from "./primitives";

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

export default function AnalysisPanel() {
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
    error: finishabilityError,
  } = useQuery<FinishabilityResponse>({
    queryKey: ["analysis", "finishability"],
    queryFn: fetchFinishability,
    refetchInterval: 1000, 
  });

  const isLoading = isLoadingTasks || isLoadingFinishability;
  const error = tasksError || finishabilityError

  // Group unsafe tasks by task name and collect their conditions
  const getGroupedUnsafeTasks = () => {
    const grouped: Record<string, Array<Record<string, string>>> = {};

    tasks
      .filter((task) => task.isUnsafe)
      .forEach((task) => {
        if (!grouped[task.task]) {
          grouped[task.task] = [];
        }
        if (
          task.unsafeConditions &&
          Object.keys(task.unsafeConditions).length > 0
        ) {
          grouped[task.task].push(task.unsafeConditions);
        }
      });

    // Convert to array and sort by task name
    return Object.entries(grouped).sort(([a], [b]) => a.localeCompare(b));
  };

  if (isLoading) {
    return (
      <div className="flex justify-center p-4">
        <Spinner />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-red-500 p-4">
        Error loading analysis data: {error instanceof Error ? error.message : 'Unknown error'}
      </div>
    );
  }

  const groupedUnsafeTasks = getGroupedUnsafeTasks();

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className={cardHeader()}>
          <div className="flex items-center w-full">
            <div className="flex items-center">
              <CompassRoseIcon className="mr-4" size={32} />
              Process Flow Control
            </div>
          </div>
        </CardHeader>
        <CardBody>
          <div className="flex flex-col gap-4">
            {finishability && (
              <div
                className={`flex items-start gap-3 p-4 rounded-lg ${
                  finishability.canFinish
                    ? "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400"
                    : "bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400"
                }`}
              >
                {finishability.canFinish ? (
                  <CheckCircleIcon
                    size={24}
                    weight="fill"
                    className="flex-shrink-0 mt-0.5"
                  />
                ) : (
                  <XCircleIcon
                    size={24}
                    weight="fill"
                    className="flex-shrink-0 mt-0.5"
                  />
                )}
                <div>
                  <h3 className="font-medium text-lg">
                    {finishability.canFinish
                      ? "Process can be finished"
                      : "Process cannot be finished"}
                  </h3>
                  {finishability.reasons.length > 0 && (
                    <div className="mt-2 space-y-2">
                      {finishability.reasons.map((reason, index) => {
                        const match = reason.match(/(.*?):\s*(.*)/);
                        const [constraintType, constraintDetail] = match
                          ? [match[1], match[2]]
                          : [null, reason];

                        return (
                          <div
                            key={index}
                            className="text-sm p-2 rounded bg-white/50 dark:bg-gray-800/50"
                          >
                            {constraintType && (
                              <span className="font-medium">{constraintType}: </span>
                            )}
                            {constraintDetail}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4">
              <div className="rounded-lg p-4 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
                <h3 className="font-medium mb-3 text-gray-800 dark:text-gray-200">
                  Available Tasks
                </h3>
                <div className="space-y-2">
                  {tasks.length === 0 ? (
                    <p className="text-sm text-gray-500">No tasks available</p>
                  ) : (
                    Array.from(new Set(tasks.map((t) => t.task)))
                      .sort((a, b) => a.localeCompare(b))
                      .map((taskName) => {
                        const hasRestrictions = tasks.some(
                          (t) => t.task === taskName && t.isUnsafe
                        );
                        return (
                          <div
                            key={taskName}
                            className="p-3 rounded-md bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700"
                          >
                            <div className="flex items-center justify-between">
                              <span className="font-medium">{taskName}</span>
                              {hasRestrictions && (
                                <Chip size="sm" color="warning" variant="flat">
                                  Restrictions
                                </Chip>
                              )}
                            </div>
                          </div>
                        );
                      })
                  )}
                </div>
              </div>

              <div className="rounded-lg p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-900/30">
                <h3 className="font-medium mb-3 text-red-800 dark:text-red-300 flex items-center gap-2">
                  <XCircleIcon size={20} weight="fill" />
                  Not Allowed Tasks Details
                </h3>
                <div className="space-y-3">
                  {groupedUnsafeTasks.length === 0 ? (
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      All tasks are currently allowed
                    </p>
                  ) : (
                    groupedUnsafeTasks.map(([taskName, conditions]) => (
                      <div
                        key={taskName}
                        className="bg-white/50 dark:bg-gray-800/50 rounded-lg p-3 border border-red-100 dark:border-red-900/30"
                      >
                        <div className="font-medium text-red-700 dark:text-red-300 mb-2">
                          {taskName}
                          {conditions.length > 0 && (
                            <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">
                              ({conditions.length} restriction{conditions.length !== 1 ? 's' : ''})
                            </span>
                          )}
                        </div>

                        {conditions.length > 0 && (
                          <ul className="space-y-2 pl-2 mt-2">
                            {conditions.map((cond, idx) => (
                              <li key={idx} className="text-sm">
                                <div className="inline-flex items-center bg-gray-100 dark:bg-gray-700/50 px-2 py-1 rounded">
                                  {Object.entries(cond).map(([param, value], i, arr) => (
                                    <React.Fragment key={param}>
                                      <span className="text-red-600 dark:text-red-400">{param}</span>
                                      <span className="mx-1">=</span>
                                      <span className="text-blue-600 dark:text-blue-400">"{value as string}"</span>
                                      {i < arr.length - 1 && <span className="mx-1 text-gray-400">and</span>}
                                    </React.Fragment>
                                  ))}
                                </div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
