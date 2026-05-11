"use client";

import { useState } from "react";
import { Link } from "@heroui/react";
import NextLink from "next/link";
import clsx from "clsx";

import { siteConfig } from "@/config/site";
import { ThemeSwitch } from "@/components/theme-switch";

export const Navbar = () => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <nav className="sticky top-0 z-40 w-full border-b border-divider bg-background/70 backdrop-blur-lg">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <div className="flex basis-1/5 items-center gap-3 sm:basis-full">
          <div className="flex items-center gap-3 max-w-fit">
            <NextLink className="flex justify-start items-center gap-1 text-inherit" href="/">
              <p className="font-bold">{siteConfig.name}</p>
            </NextLink>
          </div>
          <ul className="hidden md:flex gap-4 justify-start ml-2">
            {siteConfig.navItems.map((item) => (
              <li key={item.href}>
                <NextLink
                  className={clsx(
                    "text-foreground transition-colors hover:text-accent data-[active=true]:text-accent data-[active=true]:font-medium text-sm",
                  )}
                  href={item.href}
                >
                  {item.label}
                </NextLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="hidden sm:flex basis-1/5 sm:basis-full items-center justify-end gap-2">
          <ThemeSwitch />
        </div>

        <div className="sm:hidden flex basis-1 items-center justify-end gap-2 pl-4">
          <ThemeSwitch />
          <button
            className="flex items-center justify-center rounded-md p-2 text-foreground transition-colors hover:bg-default-100"
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            aria-label="Toggle menu"
            aria-expanded={isMenuOpen}
          >
            <svg
              className="h-6 w-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              {isMenuOpen ? (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              ) : (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 6h16M4 12h16M4 18h16"
                />
              )}
            </svg>
          </button>
        </div>
      </div>

      {isMenuOpen && (
        <div className="border-t border-divider bg-background sm:hidden">
          <div className="mx-4 mt-2 flex flex-col gap-2 py-4">
            {siteConfig.navMenuItems.map((item, index) => (
              <div key={`${item.href}-${index}`}>
                <NextLink 
                  href={item.href}
                  className="block py-2 text-foreground hover:text-accent"
                  onClick={() => setIsMenuOpen(false)}
                >
                  {item.label}
                </NextLink>
              </div>
            ))}
          </div>
        </div>
      )}
    </nav>
  );
};
