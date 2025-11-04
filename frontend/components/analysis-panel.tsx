import { Card, CardBody, CardHeader, Divider, Spinner } from "@heroui/react";
import {
  CheckCircleIcon,
  InfoIcon,
  WarningCircleIcon,
  XCircleIcon,
} from "@phosphor-icons/react";
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

      // Fetch data in parallel
      const [tasksResponse, finishabilityResponse] = await Promise.all([
        ky
          .get("http://localhost:8080/analysis/allowed-tasks")
          .json<TaskAnalysis[]>(),
        ky
          .get("http://localhost:8080/analysis/finishability")
          .json<FinishabilityResponse>(),
      ]);

      // Update display data in one go
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
    // Initial load
    fetchAnalysis(true);

    // Set up background refresh
    const interval = setInterval(() => fetchAnalysis(false), 2000);
    return () => clearInterval(interval);
  }, []);

  // Format time for last updated
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
            <InfoIcon className="w-6 h-6 mr-4" />
            Process Analysis
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
          <div className="space-y-4">
            <div>
              <h3 className="font-medium mb-2 flex items-center gap-2">
                Allowed Tasks
              </h3>
              <div className="flex flex-wrap gap-2">
                {tasks.length === 0 ? (
                  <span className="text-gray-500 text-sm">
                    No tasks available
                  </span>
                ) : (
                  tasks.map((task) => (
                    <span
                      key={task.task}
                      className={`px-3 py-1 rounded-full text-sm flex items-center gap-1 ${
                        task.isUnsafe
                          ? "bg-red-100 text-red-800"
                          : "bg-green-100 text-green-800"
                      }`}
                    >
                      {task.isUnsafe ? (
                        <XCircleIcon size={16} weight="fill" />
                      ) : (
                        <CheckCircleIcon size={16} weight="fill" />
                      )}
                      {task.task}
                    </span>
                  ))
                )}
              </div>
            </div>

            <Divider className="my-2" />

            <div>
              <h3 className="font-medium mb-2 flex items-center gap-2">
                Process Finishability
              </h3>
              {finishability ? (
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    {finishability.canFinish ? (
                      <>
                        <CheckCircleIcon
                          className="text-green-500"
                          size={20}
                          weight="fill"
                        />
                        <span className="text-green-700">
                          Process can be finished
                        </span>
                      </>
                    ) : (
                      <>
                        <XCircleIcon
                          className="text-red-500"
                          size={20}
                          weight="fill"
                        />
                        <span className="text-red-700">
                          Process cannot be finished
                        </span>
                      </>
                    )}
                  </div>

                  {finishability.reasons.length > 0 && (
                    <div className="mt-2 text-sm text-gray-600">
                      <h4 className="font-medium mb-1">Reasons:</h4>
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
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
