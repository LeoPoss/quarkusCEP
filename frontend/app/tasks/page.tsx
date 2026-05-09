"use client";

import {
    addToast,
    Button,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    useDisclosure,
} from "@heroui/react";
import {
    ArrowCounterClockwiseIcon,
    WarningIcon,
} from "@phosphor-icons/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";

import ArchitectureFlow from "@/components/architecture-flow";
import TaskList from "@/components/task-list";
import SignalInjection from "@/components/signal-injection";
import ConstraintQuickCreate from "@/components/constraint-quick-create";

export default function TasksPage() {
    const queryClient = useQueryClient();
    const { isOpen, onOpen, onClose } = useDisclosure();

    const resetMutation = useMutation({
        mutationFn: async () => {
            return await api.post("esper/reset");
        },
        onSuccess: () => {
            addToast({
                title: "Process Reset",
                description: "Process instance reset",
                color: "success",
            });
            queryClient.invalidateQueries();
            onClose();
        },
        onError: (error) => {
            addToast({
                title: "Reset Failed",
                description: error.message,
                color: "danger",
            });
        },
    });

    return (
        <section className="flex flex-col gap-4 pb-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold">Tasklist</h1>
                <Button
                    size="sm"
                    color="danger"
                    variant="flat"
                    startContent={<ArrowCounterClockwiseIcon size={16} />}
                    onPress={onOpen}
                >
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
            <Modal isOpen={isOpen} onClose={onClose} size="sm">
                <ModalContent>
                    <ModalHeader className="flex items-center gap-2 text-sm">
                        Reset Esper Engine?
                    </ModalHeader>
                    <ModalBody className="text-sm">
                        <p>This will clear trace, constraints, and reset Esper.</p>
                    </ModalBody>
                    <ModalFooter>
                        <Button size="sm" variant="flat" onPress={onClose}>Cancel</Button>
                        <Button
                            size="sm"
                            color="danger"
                            isLoading={resetMutation.isPending}
                            onPress={() => resetMutation.mutate()}
                        >
                            Reset
                        </Button>
                    </ModalFooter>
                </ModalContent>
            </Modal>
        </section>
    );
}
