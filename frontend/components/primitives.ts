import { tv } from "tailwind-variants";

export const title = tv({
  base: "tracking-tight inline font-bold",
  variants: {
    size: {
      sm: "text-2xl lg:text-3xl",
      md: "text-3xl lg:text-4xl",
      lg: "text-4xl lg:text-5xl",
    },
    fullWidth: {
      true: "w-full block",
    },
  },
  defaultVariants: {
    size: "md",
  },
});
