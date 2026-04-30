import subprocess
import re
import statistics

def run_benchmark():
    print("🚀 Running benchmark iteration...")
    # Using -Dghost.warmup=100 for a realistic balance
    result = subprocess.run(["./gradlew", ":ghost-benchmark:run", "-PskipTests=true", "-Dghost.skipTests=true", "-Dghost.warmup=100", "-x", "test"], capture_output=True, text=True)
    output = result.stdout + result.stderr
    
    # Regex to capture metrics for GHOST and KSER in the STEADY-STATE table
    # Example: | 3    | GHOST    |      0.200 |       31.6 |
    ghost_match = re.search(r"\|\s+\d+\s+\|\s+GHOST\s+\|\s+([\d.]+)\s+\|\s+([\d.]+)\s+\|", output)
    kser_match = re.search(r"\|\s+\d+\s+\|\s+KSER\s+\|\s+([\d.]+)\s+\|\s+([\d.]+)\s+\|", output)
    
    if ghost_match and kser_match:
        return {
            "ghost_time": float(ghost_match.group(1)),
            "ghost_mem": float(ghost_match.group(2)),
            "kser_time": float(kser_match.group(1)),
            "kser_mem": float(kser_match.group(2))
        }
    return None

def audit(iterations=15):
    print(f"\n📊 Starting Performance Audit ({iterations} iterations)...")
    results = []
    for i in range(iterations):
        print(f"Iteration {i+1}/{iterations}...")
        res = run_benchmark()
        if res:
            results.append(res)
        else:
            print("❌ Failed to parse benchmark results.")
    
    if not results:
        return

    avg_ghost_time = statistics.mean([r["ghost_time"] for r in results])
    avg_ghost_mem = statistics.mean([r["ghost_mem"] for r in results])
    avg_kser_time = statistics.mean([r["kser_time"] for r in results])
    avg_kser_mem = statistics.mean([r["kser_mem"] for r in results])
    
    speed_gain = ((avg_kser_time / avg_ghost_time) - 1.0) * 100.0
    mem_efficiency = ((avg_kser_mem / avg_ghost_mem) - 1.0) * 100.0 if avg_ghost_mem > 0 else 0.0

    print("\n" + "="*60)
    print("📈 AUDIT RESULTS (AVERAGE OF {} RUNS)".format(len(results)))
    print("="*60)
    print("GHOST: {:.3f} ms / {:.1f} KB".format(avg_ghost_time, avg_ghost_mem))
    print("KSER:  {:.3f} ms / {:.1f} KB".format(avg_kser_time, avg_kser_mem))
    print("-" * 60)
    print("Speed Superiority:  {}{:.1f}%".format("+" if speed_gain >= 0 else "", speed_gain))
    print("Memory Superiority: {:.1f}%".format(mem_efficiency))
    print("="*60 + "\n")
    
    print("Recommended Commit Message:")
    print("[PERFORMANCE] GHOST: {:.3f} ms / {:.1f} KB | KSER: {:.3f} ms / {:.1f} KB | Speed: {}{:.1f}% | Memory: {:.1f}%"
          .format(avg_ghost_time, avg_ghost_mem, avg_kser_time, avg_kser_mem, "+" if speed_gain >= 0 else "", speed_gain, mem_efficiency))

if __name__ == "__main__":
    audit(15)
