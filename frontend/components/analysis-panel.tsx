import { Card, CardBody, CardHeader, Chip, Spinner } from "@heroui/react";
import { CheckCircleIcon, CompassRoseIcon, XCircleIcon } from "@phosphor-icons/react";
import * as React from "react";
import { useEffect, useState } from "react";
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

export default function AnalysisPanel() {
  const [displayData, setDisplayData] = useState<{
    tasks: TaskAnalysis[];
    finishability: FinishabilityResponse | null;
    lastUpdated: Date | null;
  }>({ tasks: [], finishability: null, lastUpdated: null });

  const [isLoading, setIsLoading] = useState(true);

  const fetchAnalysis = async (isInitialLoad = false) => {
    try {
      if (isInitialLoad) {
        setIsLoading(true);
      }

      const [tasksResponse, finishabilityResponse] = await Promise.all([
        ky
          .get("http://localhost:8080/analysis/allowed-tasks")
          .json<TaskAnalysis[]>(),
        ky
          .get("http://localhost:8080/analysis/finishability")
          .json<FinishabilityResponse>(),
      ]);

      setDisplayData({
        tasks: tasksResponse,
        finishability: finishabilityResponse,
        lastUpdated: new Date(),
      });
    } catch (err) {
      console.error("Error fetching analysis:", err);
    } finally {
      if (isInitialLoad) {
        setIsLoading(false);
      }
    }
  };

  useEffect(() => {
    fetchAnalysis(true);
    const interval = setInterval(() => fetchAnalysis(false), 2000);
    return () => clearInterval(interval);
  }, []);

  const formatLastUpdated = (date: Date | null) => {
    if (!date) return "";

    return `Updated: ${date.toLocaleTimeString()}`;
  };

  const { tasks, finishability, lastUpdated } = displayData;

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

  return (
    <Card>
      <CardHeader className={cardHeader()}>
        <div className="flex justify-between items-center w-full">
          <div className="flex items-center">
            <CompassRoseIcon className="mr-4" size={32} />
            Process Flow Control
          </div>
          {/*{lastUpdated && (
            <span className="text-xs text-gray-500">
              {formatLastUpdated(lastUpdated)}
            </span>
          )}*/}
        </div>
      </CardHeader>
      <CardBody>
        {isLoading ? (
          <div className="flex justify-center py-4">
            <Spinner size="md" />
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {finishability ? (
              <div
                className={`flex items-start gap-3 ${finishability.canFinish ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"}`}
              >
                {finishability.canFinish ? (
                  <CheckCircleIcon
                    size={24}
                    weight="fill"
                    className="mt-0.5 flex-shrink-0"
                  />
                ) : (
                  <XCircleIcon
                    size={24}
                    weight="fill"
                    className="mt-0.5 flex-shrink-0"
                  />
                )}
                <div>
                  <h3 className="font-medium text-lg">
                    {finishability.canFinish
                      ? "Process can be finished"
                      : "Process cannot be finished"}
                  </h3>

                  {finishability.reasons.length > 0 && (
                    <div className="mt-3">
                      <div className="space-y-2">
                        {finishability.reasons.map((reason, index) => {
                          // Try to parse constraint information from the reason
                          const match = reason.match(/(.*?):\s*(.*)/);
                          const [constraintType, constraintDetail] = match
                            ? [match[1], match[2]]
                            : [null, reason];

                          return (
                            <div
                              key={index}
                              className={`flex items-start gap-2 p-2 rounded-md ${
                                finishability.canFinish
                                  ? "bg-green-50 dark:bg-green-900/20"
                                  : "bg-red-50 dark:bg-red-900/20"
                              }`}
                            >
                              {finishability.canFinish ? (
                                <CheckCircleIcon
                                  size={18}
                                  weight="fill"
                                  className="mt-0.5 flex-shrink-0 text-green-600 dark:text-green-400"
                                />
                              ) : (
                                <XCircleIcon
                                  size={18}
                                  weight="fill"
                                  className="mt-0.5 flex-shrink-0 text-red-600 dark:text-red-400"
                                />
                              )}
                              <div className="text-sm">
                                {constraintType && (
                                  <span className="font-medium text-gray-800 dark:text-gray-200">
                                    {constraintType}:
                                  </span>
                                )}
                                <span className="ml-1">{constraintDetail}</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <span className="text-gray-500 text-sm">
                Finishability data not available
              </span>
            )}

            <div className="grid grid-cols-1 gap-4">
              <div className="rounded-xl p-4 bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-800/30 dark:to-gray-900/30">
                <h3 className="font-medium mb-3 flex items-center gap-2 text-gray-800 dark:text-gray-200">
                  Available Tasks
                </h3>
                <div className="space-y-2">
                  {Array.from(new Set(tasks.map((t) => t.task)))
                    .sort((a, b) => a.localeCompare(b))
                    .map((taskName) => {
                      const hasRestrictions = tasks.some(
                        (t) => t.task === taskName && t.isUnsafe,
                      );
                      return (
                        <div
                          key={taskName}
                          className="group p-3 rounded-md bg-white dark:bg-gray-800/50  transition-colors border border-gray-100 dark:border-gray-700"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <span className="font-medium">{taskName}</span>
                            </div>
                            {hasRestrictions && (
                              <Chip size="sm" color="warning" variant="flat">
                                Restrictions
                              </Chip>
                            )}
                          </div>
                        </div>
                      );
                    })}
                </div>
              </div>

              <div className="rounded-xl p-4 bg-gradient-to-br from-red-50 to-red-100 dark:from-red-900/30 dark:to-red-800/30">
                <h3 className="font-medium mb-3 flex items-center gap-2 text-red-800 dark:text-red-300">
                  <XCircleIcon size={20} />
                  Not Allowed Tasks Details
                </h3>
                <div className="space-y-4">
                  {getGroupedUnsafeTasks().length === 0 ? (
                    <div className="text-center py-4 text-gray-500 dark:text-gray-400">
                      All tasks are currently allowed
                    </div>
                  ) : (
                    getGroupedUnsafeTasks().map(([taskName, conditions]) => (
                      <div
                        key={taskName}
                        className="bg-white/50 dark:bg-gray-800/50 rounded-lg p-3 shadow-sm"
                      >
                        <div className="font-medium text-red-700 dark:text-red-300 mb-2 flex items-center gap-2">
                          <span className="font-bold">{taskName}</span>
                          <span className="text-sm text-gray-500 dark:text-gray-400">
                            {conditions.length} additional restriction
                            {conditions.length !== 1 ? "s" : ""}
                          </span>
                        </div>

                        <div className="space-y-2 pl-2">
                          {conditions.length === 0 ? (
                            <div className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/30 p-2 rounded">
                              Generally not allowed in current state
                            </div>
                          ) : (
                            <ul className="space-y-1.5">
                              {conditions.map((cond, idx) => (
                                <li key={idx} className="text-sm">
                                  <span className="text-gray-500 dark:text-gray-400">
                                    •
                                  </span>{" "}
                                  <span className="font-mono bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">
                                    {Object.entries(cond)
                                      .map(([param, value]) => (
                                        <span key={param}>
                                          <span className="text-red-600 dark:text-red-400">
                                            {param}
                                          </span>
                                          <span className="text-gray-400">
                                            =
                                          </span>
                                          <span className="text-blue-600 dark:text-blue-400">
                                            "{value}"
                                          </span>
                                        </span>
                                      ))
                                      .reduce(
                                        (prev, curr, index) =>
                                          [
                                            prev,
                                            <span
                                              key={`sep-${index}`}
                                              className="mx-1 text-gray-400"
                                            >
                                              and
                                            </span>,
                                            curr,
                                          ].filter(
                                            Boolean,
                                          ) as React.ReactNode[],
                                      )}
                                  </span>
                                </li>
                              ))}
                            </ul>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
