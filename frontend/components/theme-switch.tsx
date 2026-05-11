"use client";

import { FC } from "react";
import { VisuallyHidden } from "@react-aria/visually-hidden";
import { Switch } from "@heroui/react";
import { useTheme } from "next-themes";
import { useIsSSR } from "@react-aria/ssr";
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
  const { theme, setTheme } = useTheme();
  const isSSR = useIsSSR();

  const onChange = () => {
    theme === "light" ? setTheme("dark") : setTheme("light");
  };

  const isSelected = theme === "light" || isSSR;

  return (
    <Switch
      isSelected={isSelected}
      aria-label={`Switch to ${isSelected ? "dark" : "light"} mode`}
      onChange={onChange}
      className={clsx(
        "px-px transition-opacity hover:opacity-80 cursor-pointer",
        className,
        classNames?.base,
      )}
    >
      <Switch.Control
        className={clsx(
          "w-auto h-auto bg-transparent rounded-lg flex items-center justify-center data-[selected=true]:bg-transparent !text-default-500 pt-px px-0 mx-0",
          classNames?.wrapper,
        )}
      >
        {isSelected ? (
          <SunFilledIcon size={22} />
        ) : (
          <MoonFilledIcon size={22} />
        )}
      </Switch.Control>
    </Switch>
  );
};
