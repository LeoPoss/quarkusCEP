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
library(hablar)


font_add_google("Roboto Condensed")
font_add_google("Barlow Condensed")
font_add_google("Roboto Mono")
showtext_auto()

# ── Resource plots (CPU / Memory time-series) ──────────────────────────────────

list.files(pattern = "complexity_\\d+_\\w+_resources\\.csv",
           full.names = TRUE)

line_types <- c("solid", "dashed", "dotted", "dotdash", "longdash", "twodash", "solid", "dashed", "dotted", "dotdash")

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
  ) %>%
  dplyr::group_by(scenario) %>%
  dplyr::mutate(pct = elapsed_time_s / max(elapsed_time_s) * 100) %>%
  dplyr::ungroup()

a <- ggplot(plot_data, aes(x = pct, y = .data[["cpu_percent"]], color = scenario,  linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "CPU Usage",
    x = "Test progress (%)",
    color = "Scenario"
  ) +
  scale_color_viridis_d(name = "Scenario") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  theme_ipsum_rc() +
  scale_x_continuous(
    breaks = seq(0, 100, 20),
    labels = scales::label_percent(scale = 1),
    expand = c(0, 0)
  )
  scale_y_continuous(labels = label_percent(scale=1))+
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0,0,0,0)
  )


b <- ggplot(plot_data, aes(x = pct, y = .data[["memory_mb"]], color = scenario,  linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Test progress (%)",
    color = "Scenario"
  ) +
  scale_x_continuous(
    breaks = seq(0, 100, 20),
    labels = scales::label_percent(scale = 1),
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


# ── Latency percentile bar chart (per statement type) ──────────────────────────

percentiles_to_calc <- c(0.50, 0.90, 0.95, 0.99, 0.999)

percentile_data_long <- list.files(
  pattern = "complexity_\\d+.*_latency_sample\\.csv$",
  full.names = TRUE
) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     scenario = stringr::str_extract(basename(.x), "(?<=complexity_).*(?=_latency_sample\\.csv)")
                   ))

# Backward compat: add statement_type and constraint_type if missing
if (!("statement_type" %in% colnames(percentile_data_long))) {
  percentile_data_long <- percentile_data_long %>% dplyr::mutate(statement_type = "UNKNOWN")
}
if (!("constraint_type" %in% colnames(percentile_data_long))) {
  percentile_data_long <- percentile_data_long %>%
    dplyr::mutate(
      constraint_type = stringr::str_extract(constraint, "^[A-Za-z]+") %>% tolower()
    )
}

percentile_data_long <- percentile_data_long %>%
  dplyr::mutate(
    statement_type = factor(statement_type,
      levels = c("FULFILLMENT", "TEMPORARY_VIOLATION", "PERMANENT_VIOLATION"))
  ) %>%
  dplyr::filter(!(constraint_type == "precedence" & statement_type == "FULFILLMENT"))


# ── 1) Global percentile bars (all types pooled) ──

percentile_global <- percentile_data_long %>%
  dplyr::group_by(scenario) %>%
  dplyr::summarize(
    `p50 (Median)` = quantile(latency_ms, percentiles_to_calc[1], na.rm = TRUE),
    `p90`          = quantile(latency_ms, percentiles_to_calc[2], na.rm = TRUE),
    `p95`          = quantile(latency_ms, percentiles_to_calc[3], na.rm = TRUE),
    `p99`          = quantile(latency_ms, percentiles_to_calc[4], na.rm = TRUE),
    `p99.9`        = quantile(latency_ms, percentiles_to_calc[5], na.rm = TRUE),
    .groups = "drop"
  ) %>%
  tidyr::pivot_longer(
    cols = -scenario,
    names_to = "Percentile",
    values_to = "Latency (ms)"
  ) %>%
  mutate(Percentile = fct_inorder(Percentile))

print(percentile_global)

ggplot(
  percentile_global,
  aes(x = scenario, y = `Latency (ms)`, fill = Percentile)
) +
  geom_col(position = "dodge") +
  labs(
    title = "Latency Percentiles by Test Scenario (all types pooled)",
    x = "Test Scenario",
    y = "Latency (ms)",
    fill = "Percentile"
  ) +
  theme_ipsum_rc() +
  theme(
    axis.text.x = element_text(angle = 45, hjust = 1)
  )


# ── 2) Faceted percentile bars by statement_type ──

percentile_by_st <- percentile_data_long %>%
  dplyr::group_by(scenario, statement_type) %>%
  dplyr::summarize(
    `p50 (Median)` = quantile(latency_ms, percentiles_to_calc[1], na.rm = TRUE),
    `p90`          = quantile(latency_ms, percentiles_to_calc[2], na.rm = TRUE),
    `p95`          = quantile(latency_ms, percentiles_to_calc[3], na.rm = TRUE),
    `p99`          = quantile(latency_ms, percentiles_to_calc[4], na.rm = TRUE),
    `p99.9`        = quantile(latency_ms, percentiles_to_calc[5], na.rm = TRUE),
    .groups = "drop"
  ) %>%
  tidyr::pivot_longer(
    cols = -c(scenario, statement_type),
    names_to = "Percentile",
    values_to = "Latency (ms)"
  ) %>%
  mutate(Percentile = fct_inorder(Percentile))

ggplot(
  percentile_by_st,
  aes(x = scenario, y = `Latency (ms)`, fill = Percentile)
) +
  geom_col(position = "dodge") +
  facet_wrap(~ statement_type, scales = "free_y", ncol = 1) +
  labs(
    title = "Latency Percentiles by Statement Type",
    subtitle = "FULFILLMENT = target→fire | TEMP_VIO = event→detect | PERM_VIO = activation→timeout→detect",
    x = "Test Scenario",
    y = "Latency (ms)",
    fill = "Percentile",
    caption = "PERMANENT_VIOLATION latency includes timeout. Scales are free per facet."
  ) +
  theme_ipsum_rc() +
  theme(
    axis.text.x = element_text(angle = 45, hjust = 1),
    strip.text = element_text(face = "bold", size = 11)
  )


# ── 3) Boxplot by scenario, faceted by statement_type ──

raw_latency_data <- list.files(
  pattern = "complexity_\\d+.*_latency_sample\\.csv$",
  full.names = TRUE
) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     scenario = stringr::str_extract(basename(.x), "(?<=complexity_).*(?=_latency_sample\\.csv)")
                   ))

if (!("statement_type" %in% colnames(raw_latency_data))) {
  raw_latency_data <- raw_latency_data %>% dplyr::mutate(statement_type = "UNKNOWN")
}
if (!("constraint_type" %in% colnames(raw_latency_data))) {
  raw_latency_data <- raw_latency_data %>%
    dplyr::mutate(
      constraint_type = stringr::str_extract(constraint, "^[A-Za-z]+") %>% tolower()
    )
}

raw_latency_data <- raw_latency_data %>%
  dplyr::mutate(
    scenario = forcats::fct_reorder(
      .f = scenario,
      .x = as.numeric(stringr::str_extract(scenario, "^\\d+"))
    ),
    statement_type = factor(statement_type,
      levels = c("FULFILLMENT", "TEMPORARY_VIOLATION", "PERMANENT_VIOLATION"))
  ) %>%
  dplyr::filter(!(constraint_type == "precedence" & statement_type == "FULFILLMENT"))

ggplot(raw_latency_data, aes(x = scenario, y = latency_ms, fill = scenario)) +
  geom_boxplot() +
  scale_y_continuous(labels = scales::label_number()) +
  coord_cartesian(ylim = c(0, 5)) +
  scale_fill_viridis_d() +
  facet_wrap(~ statement_type, scales = "free_y", ncol = 1) +
  coord_flip() +
  labs(
    title = "Latency Distribution by Statement Type",
    x = "Test Scenario",
    y = "Latency (ms)"
  ) +
  theme_ipsum_rc() +
  theme(legend.position = "none",
        axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
        axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
        text = element_text(family = "Roboto Condensed"),
        plot.margin = margin(0,5,0,0),
        strip.text = element_text(face = "bold", size = 11))


# ── 4) Per constraint-type × statement-type heatmap ──

ct_st_summary <- raw_latency_data %>%
  dplyr::group_by(scenario, constraint_type, statement_type) %>%
  dplyr::summarise(
    mean_latency = mean(latency_ms, na.rm = TRUE),
    n = n(),
    .groups = 'drop'
  )

ggplot(ct_st_summary,
  aes(x = constraint_type, y = statement_type, fill = mean_latency)) +
  geom_tile(color = "white", linewidth = 0.5) +
  geom_text(aes(label = sprintf("%.1f\n(n=%d)", mean_latency, n)),
            size = 2.8, family = "Roboto Mono") +
  scale_fill_viridis_c(
    name = "Mean Latency (ms)",
    option = "magma",
    labels = scales::label_number()
  ) +
  facet_wrap(~ scenario) +
  labs(
    title = "Mean Latency: Constraint Type × Statement Type",
    x = "Constraint Type",
    y = "Statement Type"
  ) +
  theme_ipsum_rc() +
  theme(
    axis.text.x = element_text(angle = 45, hjust = 1, family = "Roboto Mono", size = 8),
    axis.text.y = element_text(family = "Roboto Mono", size = 9),
    text = element_text(family = "Roboto Condensed"),
    legend.position = "bottom"
  )


# ── 5) Latency summary table per statement type ──

st_summary_table <- raw_latency_data %>%
  dplyr::group_by(scenario, statement_type) %>%
  dplyr::summarise(
    min    = min(latency_ms, na.rm = TRUE),
    median = median(latency_ms, na.rm = TRUE),
    mean   = mean(latency_ms, na.rm = TRUE),
    max    = max(latency_ms, na.rm = TRUE),
    p95    = quantile(latency_ms, 0.95, na.rm = TRUE),
    p99    = quantile(latency_ms, 0.99, na.rm = TRUE),
    count  = n(),
    .groups = 'drop'
  )

print("\nLatency Summary by Statement Type:")
print(knitr::kable(st_summary_table, format = "simple", digits = 2))


# ── Constraint count scalability (hardcoded data) ──────────────────────────────

library(xtable)
library(hms)

read_csv("complexity_results.csv", show_col_types = FALSE) %>%
  mutate(across(where(is.numeric), ~ round(.x, 2))) %>%
  xtable(auto = TRUE)

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
