import React from "react";
import { Button, Card, CardBody, CardHeader, Form, Input } from "@heroui/react";
import ky from "ky";
import { PlusIcon } from "@phosphor-icons/react";
import dynamic from "next/dynamic";

import { cardHeader } from "./primitives";

import { useMPDeclare } from "@/contexts/mpDeclareContext";

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
        <PlusIcon className="w-6 h-6 mr-4" /> Create new Constraint
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
