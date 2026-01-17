"use client";

import TaskListDashboard from "@/components/task-list-dashboard";

export default function TasksPage() {
    return (
        <section className="flex flex-col gap-4">
            <TaskListDashboard />
        </section>
    );
}
