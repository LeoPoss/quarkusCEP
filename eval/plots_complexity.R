library(tidyverse)
library(readr)
library(lubridate)
library(scales)
library(hrbrthemes)
library(ggplot2)
library(viridis)
library(patchwork)
library(showtext)
library(readxl)
library(patchwork)
library(hablar)


font_add_google("Roboto Condensed")
font_add_google("Barlow Condensed")
font_add_google("Roboto Mono")
showtext_auto()

list.files(pattern = "complexity_\\d+_\\w+_resources\\.csv",
           full.names = TRUE)

line_types <- c("dashed", "dotted","solid", "dotdash", "twodash")

plot_data <- list.files(pattern = "complexity_\\d+_\\w+_resources\\.csv",
                        full.names = TRUE) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(filename = basename(.x))) %>%
  dplyr::mutate(
    scenario = stringr::str_extract(filename, "(?<=complexity_).*(?=_resources\\.csv)"),
    
    scenario = forcats::fct_reorder(
      .f = scenario, 
      .x = as.numeric(stringr::str_extract(scenario, "^\\d+"))
    )
  )

a <- ggplot(plot_data, aes(x = elapsed_time_s, y = .data[["cpu_percent"]], color = scenario,  linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "CPU Usage",
    x = "Time (seconds)",
    color = "Scenario"
  ) +
  scale_color_viridis_d(name = "Scenario") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  theme_ipsum_rc() +
  scale_x_continuous(expand=c(0,0))+
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  scale_y_continuous(labels = label_percent(scale=1))+
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0,0,0,0)
  )


b <- ggplot(plot_data, aes(x = elapsed_time_s, y = .data[["memory_mb"]], color = scenario,  linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Time (seconds)",
    color = "Scenario"
  ) +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  coord_cartesian(ylim = c(0, NA)) +
  scale_color_viridis_d(name = "Scenario") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  theme_ipsum_rc() +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0,0,0,25)
  )

a + b +
  plot_layout(guides = 'collect') &
  theme(legend.position='bottom')

percentiles_to_calc <- c(0.50, 0.90, 0.95, 0.99, 0.999)

percentile_data_long <- list.files(
  pattern = "complexity_\\d+.*_latency\\.csv$",
  full.names = TRUE
) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     scenario = stringr::str_extract(basename(.x), "(?<=complexity_).*(?=_latency\\.csv)")
                   )) %>%
  dplyr::group_by(scenario) %>%
  dplyr::summarize(
    `p50 (Median)` = quantile(latency_ms, percentiles_to_calc[1], na.rm = TRUE),
    `p90` = quantile(latency_ms, percentiles_to_calc[2], na.rm = TRUE),
    `p95` = quantile(latency_ms, percentiles_to_calc[3], na.rm = TRUE),
    `p99` = quantile(latency_ms, percentiles_to_calc[4], na.rm = TRUE),
    `p99.9` = quantile(latency_ms, percentiles_to_calc[5], na.rm = TRUE),
    .groups = "drop"
  ) %>%
  tidyr::pivot_longer(
    cols = -scenario,
    names_to = "Percentile",
    values_to = "Latency (ms)"
  ) %>%
  mutate(Percentile = fct_inorder(Percentile))

percentile_data_long
library(ggplot2)
library(hrbrthemes)

ggplot(
  percentile_data_long, 
  aes(x = scenario, y = `Latency (ms)`, fill = Percentile)
) +
  geom_col(position = "dodge") +
  labs(
    title = "Latency Percentiles by Test Scenario",
    x = "Test Scenario",
    y = "Latency (ms)",
    fill = "Percentile"
  ) +
  theme_ipsum_rc() +
  theme(
    axis.text.x = element_text(angle = 45, hjust = 1)
  )




library(tidyverse)
library(hrbrthemes)

raw_latency_data <- list.files(
  pattern = "complexity_\\d+.*_latency\\.csv$",
  full.names = TRUE
) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     scenario = stringr::str_extract(basename(.x), "(?<=complexity_).*(?=_latency\\.csv)")
                   ))

raw_latency_data <- raw_latency_data %>%
  mutate(
    scenario = forcats::fct_reorder(
      .f = scenario, 
      .x = as.numeric(stringr::str_extract(scenario, "^\\d+")),
    )
  )

ggplot(raw_latency_data, aes(x = scenario, y = latency_ms, fill = scenario)) +
  geom_boxplot() +
  scale_y_log10(labels = scales::label_number()) +
  scale_fill_viridis_d()
  annotation_logticks(sides = "b") + 
  coord_flip() +
  labs(
    x = "Test Scenario",
    y = "Latency (ms) [Log Scale]"
  ) +
  theme_ipsum_rc() +
  theme(legend.position = "none",
        axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
        axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
        text = element_text(family = "Roboto Condensed"),
        plot.margin = margin(0,5,0,0))











library(xtable)

read_csv("complexity_results.csv", show_col_types = FALSE) %>%
  mutate(across(where(is.numeric), ~ round(.x, 2))) %>%
  xtable(auto = TRUE)





library(hms)   



# with 10000 constant events

# 400  Constraints = 584.07 MB (1:14 min)
# 2000 Constraints = 2007.95 MB (5:48 min)
# 4000 Constraints = 3673.23 MB (11:25 min)
# 4000 Constraints = 5771.17 MB (22:52 min)

library(ggplot2)
library(hrbrthemes)
library(scales)

df <- data.frame(
  Constraints = c(400, 2000, 4000, 8000),
  Memory_MB   = c(584.07, 2007.95, 3673.23, 5771.17),
  Time_sec    = c(74, 348, 685, 1372)
)

plot_mem <- ggplot(df, aes(x = Constraints, y = Memory_MB)) +
  geom_point(size = 3, color = "#0072B2") +
  geom_smooth(method = "lm", se = FALSE, color = "#0072B2", alpha = 0.5) +
  labs(y = "Memory (MB)", x = NULL) + theme_ipsum_rc() +
  scale_x_continuous(expand=c(0,0))+
  coord_cartesian(ylim = c(0, NA), xlim = c(0, NA)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed")
  )

plot_time <- ggplot(df, aes(x = Constraints, y = Time_sec)) +
  geom_point(size = 3, color = "#D55E00") +
  geom_smooth(method = "lm", se = FALSE, color = "#D55E00", alpha = 0.5) +
  labs(y = "Time (sec)", x = NULL) + theme_ipsum_rc() +
  scale_x_continuous(expand=c(0,0))+
  coord_cartesian(ylim = c(0, NA), xlim = c(0, NA)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed")
  )

(plot_mem + plot_time)

df %>%  mutate(across(where(is.numeric), ~ round(.x, 2))) %>%
  xtable(auto = TRUE)
