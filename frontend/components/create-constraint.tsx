import React from "react";
import {
  Button,
  Card,
  CardBody,
  CardFooter,
  CardHeader,
  Form,
  Input,
  Select,
  SelectItem,
} from "@heroui/react";
import ky from "ky";
import { Plus } from "@phosphor-icons/react";

export default function CreateConstraint() {
  const [submitted, setSubmitted] = React.useState(null);
  const [errors, setErrors] = React.useState({});

  const onSubmit = (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));

    ky.post("http://localhost:8080/constraints/" + data.type.toLowerCase(), {
      json: data,
    });
    setErrors({});
  };

  return (
    <Card>
      <CardHeader>
        <Plus className="w-6 h-6 mr-2" /> Create new Constraint
      </CardHeader>
      <Form
        validationErrors={errors}
        onReset={() => setSubmitted(null)}
        onSubmit={onSubmit}
      >
        <CardBody>
          <div className="grid grid-cols-3 gap-4">
            <Input isRequired className="col-span-3" label="Name" name="name" />
            <Select isRequired label="Type" name="type">
              <SelectItem key="existence">Existence</SelectItem>
              <SelectItem key="response">Response</SelectItem>
              <SelectItem key="precedence">Precedence</SelectItem>
              <SelectItem key="respondedExistence">
                Responded Existence
              </SelectItem>
              <SelectItem key="alternateResponse">
                Alternate Response
              </SelectItem>
              <SelectItem key="notResponse">Not Response</SelectItem>
            </Select>
            <Input label="Activation Event" name="activationEvent" />
            <Input isRequired label="Target Event" name="targetEvent" />
          </div>
        </CardBody>
        <CardFooter>
          <div className="flex gap-4">
            <Button className="w-full" color="primary" size="lg" type="submit">
              Create
            </Button>
            <Button size="lg" type="reset" variant="bordered">
              Reset
            </Button>
          </div>
        </CardFooter>
      </Form>
    </Card>
  );
}
