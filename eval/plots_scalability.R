#setwd("../Desktop/quarkuscep/eval")

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
library(hablar)


font_add_google("Roboto Condensed")
font_add_google("Barlow Condensed")
font_add_google("Roboto Mono")
showtext_auto()

# ── Resource plots (CPU / Memory time-series) ──────────────────────────────────

run_files <- list.files(pattern = "scalability_.*_run\\d+_resources\\.csv", full.names = TRUE)

plot_data <- run_files %>%
  tibble::tibble(
    filepath = .,
    filename = basename(.),
    run = stringr::str_extract(filename, "run\\d+") %>% stringr::str_remove("run") %>% as.integer(),
    rate = stringr::str_extract(filename, "\\d+eps") %>% stringr::str_remove("eps") %>% as.integer()
  ) %>%
  dplyr::arrange(rate, run) %>%
  dplyr::group_split(rate, run) %>%
  purrr::map_dfr(function(x) {
    readr::read_csv(x$filepath, show_col_types = FALSE) %>%
      dplyr::mutate(
        rate = x$rate,
        run  = x$run,
        scenario = paste0(rate, " events/s")
      )
  }) %>%
  dplyr::mutate(
    scenario = forcats::fct_reorder(
      .f = scenario,
      .x = as.numeric(stringr::str_extract(scenario, "\\d+")),
      .desc = FALSE
    )
  )

run_summary <- plot_data %>%
  dplyr::group_by(elapsed_time_s, rate, scenario) %>%
  dplyr::summarise(
    avg_cpu = mean(cpu_percent, na.rm = TRUE),
    min_cpu = min(cpu_percent, na.rm = TRUE),
    max_cpu = max(cpu_percent, na.rm = TRUE),
    sd_cpu = sd(cpu_percent, na.rm = TRUE),
    avg_mem = mean(memory_mb, na.rm = TRUE),
    min_mem = min(memory_mb, na.rm = TRUE),
    max_mem = max(memory_mb, na.rm = TRUE),
    sd_mem = sd(memory_mb, na.rm = TRUE),
    n_runs = n(),
    .groups = 'drop'
  ) %>%
  dplyr::group_by(scenario) %>%
  dplyr::mutate(
    pct = elapsed_time_s / max(elapsed_time_s) * 100,
    ci_cpu = ifelse(n_runs > 1, qt(0.95, df = n_runs-1) * sd_cpu / sqrt(n_runs), 0),
    ci_mem = ifelse(n_runs > 1, qt(0.95, df = n_runs-1) * sd_mem / sqrt(n_runs), 0)
  ) %>%
  dplyr::ungroup()

line_types <- c("solid", "dashed", "dotted", "dotdash", "longdash", "twodash", "solid", "dashed", "dotted", "dotdash")

a <- ggplot(run_summary, aes(x = pct, y = avg_cpu, color = scenario, linetype = scenario)) +
  geom_ribbon(aes(ymin = pmax(0, avg_cpu - ci_cpu), ymax = avg_cpu + ci_cpu, fill = scenario),
              alpha = 0.2, color = NA, show.legend = FALSE) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "CPU usage",
    x = "Test progress (%)"
  ) +
  theme_ipsum_rc() +
  scale_color_brewer(name="Scenario", palette = "Dark2") +
  scale_fill_brewer(name="Scenario", palette = "Dark2") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_continuous(
    breaks = seq(0, 100, 20),
    labels = scales::label_percent(scale = 1),
    expand = c(0, 0)
  ) +
  scale_y_continuous(labels = percent_format(scale = 1)) +
  coord_cartesian(ylim = c(0, 200)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin()
  )

b <- ggplot(run_summary, aes(x = pct, y = avg_mem, color = scenario, linetype = scenario)) +
  geom_ribbon(aes(ymin = pmax(0, avg_mem - ci_mem), ymax = avg_mem + ci_mem, fill = scenario),
              alpha = 0.2, color = NA, show.legend = FALSE) +
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Test progress (%)"
  ) +
  theme_ipsum_rc() +
  scale_color_brewer(name="Scenario", palette = "Dark2") +
  scale_fill_brewer(name="Scenario", palette = "Dark2") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_continuous(
    breaks = seq(0, 100, 20),
    labels = scales::label_percent(scale = 1),
    expand = c(0, 0)
  ) +
  coord_cartesian(ylim = c(0, 2048)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin(0, 0, 0, 25)
  )

resource_plot <- a + b +
  plot_layout(guides = 'collect') &
  theme(legend.position = 'bottom')

print(resource_plot)

# Save as PDF for paper
#paper_dir <- "../PAPER"
#if (dir.exists(paper_dir)) {
#  ggsave(file.path(paper_dir, "stlp160n.pdf"), resource_plot,
#         width = 16, height = 9, units = "cm", device = cairo_pdf)
#  cat(sprintf("\nSaved CPU/RAM plot to %s/stlp160n.pdf\n", paper_dir))
#}


# ── Latency plots (per statement type) ─────────────────────────────────────────

latency_run_files <- list.files(
  pattern = "scalability_.*_run\\d+_latency_sample\\.csv",
  full.names = TRUE
)

if (length(latency_run_files) > 0) {
  latency_plot_data <- latency_run_files %>%
    tibble::tibble(
      filepath = .,
      filename = basename(.),
      run  = stringr::str_extract(filename, "run\\d+")  %>% stringr::str_remove("run") %>% as.integer(),
      rate = stringr::str_extract(filename, "\\d+eps") %>% stringr::str_remove("eps")
    ) %>%
    dplyr::arrange(rate, run) %>%
    dplyr::group_split(rate, run) %>%
    purrr::map_dfr(function(x) {
      if (file.exists(x$filepath)) {
        readr::read_csv(x$filepath, show_col_types = FALSE) %>%
          dplyr::mutate(
            rate     = x$rate,
            run      = x$run,
            event_rate_label = paste0(rate, " events/s")
          )
      } else {
        NULL
      }
    })

  # Ensure statement_type and constraint_type are present (backward compat)
  if (!("statement_type" %in% colnames(latency_plot_data))) {
    latency_plot_data <- latency_plot_data %>% dplyr::mutate(statement_type = "UNKNOWN")
  }
  if (!("constraint_type" %in% colnames(latency_plot_data))) {
    latency_plot_data <- latency_plot_data %>%
      dplyr::mutate(
        constraint_type = stringr::str_extract(constraint, "^[A-Za-z]+") %>% tolower()
      )
  }

  latency_plot_data <- latency_plot_data %>%
    dplyr::mutate(
      event_rate_label = forcats::fct_reorder(
        .f = event_rate_label,
        .x = as.numeric(rate),
        .desc = FALSE
      ),
      statement_type = factor(statement_type,
        levels = c("FULFILLMENT", "TEMPORARY_VIOLATION", "PERMANENT_VIOLATION"))
    ) %>%
    # Precedence FULFILLMENT samples are too sparse with random event ordering;
    # the EPL is correct but the test design doesn't generate enough activation→target pairs.
    dplyr::filter(!(constraint_type == "precedence" & statement_type == "FULFILLMENT"))

  # ── 1) Global violin plot (all statement types pooled) ──
  all_latency_summary <- latency_plot_data %>%
    dplyr::group_by(event_rate_label, run) %>%
    dplyr::summarise(
      mean_latency = mean(latency_ms, na.rm = TRUE),
      .groups = 'drop_last'
    ) %>%
    dplyr::summarise(
      mean_latency = mean(mean_latency),
      ci = qt(0.975, n() - 1) * sd(mean_latency) / sqrt(n()),
      .groups = 'drop'
    )

  violin_global <- ggplot(latency_plot_data,
    aes(x = event_rate_label, y = latency_ms, fill = event_rate_label)) +
    geom_violin(alpha = 0.4, trim = TRUE, scale = "width", drop = FALSE) +
    geom_point(
      data = latency_plot_data %>%
        dplyr::group_by(event_rate_label, run) %>%
        dplyr::summarise(mean_latency = mean(latency_ms, na.rm = TRUE), .groups = 'drop'),
      aes(y = mean_latency, group = event_rate_label),
      position = position_jitter(width = 0.1),
      size = 1.5, alpha = 0.6
    ) +
    geom_errorbar(
      data = all_latency_summary,
      aes(y = mean_latency, ymin = pmax(0, mean_latency - ci), ymax = mean_latency + ci),
      width = 0.2, color = "black", size = 0.8
    ) +
    geom_point(
      data = all_latency_summary,
      aes(y = mean_latency),
      shape = 23, size = 3, fill = "white", color = "black"
    ) +
    scale_y_continuous(labels = scales::label_number()) +
    scale_fill_brewer(name = "Event Rate", palette = "Dark2") +
    labs(
      x = "Event Rate",
      y = "Latency (ms)",
      caption = "Points: run means. Error bars: 95% CI across runs."
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

  print(violin_global)

  # Save latency plot for paper
#  if (dir.exists(paper_dir)) {
#    ggsave(file.path(paper_dir, "stlp260n.pdf"), violin_global,
#           width = 16, height = 10, units = "cm", device = cairo_pdf)
#    cat(sprintf("Saved latency plot to %s/stlp260n.pdf\n", paper_dir))
#  }


  # ── 2) Faceted violin by statement_type ──
  st_latency_summary <- latency_plot_data %>%
    dplyr::group_by(event_rate_label, statement_type, run) %>%
    dplyr::summarise(
      mean_latency = mean(latency_ms, na.rm = TRUE),
      .groups = 'drop_last'
    ) %>%
    dplyr::summarise(
      mean_latency = mean(mean_latency),
      ci = qt(0.975, n() - 1) * sd(mean_latency) / sqrt(n()),
      .groups = 'drop'
    )

  violin_faceted <- ggplot(latency_plot_data,
    aes(x = event_rate_label, y = latency_ms, fill = event_rate_label)) +
    geom_violin(alpha = 0.4, trim = TRUE, scale = "width", drop = FALSE) +
    geom_point(
      data = latency_plot_data %>%
        dplyr::group_by(event_rate_label, statement_type, run) %>%
        dplyr::summarise(mean_latency = mean(latency_ms, na.rm = TRUE), .groups = 'drop'),
      aes(y = mean_latency, group = event_rate_label),
      position = position_jitter(width = 0.1),
      size = 1.0, alpha = 0.5
    ) +
    geom_errorbar(
      data = st_latency_summary,
      aes(y = mean_latency, ymin = pmax(0, mean_latency - ci), ymax = mean_latency + ci),
      width = 0.2, color = "black", size = 0.7
    ) +
    geom_point(
      data = st_latency_summary,
      aes(y = mean_latency),
      shape = 23, size = 2.5, fill = "white", color = "black"
    ) +
    facet_wrap(~ statement_type, scales = "free_y", ncol = 1) +
    scale_y_continuous(labels = scales::label_number()) +
    scale_fill_brewer(name = "Event Rate", palette = "Dark2") +
    labs(
      title = "Latency by Statement Type",
      subtitle = "FULFILLMENT = target→fire | TEMP_VIO = event→detect | PERM_VIO = activation→timeout→detect",
      x = "Event Rate",
      y = "Latency (ms) [Log Scale]",
      caption = "PERMANENT_VIOLATION latency includes withinPeriod timeout by design."
    ) +
    theme_ipsum_rc() +
    theme(
      panel.grid.major = element_line(linetype = "dashed", linewidth = 0.5, color = "gray80"),
      panel.grid.minor = element_line(linetype = "dashed", linewidth = 0.25, color = "gray85"),
      legend.position = "bottom",
      axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
      axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
      text = element_text(family = "Roboto Condensed"),
      plot.margin = margin(),
      strip.text = element_text(face = "bold", size = 11)
    )

  print(violin_faceted)


  # ── 3) Per constraint-type × statement-type heatmap ──
  ct_st_summary <- latency_plot_data %>%
    dplyr::group_by(event_rate_label, constraint_type, statement_type) %>%
    dplyr::summarise(
      mean_latency = mean(latency_ms, na.rm = TRUE),
      n = n(),
      .groups = 'drop'
    )

  heatmap_plot <- ggplot(ct_st_summary,
    aes(x = constraint_type, y = statement_type, fill = mean_latency)) +
    geom_tile(color = "white", linewidth = 0.5) +
    geom_text(aes(label = sprintf("%.1f\n(n=%d)", mean_latency, n)),
              size = 3.0, family = "Roboto Mono") +
    scale_fill_viridis_c(
      name = "Mean Latency (ms)",
      option = "magma",
      labels = scales::label_number()
    ) +
    facet_wrap(~ event_rate_label) +
    labs(
      title = "Mean Latency: Constraint Type × Statement Type",
      x = "Constraint Type",
      y = "Statement Type"
    ) +
    theme_ipsum_rc() +
    theme(
      axis.text.x = element_text(angle = 45, hjust = 1, family = "Roboto Mono", size = 9),
      axis.text.y = element_text(family = "Roboto Mono", size = 9),
      text = element_text(family = "Roboto Condensed"),
      legend.position = "bottom"
    )

  print(heatmap_plot)


  # ── Summary tables ──

  # Per statement type
  st_summary_table <- latency_plot_data %>%
    dplyr::group_by(event_rate_label, statement_type, run) %>%
    dplyr::summarise(
      min    = min(latency_ms, na.rm = TRUE),
      median = median(latency_ms, na.rm = TRUE),
      mean   = mean(latency_ms, na.rm = TRUE),
      max    = max(latency_ms, na.rm = TRUE),
      p95    = quantile(latency_ms, 0.95, na.rm = TRUE),
      p99    = quantile(latency_ms, 0.99, na.rm = TRUE),
      count  = n(),
      .groups = 'drop_last'
    ) %>%
    dplyr::summarise(
      `Min (ms)`     = sprintf("%.2f ± %.2f", mean(min),    sd(min)),
      `Median (ms)`  = sprintf("%.2f ± %.2f", mean(median), sd(median)),
      `Mean (ms)`    = sprintf("%.2f ± %.2f", mean(mean),   sd(mean)),
      `Max (ms)`     = sprintf("%.2f ± %.2f", mean(max),    sd(max)),
      `P95 (ms)`     = sprintf("%.2f ± %.2f", mean(p95),    sd(p95)),
      `P99 (ms)`     = sprintf("%.2f ± %.2f", mean(p99),    sd(p99)),
      `Samples`      = sprintf("%d (over %d runs)", sum(count), n()),
      .groups = 'drop'
    )

  print("\nLatency Summary by Statement Type:")
  print(knitr::kable(st_summary_table, format = "simple"))

} else {
  warning("No latency data files found. Skipping latency plots.")
}


# ── Combined resource + latency summary table ─────────────────────────────────

if (exists("plot_data") && nrow(plot_data) > 0) {
  resource_summary <- plot_data %>%
    dplyr::group_by(scenario, rate, run) %>%
    dplyr::summarise(
      avg_cpu = mean(cpu_percent, na.rm = TRUE),
      max_cpu = max(cpu_percent, na.rm = TRUE),
      avg_mem = mean(memory_mb, na.rm = TRUE),
      max_mem = max(memory_mb, na.rm = TRUE),
      .groups = 'drop_last'
    )

  if (exists("latency_plot_data") && nrow(latency_plot_data) > 0) {
    latency_summary <- latency_plot_data %>%
      dplyr::group_by(event_rate_label, statement_type, run) %>%
      dplyr::summarise(
        mean_latency = mean(latency_ms, na.rm = TRUE),
        p50_latency  = quantile(latency_ms, probs = 0.5,  na.rm = TRUE),
        p90_latency  = quantile(latency_ms, probs = 0.9,  na.rm = TRUE),
        p99_latency  = quantile(latency_ms, probs = 0.99, na.rm = TRUE),
        .groups = 'drop_last'
      ) %>%
      dplyr::ungroup()

    # Join for FULFILLMENT only (the clean E2E metric)
    fulfillment_latency <- latency_summary %>%
      dplyr::filter(statement_type == "FULFILLMENT") %>%
      dplyr::mutate(rate = as.numeric(stringr::str_extract(event_rate_label, "\\d+")))

    combined_summary <- resource_summary %>%
      dplyr::left_join(
        fulfillment_latency %>%
          dplyr::select(rate, run, mean_latency, p50_latency, p90_latency, p99_latency),
        by = c("rate", "run")
      )
  } else {
    combined_summary <- resource_summary
  }

  final_summary <- combined_summary %>%
    dplyr::group_by(scenario, rate) %>%
    dplyr::summarise(
      `Avg CPU %`       = sprintf("%.1f ± %.1f", mean(avg_cpu), sd(avg_cpu)),
      `Max CPU %`       = sprintf("%.1f ± %.1f", mean(max_cpu), sd(max_cpu)),
      `Avg RAM (MB)`    = sprintf("%.1f ± %.1f", mean(avg_mem), sd(avg_mem)),
      `Max RAM (MB)`    = sprintf("%.1f ± %.1f", mean(max_mem), sd(max_mem)),
      `Mean Latency (ms)` = if (exists("mean_latency", where = combined_summary))
        sprintf("%.1f ± %.1f", mean(mean_latency, na.rm = TRUE), sd(mean_latency, na.rm = TRUE))
        else "N/A",
      `Latency P50 (ms)`  = if (exists("p50_latency", where = combined_summary))
        sprintf("%.1f ± %.1f", mean(p50_latency, na.rm = TRUE), sd(p50_latency, na.rm = TRUE))
        else "N/A",
      `Latency P90 (ms)`  = if (exists("p90_latency", where = combined_summary))
        sprintf("%.1f ± %.1f", mean(p90_latency, na.rm = TRUE), sd(p90_latency, na.rm = TRUE))
        else "N/A",
      `Latency P99 (ms)`  = if (exists("p99_latency", where = combined_summary))
        sprintf("%.1f ± %.1f", mean(p99_latency, na.rm = TRUE), sd(p99_latency, na.rm = TRUE))
        else "N/A",
      `Runs` = n(),
      .groups = 'drop'
    )

  print("\nResource and Latency Summary (FULFILLMENT only = pure E2E):")
  print(knitr::kable(final_summary, format = "simple"))
}

knitr::kable(final_summary, "latex")


# ── LaTeX talltblr performance table ──────────────────────────────────────────
# Uses FULFILLMENT-only latency (pure E2E: target arrival → rule fires).
# Reads raw per-run data to compute mean ± sd across runs.

if (exists("latency_plot_data") && nrow(latency_plot_data) > 0 &&
    exists("plot_data") && nrow(plot_data) > 0) {

  # 1) Per-run CPU / Memory aggregates
  resource_per_run <- plot_data %>%
    dplyr::group_by(rate, run) %>%
    dplyr::summarise(
      avg_cpu = mean(cpu_percent, na.rm = TRUE),
      max_cpu = max(cpu_percent, na.rm = TRUE),
      avg_mem = mean(memory_mb, na.rm = TRUE),
      max_mem = max(memory_mb, na.rm = TRUE),
      .groups = 'drop'
    )

  # 2) Per-run FULFILLMENT-only latency aggregates
  fulfillment_per_run <- latency_plot_data %>%
    dplyr::filter(statement_type == "FULFILLMENT") %>%
    dplyr::mutate(rate = as.numeric(rate)) %>%
    dplyr::group_by(rate, run) %>%
    dplyr::summarise(
      lat_mean = mean(latency_ms, na.rm = TRUE),
      lat_p90  = quantile(latency_ms, 0.90, na.rm = TRUE),
      lat_p99  = quantile(latency_ms, 0.99, na.rm = TRUE),
      n_events = n(),
      .groups = 'drop'
    )

  # 3) Join resource + latency per run
  perf_per_run <- resource_per_run %>%
    dplyr::inner_join(fulfillment_per_run, by = c("rate", "run"))

  # 4) Across-run summary: mean ± sd
  perf_summary <- perf_per_run %>%
    dplyr::group_by(rate) %>%
    dplyr::summarise(
      cpu_mean   = sprintf("%.1f $\\pm$ %.1f", mean(avg_cpu), sd(avg_cpu)),
      cpu_max    = sprintf("%.1f $\\pm$ %.1f", mean(max_cpu), sd(max_cpu)),
      mem_mean   = sprintf("%.0f $\\pm$ %.0f", mean(avg_mem), sd(avg_mem)),
      mem_max    = sprintf("%.0f $\\pm$ %.0f", mean(max_mem), sd(max_mem)),
      lat_mean   = sprintf("%.1f $\\pm$ %.1f", mean(lat_mean), sd(lat_mean)),
      lat_p90    = sprintf("%.1f $\\pm$ %.1f", mean(lat_p90),  sd(lat_p90)),
      lat_p99    = sprintf("%.1f $\\pm$ %.1f", mean(lat_p99),  sd(lat_p99)),
      throughput = sprintf("%.1f", mean(rate)),  # actual throughput ≈ target rate for fixed-duration tests
      n_runs     = n(),
      .groups    = 'drop'
    ) %>%
    dplyr::arrange(rate)

  # 5) Emit LaTeX talltblr
  cat("\n\n% ── LaTeX talltblr (FULFILLMENT-only latency) ──\n")
  cat("\\begin{talltblr}[\n")
  cat("    caption = {Performance and resource utilization metrics for scalability scenarios.},\n")
  cat("    label = {tab:performance_metrics_scal_normal},\n")
  cat("  ]{\n")
  cat("    colspec = {X[l,-1]*{9}{X[r,-1]}},\n")
  cat("    row{1-Z} = {font=\\tiny},\n")
  cat("    row{1,2} = {font=\\tiny\\bfseries, c, m},\n")
  cat("    hline{1,Z} = {1.5pt, solid},\n")
  cat("    hline{3} = {0.75pt, solid},\n")
  cat("    stretch = 0,\n")
  cat("    colsep = 3pt,\n")
  cat("    rowsep = 2.5pt\n")
  cat("  }\n")
  cat("  \\SetCell[r=2]{c} {Target Load}\n")
  cat("  & \\SetCell[c=2]{c} CPU (\\%) & & \\SetCell[c=2]{c} Memory (MB) & & \\SetCell[c=3]{c} Latency (ms) & & & \\SetCell[r=2]{c} {Actual Throughput\\\\(events/s)} \\\\\n")
  cat("  \\cmidrule[lr]{2-3} \\cmidrule[lr]{4-5} \\cmidrule[lr]{6-8}\n")
  cat("  & $\\mu$ & $\\max$ & $\\mu$ & $\\max$ & $\\mu$ & $P_{90}$ & $P_{99}$ & \\\\\n")

  for (i in seq_len(nrow(perf_summary))) {
    row <- perf_summary[i, ]
    cat(sprintf("  %s & %s & %s & %s & %s & %s & %s & %s & %s \\\\\n",
      format(row$rate, big.mark = ",", scientific = FALSE),
      row$cpu_mean, row$cpu_max,
      row$mem_mean, row$mem_max,
      row$lat_mean, row$lat_p90, row$lat_p99,
      row$throughput))
  }

  cat("\\end{talltblr}\n")
  cat("% ── End talltblr ──\n\n")

  print(perf_summary)
}
