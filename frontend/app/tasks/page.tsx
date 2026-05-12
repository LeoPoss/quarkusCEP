"use client";

import {
    toast,
    Button,
    Modal,
    useOverlayState,
    Spinner,
} from "@heroui/react";
import {
    ArrowCounterClockwise,
} from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";

import ArchitectureFlow from "@/components/architecture-flow";
import TaskList from "@/components/task-list";
import SignalInjection from "@/components/signal-injection";
import ConstraintQuickCreate from "@/components/constraint-quick-create";

export default function TasksPage() {
    const queryClient = useQueryClient();
    const modalState = useOverlayState();

    const resetMutation = useMutation({
        mutationFn: async () => {
            return await api.post("esper/reset");
        },
        onSuccess: () => {
            toast.success("Process Reset", {
                description: "Process instance reset",
            });
            queryClient.invalidateQueries();
            modalState.close();
        },
        onError: (error: any) => {
            toast.danger("Reset Failed", {
                description: error.message,
            });
        },
    });

    return (
        <section className="flex flex-col gap-4">
            {/* Header */}
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold text-foreground">Tasklist</h1>
                <Button
                    variant="danger-soft"
                    size="sm"
                    onPress={modalState.open}
                >
                    <ArrowCounterClockwise size={16} />
                    Reset
                </Button>
            </div>

            {/* Three-tier architecture overview */}
            <ArchitectureFlow />

            {/* Task List */}
            <TaskList />

            {/* Signal + Constraint Create row */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <SignalInjection />
                <ConstraintQuickCreate />
            </div>

            {/* Reset Modal */}
            <Modal state={modalState}>
                <Modal.Backdrop variant="blur">
                    <Modal.Container size="sm">
                        <Modal.Dialog>
                            {({close}) => (
                                <>
                                    <Modal.Header>
                                        <Modal.Heading>Reset Esper Engine?</Modal.Heading>
                                    </Modal.Header>
                                    <Modal.Body className="text-sm">
                                        <p className="text-default-600">This will clear trace, constraints, and reset Esper.</p>
                                    </Modal.Body>
                                    <Modal.Footer>
                                        <Button variant="ghost" size="sm" onPress={close}>Cancel</Button>
                                        <Button
                                            variant="danger"
                                            size="sm"
                                            isPending={resetMutation.isPending}
                                            onPress={() => resetMutation.mutate()}
                                        >
                                            {({isPending}) => (
                                                <>
                                                    {isPending && <Spinner color="current" size="sm" />}
                                                    Reset
                                                </>
                                            )}
                                        </Button>
                                    </Modal.Footer>
                                </>
                            )}
                        </Modal.Dialog>
                    </Modal.Container>
                </Modal.Backdrop>
            </Modal>
        </section>
    );
}