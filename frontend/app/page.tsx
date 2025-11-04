"use client";

import * as React from "react";
import { Switch } from "@heroui/switch";
import { Chip } from "@heroui/chip";

import ConstraintsOverview from "@/components/constraints";
import CreateConstraint from "@/components/create-constraint";
import SendEvent from "@/components/send-event";
import AnalysisPanel from "@/components/analysis-panel";
import { useMPDeclare } from "@/contexts/mpDeclareContext";
import { subtitle, title } from "@/components/primitives";

export default function Home() {
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  return (
    <section className="flex flex-col gap-4">
      <h2 className={title()}>Synergistic CEP for MP-Declare</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="md:col-span-2 space-y-4">
          <AnalysisPanel />
        </div>
        <div className="flex items-center col-span-2 gap-2 bg-gradient-to-br from-primary-200 via-transparent p-2 w-fit">
          <Chip color="primary">MP-Declare</Chip>
          <Switch
            checked={isMPDeclareEnabled}
            color="primary"
            size="sm"
            onChange={toggleMPDeclare}
          />
        </div>
        <CreateConstraint />
        <SendEvent />
        <div className="md:col-span-1 space-y-4"></div>
      </div>

      <h2 className={subtitle()}>Current constraints</h2>
      <ConstraintsOverview />
    </section>
  );
}
