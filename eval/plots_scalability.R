setwd("../Desktop/quarkuscep/eval")

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

# Get all run files and extract run numbers
run_files <- list.files(pattern = "scalability_.*_run\\d+_resources\\.csv", full.names = TRUE)

# Read and combine all run files
plot_data <- run_files %>%
  # Extract run information from filenames
  tibble::tibble(
    filepath = .,
    filename = basename(.),
    run = stringr::str_extract(filename, "run\\d+") %>% stringr::str_remove("run") %>% as.integer(),
    rate = stringr::str_extract(filename, "\\d+eps") %>% stringr::str_remove("eps") %>% as.integer()
  ) %>%
  # Sort by rate and run number
  dplyr::arrange(rate, run) %>%
  # Read each file and combine
  dplyr::group_split(rate, run) %>%
  purrr::map_dfr(function(x) {
    readr::read_csv(x$filepath, show_col_types = FALSE) %>%
      dplyr::mutate(
        rate = x$rate,
        run = x$run,
        scenario = paste0(rate, " events/s")
      )
  }) %>%
  # Convert scenario to factor with proper ordering
  dplyr::mutate(
    scenario = forcats::fct_reorder(
      .f = scenario,
      .x = as.numeric(stringr::str_extract(scenario, "\\d+")),
      .desc = FALSE
    )
  )

# Calculate summary statistics across runs
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
  dplyr::mutate(
    # Calculate 95% confidence intervals
    ci_cpu = ifelse(n_runs > 1, qt(0.95, df = n_runs-1) * sd_cpu / sqrt(n_runs), 0),
    ci_mem = ifelse(n_runs > 1, qt(0.95, df = n_runs-1) * sd_mem / sqrt(n_runs), 0)
  )

line_types <- c("dashed", "dotted", "solid", "dotdash", "twodash", "dashed","dotted")

# CPU Plot with confidence intervals
a <- ggplot(run_summary, aes(x = elapsed_time_s, y = avg_cpu, color = scenario, linetype = scenario)) +
  # Add confidence interval ribbon with matching color but no border
  geom_ribbon(aes(ymin = pmax(0, avg_cpu - ci_cpu), ymax = avg_cpu + ci_cpu, fill = scenario), 
              alpha = 0.2, color = NA, show.legend = FALSE) +
  # Add average line
  geom_line(linewidth = 1.1) +
  labs(
    y = "CPU usage",
    x = "Time (seconds)"
  ) +
  theme_ipsum_rc() +
  scale_color_brewer(name="Scenario", palette = "Dark2") +
  scale_fill_brewer(name="Scenario", palette = "Dark2") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  scale_y_continuous(labels = percent_format(scale = 1)) +
  coord_cartesian(ylim = c(0, 200)) +
  theme(
    legend.position = "bottom",
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    text = element_text(family = "Roboto Condensed"),
    plot.margin = margin()
  )

# Memory Plot with confidence intervals
b <- ggplot(run_summary, aes(x = elapsed_time_s, y = avg_mem, color = scenario, linetype = scenario)) +
  # Add confidence interval ribbon with matching color but no border
  geom_ribbon(aes(ymin = pmax(0, avg_mem - ci_mem), ymax = avg_mem + ci_mem, fill = scenario), 
              alpha = 0.2, color = NA, show.legend = FALSE) +
  # Add average line
  geom_line(linewidth = 1.1) +
  labs(
    y = "RAM usage (MB)",
    x = "Time (seconds)"
  ) +
  theme_ipsum_rc() +
  scale_color_brewer(name="Scenario", palette = "Dark2") +
  scale_fill_brewer(name="Scenario", palette = "Dark2") +
  scale_linetype_manual(name = "Scenario", values = line_types) +
  scale_x_time(
    breaks = scales::breaks_width("10 secs"),
    labels = scales::label_time(format = "%M:%S"),
    expand = c(0, 0)
  )+
  coord_cartesian(ylim = c(0, 2048)) +
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














# Read and process latency data from all runs
latency_run_files <- list.files(
  pattern = "scalability_.*_run\\d+_latency\\.csv",
  full.names = TRUE
)

if (length(latency_run_files) > 0) {
  # Process latency data with run information
  latency_plot_data <- latency_run_files %>%
    # Extract run and rate information from filenames
    tibble::tibble(
      filepath = .,
      filename = basename(.),
      run = stringr::str_extract(filename, "run\\d+") %>% stringr::str_remove("run") %>% as.integer(),
      rate = stringr::str_extract(filename, "\\d+eps") %>% stringr::str_remove("eps") 
    ) %>%
    # Sort by rate and run number
    dplyr::arrange(rate, run) %>%
    # Read each file and combine
    dplyr::group_split(rate, run) %>%
    purrr::map_dfr(function(x) {
      if (file.exists(x$filepath)) {
        readr::read_csv(x$filepath, show_col_types = FALSE) %>%
          dplyr::mutate(
            rate = x$rate,
            run = x$run,
            event_rate = x$rate,
            event_rate_label = paste0(rate, " events/s")
          )
      } else {
        NULL
      }
    })
  
  # Filter for first run only
  first_run_latency <- latency_plot_data %>%
    dplyr::filter(run == 1) %>%
    dplyr::mutate(
      event_rate_label = forcats::fct_reorder(
        .f = event_rate_label,
        .x = as.numeric(event_rate),
        .desc = FALSE
      )
    )
} else {
  warning("No latency data files found. Skipping latency plots.")
  first_run_latency <- NULL
}

# Use the same 'latency_plot_data' from the CDF example

# Only create latency plot if we have data
if (!is.null(latency_plot_data) && nrow(latency_plot_data) > 0) {
  # Calculate mean and CI for each event rate
  latency_summary_plot <- latency_plot_data %>%
    dplyr::group_by(event_rate_label, run) %>%
    dplyr::summarise(
      median = median(latency_ms, na.rm = TRUE),
      mean = mean(latency_ms, na.rm = TRUE),
      .groups = 'drop_last'
    ) %>%
    dplyr::summarise(
      mean_latency = mean(mean),
      ci = qt(0.975, n() - 1) * sd(mean) / sqrt(n()),
      .groups = 'drop'
    )
  
  # Create the plot
  violin_plot <- ggplot(latency_plot_data, aes(x = event_rate_label, y = latency_ms, fill = event_rate_label)) +
  # Use geom_violin for the density shape with run averages
  geom_violin(alpha = 0.4, trim = TRUE, scale = "width", drop=FALSE) +
  # Add points for each run's mean
  geom_point(data = latency_plot_data %>% 
               group_by(event_rate_label, run) %>% 
               summarise(mean_latency = mean(latency_ms, na.rm = TRUE), .groups = 'drop'),
             aes(y = mean_latency, group = event_rate_label),
             position = position_jitter(width = 0.1),
             size = 1.5, alpha = 0.6) +
  # Add mean and CI from all runs
  geom_errorbar(data = latency_summary_plot,
                aes(y = mean_latency, ymin = pmax(0, mean_latency - ci), ymax = mean_latency + ci),
                width = 0.2, color = "black", size = 0.8) +
  geom_point(data = latency_summary_plot,
             aes(y = mean_latency),
             shape = 23, size = 3, fill = "white", color = "black") +
  scale_y_log10(labels = scales::label_number()) +
  annotation_logticks(sides = "l") +
  scale_fill_brewer(name = "Event Rate", palette = "Dark2") +
  
  labs(
    x = "Event Rate",
    y = "Latency (ms) [Log Scale]",
    fill = "Event Rate",
    caption = "Points show individual run means. Error bars show 95% CI of the mean across runs."
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

  # Print the violin plot
  print(violin_plot)
  
  # Generate summary statistics table
  if (exists("latency_plot_data") && nrow(latency_plot_data) > 0) {
    latency_summary <- latency_plot_data %>%
      dplyr::group_by(event_rate_label, run) %>%
      dplyr::summarise(
        min = min(latency_ms, na.rm = TRUE),
        median = median(latency_ms, na.rm = TRUE),
        mean = mean(latency_ms, na.rm = TRUE),
        max = max(latency_ms, na.rm = TRUE),
        p95 = quantile(latency_ms, 0.95, na.rm = TRUE),
        count = n(),
        .groups = 'drop_last'
      ) %>%
      dplyr::summarise(
        `Min (ms)` = sprintf("%.2f ± %.2f", mean(min), sd(min)),
        `Median (ms)` = sprintf("%.2f ± %.2f", mean(median), sd(median)),
        `Mean (ms)` = sprintf("%.2f ± %.2f", mean(mean), sd(mean)),
        `Max (ms)` = sprintf("%.2f ± %.2f", mean(max), sd(max)),
        `p95 (ms)` = sprintf("%.2f ± %.2f", mean(p95), sd(p95)),
        `Samples` = sprintf("%d (over %d runs)", sum(count), n()),
        .groups = 'drop'
      )
    
    # Print the summary table
    print("\nLatency Summary (First Run):")
    print(knitr::kable(latency_summary, format = "simple"))
  }
}

# Generate summary table for resource usage
if (exists("plot_data") && nrow(plot_data) > 0) {
  # First, process resource data
  resource_summary <- plot_data %>%
    dplyr::group_by(scenario, rate, run) %>%
    dplyr::summarise(
      avg_cpu = mean(cpu_percent, na.rm = TRUE),
      max_cpu = max(cpu_percent, na.rm = TRUE),
      avg_mem = mean(memory_mb, na.rm = TRUE),
      max_mem = max(memory_mb, na.rm = TRUE),
      .groups = 'drop_last'
    )
  
  # If latency data exists, join it with resource data
  if (exists("latency_plot_data") && nrow(latency_plot_data) > 0) {
    latency_summary <- latency_plot_data %>%
      dplyr::group_by(event_rate, event_rate_label, run) %>%
      dplyr::summarise(
        mean_latency = mean(latency_ms, na.rm = TRUE),
        p50_latency = quantile(latency_ms, probs = 0.5, na.rm = TRUE),
        p90_latency = quantile(latency_ms, probs = 0.9, na.rm = TRUE),
        p99_latency = quantile(latency_ms, probs = 0.99, na.rm = TRUE),
        .groups = 'drop_last'
      ) %>%
      dplyr::ungroup()
    
    # Join resource and latency summaries
    combined_summary <- resource_summary %>%
      dplyr::left_join(
        latency_summary %>% 
          dplyr::mutate(rate = as.numeric(stringr::str_extract(event_rate, "\\d+"))) %>%
          dplyr::select(rate, run, mean_latency, p50_latency, p90_latency, p99_latency),
        by = c("rate", "run")
      )
  } else {
    combined_summary <- resource_summary
  }
  
  # Create final summary table
  final_summary <- combined_summary %>%
    dplyr::group_by(scenario, rate) %>%
    dplyr::summarise(
      `Avg CPU %` = sprintf("%.1f ± %.1f", mean(avg_cpu), sd(avg_cpu)),
      `Max CPU %` = sprintf("%.1f ± %.1f", mean(max_cpu), sd(max_cpu)),
      `Avg RAM (MB)` = sprintf("%.1f ± %.1f", mean(avg_mem), sd(avg_mem)),
      `Max RAM (MB)` = sprintf("%.1f ± %.1f", mean(max_mem), sd(max_mem)),
      `Mean Latency (ms)` = if (exists("mean_latency")) sprintf("%.1f ± %.1f", mean(mean_latency), sd(mean_latency)) else "N/A",
      `Latency P50 (ms)` = if (exists("p50_latency")) sprintf("%.1f ± %.1f", mean(p50_latency), sd(p50_latency)) else "N/A",
      `Latency P90 (ms)` = if (exists("p90_latency")) sprintf("%.1f ± %.1f", mean(p90_latency), sd(p90_latency)) else "N/A",
      `Latency P99 (ms)` = if (exists("p99_latency")) sprintf("%.1f ± %.1f", mean(p99_latency), sd(p99_latency)) else "N/A",
      `Runs` = n(),
      .groups = 'drop'
    )
  
  print("\nResource and Latency Summary:")
  print(knitr::kable(final_summary, format = "simple"))
  
  final_summary
}

knitr::kable(final_summary, "latex")

