import { CheckCircleIcon, XCircleIcon } from "@phosphor-icons/react";

interface FinishabilityStatusProps {
  finishability: {
    canFinish: boolean;
    reasons: string[];
  };
  isLoading: boolean;
  error: Error | null;
}

export default function FinishabilityStatus({
  finishability,
  isLoading,
  error,
}: FinishabilityStatusProps) {
  if (isLoading) return null;
  if (error) return null;
  if (!finishability) return null;

  return (
    <div
      className={`flex items-start gap-3 p-4 rounded-lg  ${
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
  );
}
