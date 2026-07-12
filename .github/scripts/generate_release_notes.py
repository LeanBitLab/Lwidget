import os
import subprocess

def run_cmd(cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        return ""

def main():
    tag_name = os.environ.get("TAG_NAME", "v-dev")
    
    # Get previous tag matching v*
    prev_tag = run_cmd(["git", "describe", "--tags", "--match=v*", "--abbrev=0", "HEAD^"])
    log_range = f"{prev_tag}..HEAD" if prev_tag else "HEAD"
    
    # Get commits
    commits_raw = run_cmd(["git", "log", log_range, "--pretty=format:%s"])
    raw_list = [c.strip() for c in commits_raw.split("\n") if c.strip()] if commits_raw else []
    
    # Filter commits (keep only conventional app changes)
    commits = []
    allowed_prefixes = ("feat:", "fix:", "refactor:", "perf:")
    ignored_keywords = ("gitignore", "badge", "workflow", "readme", "fleet")
    for c in raw_list:
        c_lower = c.lower()
        if c_lower.startswith(allowed_prefixes):
            if "jules" in c_lower:
                continue
            if any(kw in c_lower for kw in ignored_keywords):
                continue
            commits.append(c)
            
    # Build release notes markdown
    lines = []
    lines.append(f"## {tag_name}")
    lines.append("")
    lines.append("🚀 **What's New**")
    lines.append("")
    
    for c in commits:
        lines.append(f"- {c}")
        
    # Ensure file is written correctly
    with open("release_notes.md", "w") as f:
        f.write("\n".join(lines).strip() + "\n")

if __name__ == "__main__":
    main()
