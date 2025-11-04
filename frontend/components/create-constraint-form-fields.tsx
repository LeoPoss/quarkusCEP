import React from "react";
import { Input, Select, SelectItem, SelectSection } from "@heroui/react";

interface FormFieldsProps {
  constraintType: string;
  setConstraintType: (type: string) => void;
  isMPDeclareEnabled: boolean;
}

export default function FormFields({
  constraintType,
  setConstraintType,
  isMPDeclareEnabled,
}: FormFieldsProps) {
  return (
    <>
      <Select
        isRequired
        disabledKeys={[
          "alternateResponse",
          "chainResponse",
          "alternatePrecedence",
          "chainPrecedence",
          "notPrecedence",
        ]}
        label="Type"
        name="type"
        size="sm"
        onChange={(e) => setConstraintType(e.target.value)}
      >
        <SelectSection title="Existence Constraints">
          <SelectItem key="existence">Existence</SelectItem>
          <SelectItem key="notexistence">NotExistence</SelectItem>
        </SelectSection>
        <SelectSection title="Relation Constraints">
          <SelectItem key="respondedExistence">Responded Existence</SelectItem>
          <SelectItem key="response">Response</SelectItem>
          <SelectItem key="alternateResponse">Alternate Response</SelectItem>
          <SelectItem key="chainResponse">Chain Response</SelectItem>
          <SelectItem key="precedence">Precedence</SelectItem>
          <SelectItem key="alternatePrecedence">
            Alternate Precedence
          </SelectItem>
          <SelectItem key="chainPrecedence">Chain Precedence</SelectItem>
        </SelectSection>
        <SelectSection title="Negative Relation Constraints">
          <SelectItem key="notResponse">Not Response</SelectItem>
          <SelectItem key="notPrecedence">Not Precedence</SelectItem>
        </SelectSection>
      </Select>
      <Input
        isDisabled={
          constraintType === "existence" || constraintType === "notexistence"
        }
        isRequired={
          constraintType !== "existence" && constraintType !== "notexistence"
        }
        label="Activation Event"
        name="activationEvent"
        size="sm"
      />
      <Input isRequired label="Target Event" name="targetEvent" size="sm" />
      {isMPDeclareEnabled && (
        <div className="mt-4 bg-gradient-to-br from-primary-200 via-transparent p-2 relative col-span-3">
          <div className="absolute left-2 top-2 opacity-20 text-5xl font-bold text-primary">
            MP-Declare
          </div>
          <div>
            <h3 className="text-sm font-medium mb-2">Conditions</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <h4 className="text-xs font-medium text-neutral-500 mb-1">
                  Activation Condition
                </h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                  <Input
                    isDisabled={constraintType === "existence"}
                    label="Parameter"
                    name="activationParam"
                    size="sm"
                  />
                  <Select
                    isDisabled={constraintType === "existence"}
                    label="Operator"
                    name="activationOperator"
                    size="sm"
                  >
                    <SelectItem key="<">{"<"}</SelectItem>
                    <SelectItem key="=">{"=="}</SelectItem>
                    <SelectItem key="!=">{"!="}</SelectItem>
                    <SelectItem key=">">{">"}</SelectItem>
                  </Select>
                  <Input
                    isDisabled={constraintType === "existence"}
                    label="Value"
                    name="activationValue"
                    size="sm"
                  />
                </div>
              </div>
              <div>
                <h4 className="text-xs font-medium text-neutral-500 mb-1">
                  Target Condition
                </h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                  <Input label="Parameter" name="targetParam" size="sm" />
                  <Select label="Operator" name="targetOperator" size="sm">
                    <SelectItem key="<">{"<"}</SelectItem>
                    <SelectItem key="=">{"=="}</SelectItem>
                    <SelectItem key="!=">{"!="}</SelectItem>
                    <SelectItem key=">">{">"}</SelectItem>
                  </Select>
                  <Input label="Value" name="targetValue" size="sm" />
                </div>
              </div>
              <div className="space-y-2">
                <h4 className="text-xs font-medium text-neutral-500 mb-1">
                  Correlation Condition
                </h4>
                <div className="grid grid-cols-3 gap-2">
                  <Input
                    label="Act. Param."
                    name="correlationActivationParam"
                    size="sm"
                  />
                  <Select label="Operator" name="correlationOperator" size="sm">
                    <SelectItem key="<">{"<"}</SelectItem>
                    <SelectItem key="=">{"=="}</SelectItem>
                    <SelectItem key="!=">{"!="}</SelectItem>
                    <SelectItem key=">">{">"}</SelectItem>
                  </Select>
                  <Input
                    label="Tar. Param."
                    name="correlationTargetParam"
                    size="sm"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
