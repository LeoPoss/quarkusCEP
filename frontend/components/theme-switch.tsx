"use client";

import { FC, useEffect, useState } from "react";
import { useTheme } from "next-themes";
import clsx from "clsx";

import { MoonFilledIcon, SunFilledIcon } from "@/components/icons";

export interface ThemeSwitchProps {
  className?: string;
  classNames?: {
    base?: string;
    wrapper?: string;
  };
}

export const ThemeSwitch: FC<ThemeSwitchProps> = ({
  className,
  classNames,
}) => {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return (
      <button
        aria-label="Loading theme"
        className={clsx(
          "px-px transition-opacity hover:opacity-80 cursor-pointer bg-transparent border-0",
          className,
          classNames?.base,
        )}
      >
        <div
          className={clsx(
            "w-auto h-auto bg-transparent rounded-lg flex items-center justify-center !text-default-500 pt-px px-0 mx-0",
            classNames?.wrapper,
          )}
        >
          <SunFilledIcon size={22} />
        </div>
      </button>
    );
  }

  const isDark = resolvedTheme === "dark";

  return (
    <button
      onClick={() => setTheme(isDark ? "light" : "dark")}
      aria-label={`Switch to ${isDark ? "light" : "dark"} mode`}
      className={clsx(
        "px-px transition-opacity hover:opacity-80 cursor-pointer bg-transparent border-0",
        className,
        classNames?.base,
      )}
    >
      <div
        className={clsx(
          "w-auto h-auto bg-transparent rounded-lg flex items-center justify-center !text-default-500 pt-px px-0 mx-0",
          classNames?.wrapper,
        )}
      >
        {isDark ? (
          <SunFilledIcon size={22} />
        ) : (
          <MoonFilledIcon size={22} />
        )}
      </div>
    </button>
  );
};
