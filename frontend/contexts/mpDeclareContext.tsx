import { createContext, useContext, useState, ReactNode } from "react";

interface MPDeclareContextType {
  isMPDeclareEnabled: boolean;
  toggleMPDeclare: () => void;
}

const MPDeclareContext = createContext<MPDeclareContextType | undefined>(
  undefined,
);

export function MPDeclareProvider({ children }: { children: ReactNode }) {
  const [isMPDeclareEnabled, setIsMPDeclareEnabled] = useState(true);

  const toggleMPDeclare = () => {
    setIsMPDeclareEnabled((prev) => !prev);
  };

  return (
    <MPDeclareContext.Provider value={{ isMPDeclareEnabled, toggleMPDeclare }}>
      {children}
    </MPDeclareContext.Provider>
  );
}

export function useMPDeclare() {
  const context = useContext(MPDeclareContext);

  if (context === undefined) {
    throw new Error("useMPDeclare must be used within an MPDeclareProvider");
  }

  return context;
}
