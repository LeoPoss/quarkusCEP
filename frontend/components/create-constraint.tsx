import React from "react";
import { Button, Card, CardBody, CardHeader, Form } from "@heroui/react";
import ky from "ky";
import dynamic from "next/dynamic";

import { cardHeader } from "./primitives";

import { useMPDeclare } from "@/contexts/mpDeclareContext";
import { FilePlusIcon } from "@phosphor-icons/react";

const DynamicFormFields = dynamic(
  () => import("./create-constraint-form-fields"),
  {
    ssr: false,
  },
);

export default function CreateConstraint() {
  const [submitted, setSubmitted] = React.useState(null);
  const [errors, setErrors] = React.useState({});
  const [constraintType, setConstraintType] = React.useState("");
  const { isMPDeclareEnabled, toggleMPDeclare } = useMPDeclare();

  const onSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const data = Object.fromEntries(formData);

    // Prepare the request payload
    const payload: any = {
      name: data.name,
      timer: data.timer || null,
      activationEvent: data.activationEvent || null,
      targetEvent: data.targetEvent || null,
      activationEventType: data.activationEventType,
      targetEventType: data.targetEventType,
    };

    // Add activation condition if MP-Declare is enabled
    if (isMPDeclareEnabled) {
      payload.activationCondition = {
        param: data.activationParam,
        operator: data.activationOperator,
        value: data.activationValue,
        timer: data.activationTimer,
      };

      // Add target condition if fields are filled
      if (data.targetParam && data.targetOperator && data.targetValue) {
        payload.targetCondition = {
          param: data.targetParam,
          operator: data.targetOperator,
          value: data.targetValue,
          timer: data.targetTimer,
        };
      }

      // Add correlation condition if all fields are filled
      if (
        data.correlationActivationParam &&
        data.correlationOperator &&
        data.correlationTargetParam
      ) {
        payload.correlationCondition = {
          activationParam: data.correlationActivationParam,
          operator: data.correlationOperator,
          targetParam: data.correlationTargetParam,
        };
      }
    }

    ky.post(
      `http://localhost:8080/constraints/${(data.type as string).toLowerCase()}`,
      { json: payload },
    );
    setErrors({});
  };

  return (
    <Card>
      <CardHeader className={cardHeader()}>
        <FilePlusIcon className="mr-4" size={32} /> Create new Constraint
      </CardHeader>
      <Form
        className="space-y-4"
        validationErrors={errors}
        onReset={() => setSubmitted(null)}
        onSubmit={onSubmit}
      >
        <CardBody>
          <div className="space-y-4">
            <DynamicFormFields
              constraintType={constraintType}
              isMPDeclareEnabled={isMPDeclareEnabled}
              setConstraintType={setConstraintType}
            />
          </div>

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
