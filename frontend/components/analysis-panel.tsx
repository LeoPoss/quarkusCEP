import { Card, CardBody, CardHeader, Chip, Spinner } from "@heroui/react";
import { CheckCircleIcon, CompassRoseIcon, XCircleIcon } from "@phosphor-icons/react";
import * as React from "react";
import { useEffect, useState } from "react";
import ky from "ky";

import { cardHeader } from "./primitives";

interface TaskAnalysis {
  task: string;
  isUnsafe: boolean;
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
              <div>
                <div className="flex items-center gap-2 mb-2">
                  {finishability.canFinish ? (
                    <>
                      <CheckCircleIcon
                        className="text-success-700"
                        size={24}
                        weight="fill"
                      />
                      <span className="text-success-700">
                        Process can be finished
                      </span>
                    </>
                  ) : (
                    <>
                      <XCircleIcon
                        className="text-danger-600"
                        size={24}
                        weight="fill"
                      />
                      <span className="text-danger-600">
                        Process cannot be finished
                      </span>
                    </>
                  )}
                </div>

                {finishability.reasons.length > 0 && (
                  <div className="mt-2 text-sm text-gray-600">
                    <h4 className="font-medium mb-1">Reasons</h4>
                    <ul className="list-disc pl-5 space-y-1">
                      {finishability.reasons.map((reason, index) => (
                        <li key={index}>{reason}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : (
              <span className="text-gray-500 text-sm">
                Finishability data not available
              </span>
            )}

            <div className="flex-1 flex flex-col md:flex-row gap-4">
              <div className="flex-1 rounded-xl p-4 bg-gradient-to-br from-green-50 to-green-100 dark:from-green-900/30 dark:to-green-800/30">
                <h3 className="font-medium mb-3 flex items-center gap-2 text-green-800 dark:text-green-300">
                  Allowed Tasks
                </h3>
                <div className="flex flex-wrap gap-2">
                  {tasks.filter((t) => !t.isUnsafe).length === 0 ? (
                    <span className="text-gray-500 dark:text-gray-400 text-sm">
                      No allowed tasks
                    </span>
                  ) : (
                    tasks
                      .filter((t) => !t.isUnsafe)
                      .sort((a, b) => a.task.localeCompare(b.task))
                      .map((task) => (
                        <Chip
                          key={task.task}
                          variant="flat"
                          color="success"
                          avatar={<CheckCircleIcon />}
                          className="mr-2 mb-2"
                        >
                          {task.task}
                        </Chip>
                      ))
                  )}
                </div>
              </div>

              <div className="flex-1 rounded-xl p-4 bg-gradient-to-br from-red-50 to-red-100 dark:from-red-900/30 dark:to-red-800/30">
                <h3 className="font-medium mb-3 flex items-center gap-2 text-red-800 dark:text-red-300">
                  Not Allowed Tasks
                </h3>
                <div className="flex flex-wrap gap-2">
                  {tasks.filter((t) => t.isUnsafe).length === 0 ? (
                    <span className="text-gray-500 dark:text-gray-400 text-sm">
                      No restricted tasks
                    </span>
                  ) : (
                    tasks
                      .filter((t) => t.isUnsafe)
                      .sort((a, b) => a.task.localeCompare(b.task))
                      .map((task) => (
                        <Chip
                          key={task.task}
                          variant="flat"
                          color="danger"
                          avatar={<XCircleIcon />}
                          className="mr-2 mb-2"
                        >
                          {task.task}
                        </Chip>
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
