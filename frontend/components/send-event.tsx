import ky from "ky";
import {
  addToast,
  Button,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Tooltip,
} from "@heroui/react";
import { Input } from "@heroui/input";
import { useState } from "react";
import { Code, PaperPlaneTilt } from "@phosphor-icons/react";

import { useMPDeclare } from "@/contexts/mpDeclareContext";

export default function SendEvent() {
  const [customEventType, setCustomEventType] = useState("");
  const [payloadName, setPayloadName] = useState("");
  const [payloadValue, setPayloadValue] = useState("");
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  type Event = {
    type: string;
    payload?: {
      [key: string]: string;
    };
  };

  async function sendEvent(event: Event) {
    const response = await ky.post("http://localhost:8080/esper/event", {
      json: event,
    });

    addToast({
      title: "Event sent successfully",
      description: `Submitted event: ${JSON.stringify(event)}`,
      color: "success",
      timeout: 1000,
      shouldShowTimeoutProgress: true,
    });
  }

  return (
    <Card>
      <CardHeader>
        <PaperPlaneTilt className="mr-4" size={24} />
        Send Event
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
          <Button
            className="w-full"
            onPress={() =>
              sendEvent({
                type: customEventType,
                payload:
                  payloadName && payloadValue
                    ? {
                        [payloadName]: payloadValue,
                      }
                    : undefined,
              })
            }
          >
            Send
          </Button>
        </div>
        <Divider className="my-2" />
        <div className="flex flex-row gap-2 items-center">
          <Code className="mr-2" size="24" />
          <div className="grid grid-cols-2 gap-2 w-full">
            <Tooltip content="Send simple (no payload) A-Event">
              <Button
                color="secondary"
                size="md"
                variant="flat"
                onPress={() => sendEvent({ type: "A" })}
              >
                A
              </Button>
            </Tooltip>
            <Tooltip content="Send simple (no payload) B-Event">
              <Button
                color="secondary"
                size="md"
                variant="flat"
                onPress={() => sendEvent({ type: "B" })}
              >
                B
              </Button>
            </Tooltip>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
