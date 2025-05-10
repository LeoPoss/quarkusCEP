"use client";

import * as React from "react";
import { Switch } from "@heroui/switch";
import { Chip } from "@heroui/chip";

import ConstraintsOverview from "@/components/constraints";
import CreateConstraint from "@/components/create-constraint";
import SendEvent from "@/components/send-event";
import { useMPDeclare } from "@/contexts/mpDeclareContext";

export default function Home() {
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-2xl font-black">Synergistic CEP for MP-Declare</h2>
      <div className="flex items-center gap-2 bg-gradient-to-br from-primary-100 via-transparent p-2 w-fit">
        <Chip color="primary">MP-Declare</Chip>
        <Switch
          checked={isMPDeclareEnabled}
          color="primary"
          size="sm"
          onChange={toggleMPDeclare}
        />
      </div>
      <div className="grid grid-cols-3 gap-8">
        <div className="col-span-2">
          <CreateConstraint />
        </div>
        <SendEvent />
      </div>

      <h2 className="text-lg mt-4">Current constraints</h2>
      <ConstraintsOverview />
    </section>
  );
}
