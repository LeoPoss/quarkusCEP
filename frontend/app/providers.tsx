"use client";

import type { ThemeProviderProps } from "next-themes";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import * as React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toast } from "@heroui/react";

import { MPDeclareProvider } from "../contexts/mpDeclareContext";

export interface ProvidersProps {
  children: React.ReactNode;
  themeProps?: ThemeProviderProps;
}

const queryClient = new QueryClient();

export function Providers({ children, themeProps }: ProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <Toast.Provider />
      <MPDeclareProvider>
        <NextThemesProvider {...themeProps}>{children}</NextThemesProvider>
      </MPDeclareProvider>
    </QueryClientProvider>
  );
}
