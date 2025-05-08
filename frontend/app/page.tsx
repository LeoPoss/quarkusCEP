"use client";

import * as React from "react";

import ConstraintsOverview from "@/components/constraints";
import CreateConstraint from "@/components/create-constraint";
import SendEvent from "@/components/send-event";

export default function Home() {
  return (
    <section className="flex flex-col gap-4 py-8 md:py-10">
      <h2 className="text-2xl font-black">Constraints</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-16">
        <CreateConstraint />
        <SendEvent />
      </div>

      <h2 className="text-lg mt-4">Current constraints</h2>
      <ConstraintsOverview />
    </section>
  );
}
