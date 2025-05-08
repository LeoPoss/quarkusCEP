import ky from "ky";
import { addToast, Button, Card, CardBody, CardHeader } from "@heroui/react";
import { Input } from "@heroui/input";
import { useState } from "react";
import { PaperPlaneTilt } from "@phosphor-icons/react";

export default function SendEvent() {
  const [customEventType, setCustomEventType] = useState("");

  async function sendEvent(event: string) {
    const response = await ky.post("http://localhost:8080/esper/event", {
      json: { type: event },
    });

    addToast({
      title: "Event sent successfully",
      message: `Submitted event: ${event}`,
      status: "success", // Assuming addToast supports different statuses
    });
  }
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCustomEventType(e.target.value);
  };

  return (
    <Card>
      <CardHeader>
        <PaperPlaneTilt className="w-6 h-6 mr-2" />
        Send Event
      </CardHeader>
      <CardBody className="gap-4 flex flex-col">
        <div className="grid grid-cols-2 gap-4">
          <Button
            color="secondary"
            size="lg"
            variant="shadow"
            onPress={() => sendEvent("A")}
          >
            A
          </Button>
          <Button
            color="secondary"
            size="lg"
            variant="shadow"
            onPress={() => sendEvent("B")}
          >
            B
          </Button>
        </div>
        <div className="flex gap-2 items-center">
          <Input
            className="flex-grow"
            placeholder="Enter custom event type"
            value={customEventType}
            onChange={handleInputChange}
          />
          <Button size="lg" onPress={() => sendEvent(customEventType)}>
            Send Custom
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}
