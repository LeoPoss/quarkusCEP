import asyncio
import random
import re
import time

import docker
import httpx
import numpy as np
import pandas as pd
from tqdm.asyncio import tqdm

BACKEND_IMAGE = "quarkuscep-backend:latest"
API_BASE_URL = "http://localhost:8080"
CPU_CORES = 2
MEMORY_LIMIT = "2G"

ACTIVE_CONSTRAINTS_SHARE = 1
NOISE_EVENT_PERCENT = 0.9


def create_constraint(constraint_type, i, **kwargs):
    base = {
        "name": f"{constraint_type.capitalize()}{i}",
        "type": constraint_type.lower(),
        "activationEvent": f"{constraint_type[0].upper()}A{i}",
        "targetEvent": f"{constraint_type[0].upper()}T{i}",
        "activationCondition": {
            "param": "priority",
            "operator": ">",
            "value": "3"
        },
        "targetCondition": {
            "param": "priority",
            "operator": ">",
            "value": "2"
        }
    }
    base.update(kwargs)
    return base


SCALABILITY_CONSTRAINTS = [
                              create_constraint("response", i) for i in range(5)
                          ] + [
                              create_constraint("precedence", i) for i in range(5)
                          ]


def generate_constraint_variations(base_name, count, constraint_type, **overrides):
    """Generate a list of constraints with variations in conditions and time windows."""
    variations = []
    for i in range(count):
        # Ensure withinPeriod is always a positive number
        within_period = max(1000, random.choice([1000, 2000, 3000, 5000, 10000]))

        # Base constraint with safe defaults
        constraint = {
            "name": f"{base_name}{i}",
            "type": constraint_type,
            "activationEvent": f"{constraint_type[0].upper()}{base_name[0]}A{i}",
            "targetEvent": f"{constraint_type[0].upper()}{base_name[0]}T{i}",
            "withinPeriod": within_period,
            "activationCondition": {
                "param": "priority",
                "operator": random.choice([">", ">=", "<", "<=", "="]),
                "value": str(random.randint(1, 5))
            },
            "targetCondition": {
                "param": "priority",
                "operator": random.choice([">", ">=", "<", "<=", "="]),
                "value": str(random.randint(1, 5))
            }
        }

        # Apply any overrides, ensuring withinPeriod remains valid
        if 'withinPeriod' in overrides:
            overrides['withinPeriod'] = max(1000, int(overrides['withinPeriod']))
        constraint.update(overrides)

        variations.append(constraint)
    return variations


def start_backend_container():
    print(f"Starting container for image: {BACKEND_IMAGE}")
    client = docker.from_env()
    try:
        container = client.containers.run(
            BACKEND_IMAGE,
            detach=True,
            ports={'8080/tcp': 8080},
            remove=True,
            nano_cpus=int(CPU_CORES * 1e9),
            mem_limit=MEMORY_LIMIT,
            environment=["QUARKUS_LOG_CONSOLE_FORMAT=%d{HH:mm:ss,SSS} %-5p [%c] %s%e%n"]
        )
        print(f"Container '{container.short_id}' started.")
        return container
    except docker.errors.ImageNotFound:
        print(f"Error: Image '{BACKEND_IMAGE}' not found. Please build or pull it first.")
    except Exception as e:
        print(f"An unexpected error occurred while starting the container: {e}")
    return None


def stop_and_get_logs_sync(container):
    print(f"Stopping container '{container.short_id}' and collecting logs...")
    try:
        time.sleep(2)
        logs = container.logs().decode('utf-8')
        container.stop()
        print("Container stopped.")
        return logs
    except docker.errors.NotFound:
        print("Warning: Container not found, could not stop or get logs.")
        return ""


def _calculate_cpu_percent(stat):
    try:
        cpu_stats = stat.get('cpu_stats', {})
        precpu_stats = stat.get('precpu_stats', {})

        cpu_delta = cpu_stats.get('cpu_usage', {}).get('total_usage', 0) - \
                    precpu_stats.get('cpu_usage', {}).get('total_usage', 0)

        system_cpu_delta = cpu_stats.get('system_cpu_usage', 0) - \
                           precpu_stats.get('system_cpu_usage', 0)

        number_of_cpus = cpu_stats.get('online_cpus', len(cpu_stats.get('cpu_usage', {}).get('percpu_usage', [1])))
        if system_cpu_delta > 0.0 and cpu_delta > 0.0:
            cpu_percent = (cpu_delta / system_cpu_delta) * number_of_cpus * 100.0
            return cpu_percent

    except (KeyError, TypeError):
        return 0.0

    return 0.0


def monitor_resources_sync(container, duration_sec):
    print("Starting resource monitoring...")
    stats = []
    monitoring_start_time = time.time()
    try:
        for stat in container.stats(stream=True, decode=True):
            if time.time() - monitoring_start_time > duration_sec:
                break

            elapsed_time = time.time() - monitoring_start_time

            cpu_percent = _calculate_cpu_percent(stat)
            memory_usage_mb = stat.get('memory_stats', {}).get('usage', 0) / (1024 * 1024)
            stats.append({'elapsed_time_s': round(elapsed_time, 0), 'cpu_percent': round(cpu_percent, 2),
                          'memory_mb': round(memory_usage_mb, 2)})

    except Exception as e:
        print(f"   - Warning: Resource monitoring stopped unexpectedly: {e}")

    print("Resource monitoring finished.")
    return pd.DataFrame(stats)


def analyze_results_sync(logs, resources_df, actual_eps, run_name, analyzer_metrics=None):
    print("Analyzing results...")
    
    # Process logs in chunks to avoid memory issues with large logs
    chunk_size = 100000  # Process 100k lines at a time
    latency_pattern = re.compile(r"LATENCY,([^,]+),(\d+)")
    
    # Process logs in chunks
    latencies = []
    lines = logs.split('\n')
    total_lines = len(lines)
    
    print(f"Processing {total_lines} log lines in chunks...")
    
    for i in range(0, total_lines, chunk_size):
        chunk = '\n'.join(lines[i:i+chunk_size])
        latencies.extend(latency_pattern.findall(chunk))
        print(f"Processed {min(i + chunk_size, total_lines)}/{total_lines} lines, found {len(latencies)} latency markers")

    if not latencies:
        print("   - Warning: No latency markers found in logs.")
        return {
            "mean_latency_ms": np.nan, "median_latency_ms": np.nan,
            "p95_latency_ms": np.nan, "p99_latency_ms": np.nan,
            "avg_cpu_percent": np.nan, "max_cpu_percent": np.nan,
            "avg_mem_mb": np.nan, "max_mem_mb": np.nan,
            "total_events_processed": 0,
            "error": "No latency markers found"
        }

    try:
        print(f"Processing {len(latencies)} latency measurements...")
        latency_df = pd.DataFrame(latencies, columns=['constraint', 'latency_ns'])
        
        # Convert to numeric with error handling
        latency_df['latency_ms'] = pd.to_numeric(latency_df['latency_ns'], errors='coerce') / 1E6
        
        # Drop any rows with invalid latency values
        valid_latencies = latency_df['latency_ms'].dropna()
        
        if len(valid_latencies) == 0:
            print("   - Warning: No valid latency measurements found after filtering.")
            return {
                "mean_latency_ms": np.nan, "median_latency_ms": np.nan,
                "p95_latency_ms": np.nan, "p99_latency_ms": np.nan,
                "avg_cpu_percent": np.nan, "max_cpu_percent": np.nan,
                "avg_mem_mb": np.nan, "max_mem_mb": np.nan,
                "total_events_processed": 0,
                "error": "No valid latency measurements after filtering"
            }
        
        # Save a sample of the latencies instead of all to reduce I/O
        sample_size = min(10000, len(valid_latencies))
        valid_latencies.sample(sample_size).to_csv(f"{run_name}_latency_sample.csv", index=False)
        print(f"Saved sample of {sample_size} latency measurements to {run_name}_latency_sample.csv")

        if resources_df.empty:
            print("   - Warning: Resource data is empty. CPU/Mem stats will be NaN.")
            avg_cpu, max_cpu, avg_mem, max_mem = np.nan, np.nan, np.nan, np.nan
        else:
            avg_cpu = resources_df['cpu_percent'].mean()
            max_cpu = resources_df['cpu_percent'].max()
            avg_mem = resources_df['memory_mb'].mean()
            max_mem = resources_df['memory_mb'].max()

        # Calculate percentiles more efficiently for large datasets
        p95 = np.percentile(valid_latencies, 95) if len(valid_latencies) > 0 else np.nan
        p99 = np.percentile(valid_latencies, 99) if len(valid_latencies) > 0 else np.nan

        results = {
            "mean_latency_ms": valid_latencies.mean(),
            "median_latency_ms": valid_latencies.median(),
            "p95_latency_ms": p95,
            "p99_latency_ms": p99,
            "max_latency_ms": valid_latencies.max(),
            "min_latency_ms": valid_latencies.min(),
            "avg_cpu_percent": avg_cpu,
            "max_cpu_percent": max_cpu,
            "avg_mem_mb": avg_mem,
            "max_mem_mb": max_mem,
            "actual_eps": actual_eps,
            "total_events_processed": len(valid_latencies),
            "error": None
        }
    except Exception as e:
        print(f"Error during analysis: {str(e)}")
        return {
            "mean_latency_ms": np.nan, "median_latency_ms": np.nan,
            "p95_latency_ms": np.nan, "p99_latency_ms": np.nan,
            "avg_cpu_percent": np.nan, "max_cpu_percent": np.nan,
            "avg_mem_mb": np.nan, "max_mem_mb": np.nan,
            "total_events_processed": 0,
            "error": f"Analysis error: {str(e)}"
        }
    print("Analysis complete.")
    return results


async def wait_for_backend():
    print("Waiting for backend to be ready...")
    async with httpx.AsyncClient(timeout=10) as client:
        for _ in range(30):
            try:
                response = await client.get(f"{API_BASE_URL}/q/health/live")
                if response.status_code == 200:
                    print("Backend is responsive.")
                    return True
            except httpx.RequestError:
                pass  # Ignore connection errors while waiting
            await asyncio.sleep(1)
    print("Backend did not become ready in time.")
    return False


async def setup_constraints(constraints):
    print(f"Setting up {len(constraints)} constraints sequentially...")
    async with httpx.AsyncClient(timeout=120) as client:
        for c in tqdm(constraints, desc="Setting up constraints"):
            constraint_type = c.get("type")
            if not constraint_type:
                continue

            endpoint = f"{API_BASE_URL}/constraints/{constraint_type.lower()}"
            payload = {k: v for k, v in c.items() if k != 'type'}

            try:
                res = await client.post(endpoint, json=payload)
                res.raise_for_status()
            except httpx.HTTPStatusError as e:
                print(
                    f"   - Warning: Failed to create {c.get('name')}. Status: {e.response.status_code}, Body: {e.response.text}")
            except httpx.RequestError as e:
                print(f"   - Error creating constraint {c.get('name')}: {e}")

    print("Constraints setup complete.")


async def run_load_test(constraints, rate_eps, duration_sec, num_instances):
    if not constraints:
        print("   - Warning: No constraints provided to load tester. Aborting load test.")
        return 0

    num_to_activate = int(len(constraints) * ACTIVE_CONSTRAINTS_SHARE)
    if num_to_activate == 0 and len(constraints) > 0:
        num_to_activate = 1
    active_constraints = random.sample(constraints, k=num_to_activate)
    print(
        f"Starting RANDOMIZED load test: Targeting {num_to_activate}/{len(constraints)} constraints with {NOISE_EVENT_PERCENT * 100}% noise...")

    batch_size = max(rate_eps / 10, 100)
    start_time = time.monotonic()
    event_counter = 0
    event_endpoint = f"{API_BASE_URL}/esper/events/batch"
    interval = 1.0 / rate_eps
    tasks = []
    event_batch = []

    total_events = int(rate_eps * duration_sec)

    async with httpx.AsyncClient(timeout=20.0) as client:
        with tqdm(total=total_events, unit="event", desc="Generating load") as pbar:
            while time.monotonic() - start_time < duration_sec:
                event_type = None
                if random.random() < NOISE_EVENT_PERCENT:
                    event_type = f"NoiseEvent_{random.randint(0, 1000)}"
                else:
                    constraint_to_trigger = random.choice(active_constraints)
                    is_activation = random.choice([True, False])
                    event_type = constraint_to_trigger['activationEvent'] if is_activation else constraint_to_trigger[
                        'targetEvent']

                instance_id = event_counter % num_instances
                # Differentiate between signals and events in the payload
                event_kind = "signal" if event_type.startswith(
                    ("AR", "TR", "AP", "TP", "MAR", "MTR", "MAP", "MTP", "MAAR", "MATR", "MANR", "MTNR")) else "event"
                priority = random.randint(1, 5)
                payload = {
                    "eventType": event_type,
                    "kind": event_kind,
                    "payload": {
                        "instanceId": instance_id,
                        "priority": priority,
                    }
                }
                event_batch.append(payload)

                if len(event_batch) >= batch_size:
                    task = asyncio.create_task(client.post(event_endpoint, json=event_batch))
                    tasks.append(task)
                    event_batch = []

                event_counter += 1
                pbar.update(1)

                target_time = start_time + (event_counter * interval)
                sleep_duration = target_time - time.monotonic()
                if sleep_duration > 0:
                    await asyncio.sleep(sleep_duration)

        if event_batch:
            task = asyncio.create_task(client.post(event_endpoint, json=event_batch))
            tasks.append(task)

        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    actual_duration = time.monotonic() - start_time
    actual_eps = event_counter / actual_duration if actual_duration > 0 else 0
    print(f"Load test finished. Generated {event_counter} events in {actual_duration:.2f}s (~{actual_eps:.2f} EPS).")
    return actual_eps


async def run_single_benchmark(constraints, rate_eps, duration_sec, run_name, num_instances=100):
    container = await asyncio.to_thread(start_backend_container)
    if not container:
        return None

    try:
        if not await wait_for_backend():
            return None
        await setup_constraints(constraints)

        monitor_task = asyncio.create_task(
            asyncio.to_thread(monitor_resources_sync, container, duration_sec)
        )
        load_task = asyncio.create_task(
            run_load_test(constraints, rate_eps, duration_sec, num_instances)
        )

        resources_df, actual_eps = await asyncio.gather(monitor_task, load_task)

        if not resources_df.empty:
            output_filename = f"{run_name}_resources.csv"
            resources_df.to_csv(output_filename, index=False)
            print(f"Detailed resource usage saved to {output_filename}")

    finally:
        logs = await asyncio.to_thread(stop_and_get_logs_sync, container)

    return await asyncio.to_thread(analyze_results_sync, logs, resources_df, actual_eps, run_name)


async def run_scenario_scalability(num_runs=10):
    print("\n=== Running Scalability Scenario ===")
    print(f"Testing how the system scales with increasing event rates (averaging over {num_runs} runs per rate)...")

    all_results = []

    # Test different event rates
    for rate_eps in [250000]:
        print(f"\n--- Testing at {rate_eps} events/second ---")

        # Run multiple times and collect results
        run_results = []
        for run_num in range(1, num_runs + 1):
            print(f"\nRun {run_num}/{num_runs}:")

            result = await run_single_benchmark(
                constraints=SCALABILITY_CONSTRAINTS,
                rate_eps=rate_eps,
                duration_sec=60,
                run_name=f"scalability_{rate_eps}eps_run{run_num}"
            )

            if result:
                run_results.append(result)

        # Calculate averages for this rate
        if run_results:
            # Convert to DataFrame for easier calculations
            df_runs = pd.DataFrame(run_results)

            # Calculate mean for numeric columns, take first for non-numeric
            avg_result = {}
            for col in df_runs.columns:
                if pd.api.types.is_numeric_dtype(df_runs[col]):
                    avg_result[col] = df_runs[col].mean()
                else:
                    avg_result[col] = df_runs[col].iloc[0]

            # Add run count and rate info
            avg_result['rate_eps'] = rate_eps
            avg_result['num_runs'] = len(run_results)

            all_results.append(avg_result)

            # Print stats for this rate
            print(f"\n--- Results for {rate_eps} eps (avg of {len(run_results)} runs) ---")
            print(f"CPU: {avg_result['avg_cpu_percent']:.1f}% avg, {avg_result['max_cpu_percent']:.1f}% max")
            print(f"Memory: {avg_result['avg_mem_mb']:.1f}MB avg, {avg_result['max_mem_mb']:.1f}MB max")
            print(f"Throughput: {avg_result['actual_eps']:.1f} events/sec")

    # Save results to CSV
    if all_results:
        df = pd.DataFrame(all_results)
        output_file = 'results/scalability_results_avg.csv'

        # Reorder columns to have rate_eps first
        cols = ['rate_eps', 'num_runs'] + [col for col in df.columns if col not in ['rate_eps', 'num_runs']]
        df = df[cols]

        # Save to CSV
        df.to_csv(output_file, index=False)
        print(f"\nAveraged results saved to {output_file}")

        # Print final summary
        print("\n=== Final Scalability Test Summary ===")
        print(df[['rate_eps', 'num_runs', 'avg_cpu_percent', 'max_cpu_percent', 'avg_mem_mb', 'max_mem_mb',
                  'actual_eps']])
    else:
        print("No results to save.")


if __name__ == "__main__":
    asyncio.run(run_scenario_scalability())
