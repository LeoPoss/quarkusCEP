import React from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Form,
  Input,
  Select,
  SelectItem,
  SelectSection,
} from "@heroui/react";
import ky from "ky";
import { Plus } from "@phosphor-icons/react";

import { cardHeader } from "./primitives";

import { useMPDeclare } from "@/contexts/mpDeclareContext";

export default function CreateConstraint() {
  const [submitted, setSubmitted] = React.useState(null);
  const [errors, setErrors] = React.useState({});
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  const onSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));

    ky.post(
      "http://localhost:8080/constraints/" +
        (data.type as string).toLowerCase(),
      {
        json: {
          name: data.name,
          activationEvent: data.activationEvent,
          targetEvent: data.targetEvent,
          activationCondition: {
            param: data.activationParam,
            operator: data.activationOperator,
            value: data.activationValue,
          },
          targetCondition: {
            param: data.targetParam,
            operator: data.targetOperator,
            value: data.targetValue,
          },
        },
      },
    );
    setErrors({});
  };

  return (
    <Card>
      <CardHeader className={cardHeader()}>
        <Plus className="w-6 h-6 mr-4" /> Create new Constraint
      </CardHeader>
      <Form
        className="space-y-4"
        validationErrors={errors}
        onReset={() => setSubmitted(null)}
        onSubmit={onSubmit}
      >
        <CardBody>
          <div className="grid grid-cols-3 gap-4">
            <Input
              isRequired
              className="col-span-3"
              label="Name"
              name="name"
              size="sm"
            />
            <Select
              isRequired
              label="Type"
              name="type"
              size="sm"
            >
              <SelectSection title="Existence Constraints">
                <SelectItem key="existence">Existence</SelectItem>
              </SelectSection>
              <SelectSection title="Relation Constraints">
                <SelectItem key="respondedExistence">
                  Responded Existence
                </SelectItem>
                <SelectItem key="response">Response</SelectItem>
                <SelectItem key="alternateResponse">
                  Alternate Response
                </SelectItem>
                <SelectItem key="chainResponse">Chain Response</SelectItem>
                <SelectItem key="precedence">Precedence</SelectItem>
                <SelectItem key="alternatePrecedence">
                  Alternate Precedence
                </SelectItem>
                <SelectItem key="chainPrecedence">
                  Chain Precedence
                </SelectItem>
              </SelectSection>

              <SelectSection title="Negative Relation Constraints">
                <SelectItem key="notResponse">Not Response</SelectItem>
              </SelectSection>
            </Select>
            <Input label="Activation Event" name="activationEvent" size="sm" />
            <Input
              isRequired
              label="Target Event"
              name="targetEvent"
              size="sm"
            />
          </div>
          {isMPDeclareEnabled && (
            <div className="mt-4 bg-gradient-to-br from-primary-200 via-transparent p-2 relative">
              <div className="absolute left-2 top-2 opacity-20 text-5xl font-bold text-primary">
                MP-Declare
              </div>
              <div>
                <h3 className="text-sm font-medium mb-2">Conditions</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <h4 className="text-xs font-medium text-neutral-500 mb-1">
                      Activation Condition
                    </h4>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                      <Input
                        label="Parameter"
                        name="activationParam"
                        size="sm"
                      />
                      <Select
                        label="Operator"
                        name="activationOperator"
                        size="sm"
                      >
                        <SelectItem key="<">{"<"}</SelectItem>
                        <SelectItem key="=">{"=="}</SelectItem>
                        <SelectItem key=">">{">"}</SelectItem>
                      </Select>
                      <Input label="Value" name="activationValue" size="sm" />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <h4 className="text-xs font-medium text-neutral-500 mb-1">
                      Target Condition
                    </h4>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                      <Input label="Parameter" name="targetParam" size="sm" />
                      <Select label="Operator" name="targetOperator" size="sm">
                        <SelectItem key="<">{"<"}</SelectItem>
                        <SelectItem key="=">{"=="}</SelectItem>
                        <SelectItem key=">">{">"}</SelectItem>
                      </Select>
                      <Input label="Value" name="targetValue" size="sm" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="flex gap-2 mt-4">
            <Button color="primary" size="md" type="submit">
              Create
            </Button>
            <Button size="md" type="reset" variant="bordered">
              Reset
            </Button>
          </div>
        </CardBody>
      </Form>
    </Card>
  );
}
