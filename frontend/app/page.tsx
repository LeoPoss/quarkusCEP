"use client";

import { useQuery } from "@tanstack/react-query";
import ky from "ky";
import ConstraintsOverview from "@/components/constraints";
import CreateConstraint from "@/components/create-constraint";
import SendEvent from "@/components/send-event";
import AnalysisPanel from "@/components/analysis-panel";
import FinishabilityStatus from "@/components/finishability-status";
import { subtitle, title } from "@/components/primitives";

interface FinishabilityResponse {
  canFinish: boolean;
  reasons: string[];
}

const fetchFinishability = async (): Promise<FinishabilityResponse> => {
  return await ky.get("http://localhost:8080/analysis/finishability").json();
};

export default function Home() {
  const {
    data: finishability,
    isLoading,
    error,
  } = useQuery<FinishabilityResponse>({
    queryKey: ["analysis", "finishability"],
    queryFn: fetchFinishability,
    refetchInterval: 1000,
  });

  return (
    <section className="flex flex-col gap-4">

      <FinishabilityStatus
        finishability={finishability!}
        isLoading={isLoading}
        error={error}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 ">
        <AnalysisPanel />
        <SendEvent />

        <div className="md:col-span-2 space-y-4">
          <CreateConstraint />
        </div>
      </div>

      <h2 className={subtitle()}>
        Current constraints{" "}
        <span className="text-sm text-gray-500">
          (Timers start with first event)
        </span>
      </h2>
      <ConstraintsOverview />
    </section>
  );
}
