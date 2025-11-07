import {
  addToast,
  Button,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Modal,
  ModalBody,
  ModalContent,
  ModalFooter,
  ModalHeader,
  Tooltip,
  useDisclosure
} from "@heroui/react";
import { Input } from "@heroui/input";
import * as React from "react";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import ky from "ky";
import { CodeIcon, PaperPlaneTiltIcon, TrashIcon, WarningIcon } from "@phosphor-icons/react";

import { cardHeader } from "./primitives";
import { useMPDeclare } from "@/contexts/mpDeclareContext";

export default function SendEvent() {
  const [customEventType, setCustomEventType] = useState("");
  const [payloadName, setPayloadName] = useState("");
  const [payloadValue, setPayloadValue] = useState("");
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  type Event = {
    eventType: string;
    payload?: {
      [key: string]: string;
    };
  };

  const sendEventMutation = useMutation({
    mutationFn: async (event: Event) => {
      const response = await ky.post("http://localhost:8080/esper/event", {
        json: event,
      });
      return response;
    },
    onSuccess: () => {
      addToast({
        title: "Event sent successfully",
        description: `Event type: ${sendEventMutation.variables?.eventType || "Unknown"}`,
        color: "success",
      });
    },
    onError: (error) => {
      addToast({
        title: "Error sending event",
        description: error.message,
        color: "danger",
      });
    },
  });

  function handleSendEvent(event: Event) {
    sendEventMutation.mutate(event);
  }

  async function resetEsper() {
    const response = await ky.post("http://localhost:8080/esper/reset");

    addToast({
      title: "Reset esper successfully",
      color: "success",
      timeout: 1000,
      shouldShowTimeoutProgress: true,
    });
  }

  const { isOpen, onOpen, onClose } = useDisclosure();

  return (
    <Card>
      <CardHeader className={cardHeader()}>
        <PaperPlaneTiltIcon className="mr-4" size={32} />
        Send Event/Complete Task
      </CardHeader>
      <CardBody>
        <div className="grid gap-4  h-full">
          {isMPDeclareEnabled ? (
            <div className="grid grid-cols-1 gap-2">
              <Input
                isRequired
                label="Type"
                size="sm"
                value={customEventType}
                onChange={(v) => setCustomEventType(v.target.value)}
              />
              <div className="gap-2 bg-gradient-to-br from-primary-200 via-transparent p-2 relative">
                <div className="absolute left-2 top-2 opacity-20 text-5xl font-bold text-primary">
                  MP-Declare
                </div>
                <div className="col-span-2 grid grid-cols-2 gap-2">
                  <h3 className="text-sm font-medium mb-2 col-span-2">
                    Payload
                  </h3>
                  <Input
                    label="Payload Name"
                    size="sm"
                    value={payloadName}
                    onChange={(v) => setPayloadName(v.target.value)}
                  />
                  <Input
                    label="Payload Value"
                    size="sm"
                    value={payloadValue}
                    onChange={(v) => setPayloadValue(v.target.value)}
                  />
                </div>
              </div>
            </div>
          ) : (
            <Input
              isRequired
              label="Type"
              size="sm"
              value={customEventType}
              onChange={(v) => setCustomEventType(v.target.value)}
            />
          )}
          <div className="grid grid-cols-2 gap-2">
            <Button
              onPress={() =>
                handleSendEvent({
                  eventType: customEventType,
                  payload:
                    payloadName && payloadValue
                      ? {
                          [payloadName]: payloadValue,
                        }
                      : undefined,
                })
              }
            >
              Send Signal
            </Button>
            <Button
              onPress={() =>
                handleSendEvent({
                  eventType: customEventType,
                  payload:
                    payloadName && payloadValue
                      ? {
                          [payloadName]: payloadValue,
                        }
                      : undefined,
                })
              }
            >
              Finish Task
            </Button>
          </div>
        </div>
        <Divider className="my-2" />
        <div className="flex flex-row gap-2 items-center">
          <CodeIcon className="mr-2" size="24" />
          <div className="grid grid-cols-3 gap-2 w-full">
            <Tooltip content="Send simple (no payload) A-Event">
              <Button
                color="secondary"
                size="md"
                variant="flat"
                onPress={() => handleSendEvent({ eventType: "A" })}
              >
                A
              </Button>
            </Tooltip>
            <Tooltip content="Send simple (no payload) B-Event">
              <Button
                color="secondary"
                size="md"
                variant="flat"
                onPress={() => handleSendEvent({ eventType: "B" })}
              >
                B
              </Button>
            </Tooltip>
            <Button color="danger" variant="flat" onPress={onOpen}>
              <TrashIcon /> Reset
            </Button>

            <Modal isOpen={isOpen} onClose={onClose}>
              <ModalContent>
                <ModalHeader className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <WarningIcon size={24} weight="fill" />
                    Confirm Reset
                  </div>
                </ModalHeader>
                <ModalBody>
                  <p>
                    Are you sure you want to reset the Esper engine? This will
                    also delete all events and constraints.
                  </p>
                </ModalBody>
                <ModalFooter>
                  <Button variant="flat" onPress={onClose}>
                    Cancel
                  </Button>
                  <Button
                    color="danger"
                    onPress={() => {
                      resetEsper();
                      onClose();
                    }}
                  >
                    Reset Esper
                  </Button>
                </ModalFooter>
              </ModalContent>
            </Modal>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
