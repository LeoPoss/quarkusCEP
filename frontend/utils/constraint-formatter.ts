
export interface Constraint {
    name: string;
    type: string;
    status: string;
    timer?: number;
    activationEvent?: { name: string; type: string };
    targetEvent?: { name: string; type: string };
    activationCondition?: { param: string; operator: string; value: string; timer?: number };
    targetCondition?: { param: string; operator: string; value: string; timer?: number };
    eplStatements?: { deploymentId: string; statement: string; type: string }[];
}

export function formatConstraint(c: Constraint): string {
    const type = capitalize(c.type);

    // Timer formatting: [0, timer] or just empty if null
    const constraintTimer = c.timer ? `[0,${c.timer}]` : "";

    // Event formatting: Name(Type)
    const formatEvent = (name?: string, type?: string) => {
        if (!name) return "?";
        const typeChar = type === "signal" ? "s" : "t"; // Default to 't' for task or unknown
        return `${name}(${typeChar})`;
    };

    // Condition formatting: Param Op Value [0,Timer]
    // If param matches event name (common case), maybe simplify? For now, explicit.
    // User example: temp(t)< 50[0,20] -> This implies the Condition is merged with Event.
    // Let's try to append condition logic to the event string.
    const formatPart = (
        evtName?: string,
        evtType?: string,
        cond?: { param: string; operator: string; value: string; timer?: number }
    ) => {
        let base = formatEvent(evtName, evtType);

        if (cond) {
            // Check if condition is on the event itself (param == event name) or generic
            // User example: temp(t)< 50 ... likely param is 'temp'
            if (cond.operator && cond.value) {
                base += `${cond.operator}${cond.value}`;
            }
            if (cond.timer) {
                base += `[0,${cond.timer}]`;
            }
        }
        return base;
    };

    const partA = formatPart(c.activationEvent?.name, c.activationEvent?.type, c.activationCondition);
    const partB = formatPart(c.targetEvent?.name, c.targetEvent?.type, c.targetCondition);

    // Some constraints only have A (Existence, NotExistence)
    if (["Existence", "Notexistence", "Not_existence"].includes(type)) {
        // Usually target logic applies here as 'A'? Or is it Target? 
        // In the QuickCreate, singleEventConstraints use 'activationEvent' or 'targetEvent'?
        // The quick create uses targetEvent for single event constraints in the debug seed for NotExist?
        // Wait, QuickCreate: "singleEventConstraints use target = activation?" No.
        // Let's rely on what's present. If Target is present, usage it.
        // Actually, for Existence(A), usually A is the target to check existence of.
        const subject = c.targetEvent?.name ? partB : partA;
        return `${type}${constraintTimer}(${subject})`;
    }

    return `${type}${constraintTimer}(${partA}, ${partB})`;
}

function capitalize(s: string): string {
    if (!s) return "";
    return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase().replace("_", "");
}
