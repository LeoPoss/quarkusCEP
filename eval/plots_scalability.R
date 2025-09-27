setwd("../Desktop/Projekte/quarkuscep/eval/2CPU4RAM")

setwd("2CPU4RAM")
getwd()

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

plot_data <- list.files(pattern = "scalability_\\d+eps_resources\\.csv",
                        full.names = TRUE) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(filename = basename(.x))) %>%
  dplyr::mutate(
    scenario = stringr::str_extract(filename, "\\d+eps"),
    scenario = stringr::str_replace(scenario, "eps", " events/s"),
    scenario = forcats::fct_reorder(
      .f = scenario,
      .x = as.numeric(stringr::str_extract(scenario, "\\d+")),
      .desc = FALSE
    )
  )

line_types <- c("dashed", "dotted","solid", "dotdash", "twodash")

a <- ggplot(plot_data, aes(x = elapsed_time_s, y = .data[["cpu_percent"]], color = scenario, linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "CPU usage",
    x = "Time (seconds)"
  ) +
  theme_ipsum_rc() +
  scale_color_viridis_d(name = "Scenario") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  scale_y_continuous(labels = percent_format(scale = 1)) +
  coord_cartesian(ylim = c(0, NA)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin()
  )

b <- ggplot(plot_data, aes(x = elapsed_time_s, y = .data[["memory_mb"]], color = scenario, linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Time (seconds)"
  ) +
  theme_ipsum_rc() +
  scale_color_viridis_d(name = "Scenario") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  coord_cartesian(ylim = c(0, 4096)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0, 0, 0, 25)
  )

a + b +
  plot_layout(guides = 'collect') &
  theme(legend.position = 'bottom')

list_of_dfs <- plot_data %>%
  group_by(filename) %>%
  group_split()


b1 <- ggplot(list_of_dfs[[1]], aes(x = elapsed_time_s, y = .data[["memory_mb"]], color = scenario, linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Time (seconds)"
  ) +
  theme_ipsum_rc() +
  scale_color_viridis_d(name = "Scenario") +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  coord_cartesian(ylim = c(0, 1024)) +
  theme(
    legend.position = "",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0, 0, 0, 0)
  )


b2 <- ggplot(list_of_dfs[[2]], aes(x = elapsed_time_s, y = .data[["memory_mb"]], color = scenario, linetype = scenario)) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Time (mins)"
  ) +
  theme_ipsum_rc() +
  scale_color_viridis_d(name = "Scenario") +
  scale_x_time(
    breaks = scales::breaks_width("60 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  coord_cartesian(ylim = c(0, 4096)) +
  theme(
    legend.position = "",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0, 0, 0, 25)
  )



b1+b2








library(scales)

plot_data <- list.files(
  pattern = "scalability_\\d+eps_latency\\.csv$",
  full.names = TRUE
) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     event_rate_str = stringr::str_extract(basename(.x), "\\d+")
                   )) %>%
  dplyr::mutate(
    event_rate = forcats::fct_reorder(
      .f = paste(event_rate_str, "events/sec"),
      .x = as.numeric(event_rate_str),
      .desc = FALSE
    )
  )
ggplot(plot_data, aes(x = latency_ms, fill = event_rate)) +
  geom_histogram(
    aes(y = after_stat(density)),
    alpha = 0.6,
    binwidth = 1,
    position = "identity"
  ) +
  scale_y_log10(
    labels = label_log(digits = 2),
    breaks = 10^seq(-5, 0)
  ) +
  scale_fill_ipsum() +
  labs(
    title = "Latency Distribution by Event Rate",
    x = "Latency (ms) [Bin size: 1 ms]",
    y = "Probability Density",
    fill = "Event Rate"
  ) +
  theme_ipsum_rc() +
  theme(
    legend.position = "right",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed")
  )






log_breaks <- 10^seq(-2, 3, length.out = 80)
breaks <- c(0, log_breaks)

heatmap_data <- plot_data %>%
  mutate(latency_bin = cut(latency_ms, breaks = breaks, right = FALSE)) %>%
  drop_na(latency_bin) %>%
  tidyr::complete(event_rate_str, latency_bin, fill = list(count = 0)) %>%
  count(event_rate_str, latency_bin, name = "count") %>%
  mutate(
    event_rate = forcats::fct_reorder(
      .f = paste(event_rate_str, "events/sec"),
      .x = as.numeric(event_rate_str)
    ) %>% forcats::fct_rev()
  )

x_axis_labels <- levels(heatmap_data$latency_bin)
labels_to_show <- x_axis_labels[seq(1, length(x_axis_labels), by = 10)]

ggplot(heatmap_data, aes(x = latency_bin, y = event_rate, fill = count)) +
  geom_tile() +
  scale_fill_viridis_c(
    option = "magma",
    trans = "log10",
    na.value = "black",
    labels = label_log(base = 10)
  ) +
  scale_x_discrete(
    breaks = labels_to_show,
    labels = ~ format(as.numeric(str_extract(.x, "(?<=^\\[)[0-9\\.]+")), digits = 2)
  ) +  
  coord_cartesian(expand = FALSE) +
  labs(
    x = "Latency (ms) [Log Scale Bins]",
    y = "Event Rate (events/sec)",
    fill = ""
  ) +
  theme_ipsum_rc() +
  theme(
    legend.position = "right",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed")
  )


percentiles_to_calc <- c(0.50, 0.90, 0.95, 0.99, 0.999)

percentile_data_long <- list.files(
  pattern = "scalability_\\d+eps_latency\\.csv$",
  full.names = TRUE
) %>%
  stringr::str_sort(numeric = TRUE) %>%
  purrr::map_dfr(~ readr::read_csv(.x, show_col_types = FALSE) %>%
                   dplyr::mutate(
                     event_rate = as.numeric(stringr::str_extract(basename(.x), "\\d+"))
                   )) %>%
  dplyr::group_by(event_rate) %>%
  dplyr::summarize(
    `p50 (Median)` = quantile(latency_ms, percentiles_to_calc[1], na.rm = TRUE),
    `p90` = quantile(latency_ms, percentiles_to_calc[2], na.rm = TRUE),
    `p95` = quantile(latency_ms, percentiles_to_calc[3], na.rm = TRUE),
    `p99` = quantile(latency_ms, percentiles_to_calc[4], na.rm = TRUE),
    `p99.9` = quantile(latency_ms, percentiles_to_calc[5], na.rm = TRUE),
    .groups = "drop"
  ) %>%
  tidyr::pivot_longer(
    cols = -event_rate,
    names_to = "Percentile",
    values_to = "Latency (ms)"
  ) %>%
  mutate(Percentile = fct_inorder(Percentile))

ggplot(
  percentile_data_long,
  aes(x = event_rate, y = `Latency (ms)`, color = Percentile, linetype = Percentile)
) +
  geom_line(linewidth = 1.2) +
  annotation_logticks(sides = "lb") +
  scale_x_log10(labels = scales::label_number(), expand=c(0.05,0)) +
  scale_y_log10(labels = scales::label_number()) +
  scale_color_viridis_d()+
  scale_linetype_manual(name = "Percentile", values = line_types) +
  labs(
    x = "Event Rate (events/sec) [Log Scale]",
    y = "Latency (ms) [Log Scale]",
  ) +
  theme_ipsum_rc() +
  theme(
    panel.grid.major = element_line(linetype = "dashed", linewidth = 0.5, color = "gray80"),
    panel.grid.minor = element_line(linetype = "dashed", linewidth = 0.25, color = "gray85"),
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin()
  )


library(xtable)

read_csv("scalability_results.csv", show_col_types = FALSE) %>%
  mutate(across(where(is.numeric), ~ round(.x, 2))) %>%
  xtable(auto = TRUE)

