import random
import re
import asyncio
import time
import pandas as pd
import numpy as np
import docker
import httpx
from tqdm.asyncio import tqdm

BACKEND_IMAGE = "quarkuscep-backend:latest"
API_BASE_URL = "http://localhost:8080"
CPU_CORES = 2
MEMORY_LIMIT = "4G"

ACTIVE_CONSTRAINTS_SHARE = 1
NOISE_EVENT_PERCENT = 0.99

SCALABILITY_CONSTRAINTS = [
                              {"name": f"Response{i}", "type": "response", "activationEvent": f"AR{i}",
                               "targetEvent": f"TR{i}"} for i in range(5)
                          ] + [
                              {"name": f"Precedence{i}", "type": "precedence", "activationEvent": f"AP{i}",
                               "targetEvent": f"TP{i}"} for i in range(5)
                          ]

COMPLEXITY_SCENARIOS = {
    "400_mixed_complex": {
        "constraints": (
            # 5 standard Response constraints
                [{"name": f"MixResp{i}", "type": "response", "activationEvent": f"MAR{i}", "targetEvent": f"MTR{i}"} for
                 i in range(10)] +
                # 5 Precedence constraints
                [{"name": f"MixPrec{i}", "type": "precedence", "activationEvent": f"MAP{i}", "targetEvent": f"MTP{i}"}
                 for i in range(10)] +
                # 5 Alternate Response (stricter sequence) constraints
                [{"name": f"MixAltR{i}", "type": "alternateresponse", "activationEvent": f"MAAR{i}",
                  "targetEvent": f"MATR{i}"} for i in range(10)] +
                # 5 Not Response (negative) constraints
                [{"name": f"MixNotR{i}", "type": "notresponse", "activationEvent": f"MANR{i}",
                  "targetEvent": f"MTNR{i}"} for i in range(10)]
        )
    },
    "10_response": {
        "constraints": [{"name": f"Response{i}", "type": "response", "activationEvent": f"A{i}", "targetEvent": f"T{i}"}
                        for i in range(10)]
    },
    "10_precedence": {
        "constraints": [
            {"name": f"Precedence{i}", "type": "precedence", "activationEvent": f"A{i}", "targetEvent": f"T{i}"} for i
            in range(10)]
    },
    "50_response": {
        "constraints": [{"name": f"Response{i}", "type": "response", "activationEvent": f"A{i}", "targetEvent": f"T{i}"}
                        for i in range(50)]
    },
    "40_mixed_complex": {
        "constraints": (
            # 5 standard Response constraints
                [{"name": f"MixResp{i}", "type": "response", "activationEvent": f"MAR{i}", "targetEvent": f"MTR{i}"} for
                 i in range(10)] +
                # 5 Precedence constraints
                [{"name": f"MixPrec{i}", "type": "precedence", "activationEvent": f"MAP{i}", "targetEvent": f"MTP{i}"}
                 for i in range(10)] +
                # 5 Alternate Response (stricter sequence) constraints
                [{"name": f"MixAltR{i}", "type": "alternateresponse", "activationEvent": f"MAAR{i}",
                  "targetEvent": f"MATR{i}"} for i in range(10)] +
                # 5 Not Response (negative) constraints
                [{"name": f"MixNotR{i}", "type": "notresponse", "activationEvent": f"MANR{i}",
                  "targetEvent": f"MTNR{i}"} for i in range(10)]
        )
    },
    "100_mixed_complex": {
        "constraints": (
            # 5 standard Response constraints
                [{"name": f"MixResp{i}", "type": "response", "activationEvent": f"MAR{i}", "targetEvent": f"MTR{i}"} for
                 i in range(25)] +
                # 5 Precedence constraints
                [{"name": f"MixPrec{i}", "type": "precedence", "activationEvent": f"MAP{i}", "targetEvent": f"MTP{i}"}
                 for i in range(25)] +
                # 5 Alternate Response (stricter sequence) constraints
                [{"name": f"MixAltR{i}", "type": "alternateresponse", "activationEvent": f"MAAR{i}",
                  "targetEvent": f"MATR{i}"} for i in range(25)] +
                # 5 Not Response (negative) constraints
                [{"name": f"MixNotR{i}", "type": "notresponse", "activationEvent": f"MANR{i}",
                  "targetEvent": f"MTNR{i}"} for i in range(25)]
        )
    }
}


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
            stats.append({'elapsed_time_s': elapsed_time, 'cpu_percent': cpu_percent, 'memory_mb': memory_usage_mb})

    except Exception as e:
        print(f"   - Warning: Resource monitoring stopped unexpectedly: {e}")

    print("Resource monitoring finished.")
    return pd.DataFrame(stats)


def analyze_results_sync(logs, resources_df, actual_eps, run_name):
    print("Analyzing results...")
    latency_pattern = re.compile(r"LATENCY,([^,]+),(\d+)")
    latencies = latency_pattern.findall(logs)

    if not latencies:
        print("   - Warning: No latency markers found in logs.")
        return {
            "mean_latency_ms": np.nan, "median_latency_ms": np.nan,
            "p95_latency_ms": np.nan, "p99_latency_ms": np.nan,
            "avg_cpu_percent": np.nan, "max_cpu_percent": np.nan,
            "avg_mem_mb": np.nan, "max_mem_mb": np.nan,
        }

    latency_df = pd.DataFrame(latencies, columns=['constraint', 'latency_ns'])
    latency_df['latency_ms'] = pd.to_numeric(latency_df['latency_ns']) / 1E6

    latency_df['latency_ms'].to_csv(f"{run_name}_latency.csv", index=False)

    if resources_df.empty:
        print("   - Warning: Resource data is empty. CPU/Mem stats will be NaN.")
        avg_cpu, max_cpu, avg_mem, max_mem = np.nan, np.nan, np.nan, np.nan
    else:
        avg_cpu = resources_df['cpu_percent'].mean()
        max_cpu = resources_df['cpu_percent'].max()
        avg_mem = resources_df['memory_mb'].mean()
        max_mem = resources_df['memory_mb'].max()

    results = {
        "mean_latency_ms": latency_df['latency_ms'].mean(),
        "median_latency_ms": latency_df['latency_ms'].median(),
        "p95_latency_ms": latency_df['latency_ms'].quantile(0.95),
        "p99_latency_ms": latency_df['latency_ms'].quantile(0.99),
        "max_latency_ms": latency_df['latency_ms'].max(),
        "avg_cpu_percent": avg_cpu,
        "max_cpu_percent": max_cpu,
        "avg_mem_mb": avg_mem,
        "max_mem_mb": max_mem,
        "actual_eps": actual_eps
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
                payload = {"eventType": event_type, "payload": {"instanceId": instance_id}}
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
            tasks.append(asyncio.create_task(client.post(event_endpoint, json=event_batch)))

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


async def run_scenario_scalability():
    print("\n" + "=" * 50)
    print(" SCENARIO 1: SCALABILITY TEST")
    print("=" * 50)

    rates_to_test = [100000]
    duration = 300
    all_results = {}

    for rate in rates_to_test:
        run_name = f"scalability_{rate}eps"
        print(f"\n--- Testing rate: {rate} EPS ---")
        result = await run_single_benchmark(SCALABILITY_CONSTRAINTS, rate, duration, run_name)
        if result:
            all_results[f"{rate}_eps"] = result

    print("\n--- SCALABILITY RESULTS (Summary) ---")
    results_df = pd.DataFrame(all_results).T
    print(results_df[['avg_cpu_percent', 'max_mem_mb', 'median_latency_ms', 'p99_latency_ms']].round(2))
    results_df.to_csv("scalability_results.csv")
    print("\n Summary results saved")


async def run_scenario_complexity():
    print("\n" + "=" * 50)
    print(" SCENARIO 2: PROCESS COMPLEXITY TEST")
    print("=" * 50)

    fixed_rate = 10000
    duration = 60
    all_results = {}

    for name, scenario in COMPLEXITY_SCENARIOS.items():
        run_name = f"complexity_{name}"
        print(f"\n--- Testing case: {name} ---")
        result = await run_single_benchmark(scenario['constraints'], fixed_rate, duration, run_name)
        if result:
            all_results[name] = result

    print("\n--- COMPLEXITY RESULTS (Summary) ---")
    results_df = pd.DataFrame(all_results).T
    print(results_df[['avg_cpu_percent', 'max_mem_mb', 'median_latency_ms', 'p99_latency_ms']].round(2))
    results_df.to_csv("complexity_results.csv")
    print("\nSummary results saved")


if __name__ == "__main__":
    asyncio.run(run_scenario_scalability())
    # asyncio.run(run_scenario_complexity())
