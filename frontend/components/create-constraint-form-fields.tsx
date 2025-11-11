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
  const [activationEventType, setActivationEventType] =
    React.useState("signal");
  const [targetEventType, setTargetEventType] = React.useState("task");
  return (
    <div className="grid grid-cols-2 gap-x-4">
      <div className="flex flex-col gap-y-4">
        <div className="grid grid-cols-3 gap-4 col-span-3">
          <Input
            isRequired
            label="Constraint Name"
            name="name"
            size="sm"
            className="w-full"
          />
          <Select
            isRequired
            disabledKeys={[
              "alternateResponse",
              "alternatePrecedence",
              "chainPrecedence",
              "notPrecedence",
            ]}
            label="Constraint Type"
            name="type"
            size="sm"
            className="w-full"
            onChange={(e) => setConstraintType(e.target.value)}
          >
            <SelectSection title="Existence Constraints">
              <SelectItem key="existence">Existence</SelectItem>
              <SelectItem key="notexistence">NotExistence</SelectItem>
            </SelectSection>
            <SelectSection title="Relation Constraints">
              <SelectItem key="respondedExistence">
                Responded Existence
              </SelectItem>
              <SelectItem key="response">Response</SelectItem>
              {/*<SelectItem key="alternateResponse">
                Alternate Response
              </SelectItem>
              <SelectItem key="chainResponse">Chain Response</SelectItem>*/}
              <SelectItem key="precedence">Precedence</SelectItem>
              {/*              <SelectItem key="alternatePrecedence">
                Alternate Precedence
              </SelectItem>
              <SelectItem key="chainPrecedence">Chain Precedence</SelectItem>*/}
            </SelectSection>
            <SelectSection title="Negative Relation Constraints">
              <SelectItem key="notResponse">Not Response</SelectItem>
              {/*<SelectItem key="notPrecedence">Not Precedence</SelectItem>*/}
            </SelectSection>
          </Select>
          <Input
            label="Constraint Timer"
            name="timer"
            size="sm"
            className="w-full"
          />
        </div>
        <div className="col-span-3 grid gap-y-4">
          <div className="space-y-2">
            <div className="flex items-end gap-2">
              <Input
                isDisabled={
                  constraintType === "existence" ||
                  constraintType === "notexistence"
                }
                isRequired={
                  constraintType !== "existence" &&
                  constraintType !== "notexistence"
                }
                label="Activation Event"
                name="activationEvent"
                size="sm"
                className="flex-1"
                classNames={{
                  inputWrapper:
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                      ? "opacity-50"
                      : "",
                }}
              />
              <div className="flex flex-col gap-1">
                <div className="flex bg-default-100 dark:bg-default-50 p-1.5 rounded-md h-12 items-center">
                  <button
                    type="button"
                    className={`px-3 h-9 flex items-center text-sm rounded transition-colors cursor-pointer ${
                      activationEventType === "signal"
                        ? "bg-white dark:bg-default-100 shadow-sm text-foreground dark:text-foreground"
                        : "text-foreground-500 dark:text-foreground-400 hover:bg-default-200 dark:hover:bg-default-100"
                    } ${
                      constraintType === "existence" ||
                      constraintType === "notexistence"
                        ? "opacity-50"
                        : ""
                    }`}
                    onClick={() => setActivationEventType("signal")}
                    disabled={
                      constraintType === "existence" ||
                      constraintType === "notexistence"
                    }
                  >
                    Signal
                  </button>
                  <button
                    type="button"
                    className={`px-3 h-9 flex items-center text-sm rounded transition-colors cursor-pointer ${
                      activationEventType === "task"
                        ? "bg-white dark:bg-default-100 shadow-sm text-foreground dark:text-foreground"
                        : "text-foreground-500 dark:text-foreground-400 hover:bg-default-200 dark:hover:bg-default-100"
                    } ${
                      constraintType === "existence" ||
                      constraintType === "notexistence"
                        ? "opacity-50"
                        : ""
                    }`}
                    onClick={() => setActivationEventType("task")}
                    disabled={
                      constraintType === "existence" ||
                      constraintType === "notexistence"
                    }
                  >
                    Task
                  </button>
                  <input
                    type="hidden"
                    name="activationEventType"
                    value={activationEventType}
                  />
                </div>
              </div>
            </div>
          </div>
          <div className="space-y-2">
            <div className="flex items-end gap-2">
              <Input
                isRequired
                label="Target Event"
                name="targetEvent"
                size="sm"
                className="flex-1"
              />
              <div className="flex flex-col gap-1">
                <div className="flex bg-default-100 dark:bg-default-50 p-1.5 rounded-md h-12 items-center">
                  <button
                    type="button"
                    className={`px-3 h-9 flex items-center text-sm rounded transition-colors cursor-pointer ${
                      targetEventType === "signal"
                        ? "bg-white dark:bg-default-100 shadow-sm text-foreground dark:text-foreground"
                        : "text-foreground-500 dark:text-foreground-400 hover:bg-default-200 dark:hover:bg-default-100"
                    }`}
                    onClick={() => setTargetEventType("signal")}
                  >
                    Signal
                  </button>
                  <button
                    type="button"
                    className={`px-3 h-9 flex items-center text-sm rounded transition-colors cursor-pointer ${
                      targetEventType === "task"
                        ? "bg-white dark:bg-default-100 shadow-sm text-foreground dark:text-foreground"
                        : "text-foreground-500 dark:text-foreground-400 hover:bg-default-200 dark:hover:bg-default-100"
                    }`}
                    onClick={() => setTargetEventType("task")}
                  >
                    Task
                  </button>
                </div>
              </div>
              <input
                type="hidden"
                name="targetEventType"
                value={targetEventType}
              />
            </div>
          </div>
        </div>
      </div>
      {isMPDeclareEnabled && (
        <div className="bg-blue-50 dark:bg-blue-900/30 rounded-2xl border border-blue-100 dark:border-blue-800/50 p-4 relative">
          <div className="absolute right-3 top-3 opacity-20 dark:opacity-10 text-5xl font-bold text-blue-400 dark:text-blue-300">
            MP-Declare
          </div>
          <div>
            <h3 className="text-sm font-medium mb-2">Conditions</h3>
            <div className="mt-2">
              <h4 className="text-xs font-medium text-neutral-500 dark:text-neutral-400 mb-1">
                Activation Condition
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
                <Input
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                  label="Parameter"
                  name="activationParam"
                  size="sm"
                />
                <Select
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
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
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                  label="Value"
                  name="activationValue"
                  size="sm"
                />
                <Input
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                  label="Cond. Timer"
                  name="activationTimer"
                  size="sm"
                />
              </div>
            </div>
            <div className="mt-2">
              <h4 className="text-xs font-medium text-neutral-500 dark:text-neutral-400 mb-1">
                Target Condition
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
                <Input label="Parameter" name="targetParam" size="sm" />
                <Select label="Operator" name="targetOperator" size="sm">
                  <SelectItem key="<">{"<"}</SelectItem>
                  <SelectItem key="=">{"=="}</SelectItem>
                  <SelectItem key="!=">{"!="}</SelectItem>
                  <SelectItem key=">">{">"}</SelectItem>
                </Select>
                <Input label="Value" name="targetValue" size="sm" />
                <Input label="Cond. Timer" name="targetTimer" size="sm" />
              </div>
            </div>
            <div className="mt-2">
              <h4 className="text-xs font-medium text-neutral-500 dark:text-neutral-400 mb-1">
                Correlation Condition
              </h4>
              <div className="grid grid-cols-3 gap-2">
                <Input
                  label="Act. Param."
                  name="correlationActivationParam"
                  size="sm"
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                />
                <Select
                  label="Operator"
                  name="correlationOperator"
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                  size="sm"
                >
                  <SelectItem key="<">{"<"}</SelectItem>
                  <SelectItem key="=">{"=="}</SelectItem>
                  <SelectItem key="!=">{"!="}</SelectItem>
                  <SelectItem key=">">{">"}</SelectItem>
                </Select>
                <Input
                  isDisabled={
                    constraintType === "existence" ||
                    constraintType === "notexistence"
                  }
                  label="Tar. Param."
                  name="correlationTargetParam"
                  size="sm"
                />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
