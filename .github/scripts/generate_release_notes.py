import os
import subprocess

def run_cmd(cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        return ""

def main():
    tag_name = os.environ.get("TAG_NAME", "v-dev")
    commit_sha = run_cmd(["git", "rev-parse", "--short", "HEAD"])
    
    # Get previous tag
    prev_tag = run_cmd(["git", "describe", "--tags", "--abbrev=0", "HEAD^"])
    log_range = f"{prev_tag}..HEAD" if prev_tag else "HEAD"
    
    # Get commits
    commits_raw = run_cmd(["git", "log", log_range, "--pretty=format:%s"])
    commits = [c.strip() for c in commits_raw.split("\n") if c.strip()] if commits_raw else []
    
    # Categorized lists
    customization = []
    behavior = []
    performance = []
    under_the_hood = []
    
    for commit in commits:
        commit_lower = commit.lower()
        if any(x in commit_lower for x in ["theme", "color", "appearance", "style", "font", "preset", "outline", "transparency", "opacity"]):
            customization.append(commit)
        elif any(x in commit_lower for x in ["layout", "click", "action", "alarm", "clock", "calendar", "tasks", "steps", "battery", "temp", "weather", "data", "storage", "ram", "screen_time"]):
            behavior.append(commit)
        elif any(x in commit_lower for x in ["perf", "optimize", "cache", "speed", "fast"]):
            performance.append(commit)
        else:
            under_the_hood.append(commit)
            
    # Build release notes markdown
    lines = []
    lines.append(f"## {tag_name}")
    lines.append(f"Commit: {commit_sha}")
    lines.append("")
    
    lines.append("💖 **Support Our Work**")
    lines.append("We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a sponsor. A huge thank you to all our current supporters!")
    lines.append("")
    
    lines.append("🚀 **What's New**")
    lines.append("")
    
    if customization:
        lines.append("🎨 **Customization & Appearance**")
        for c in customization:
            lines.append(f"- {c}")
        lines.append("")
        
    if behavior:
        lines.append("⌨️ **Layouts & Keyboard Behavior**")
        for c in behavior:
            lines.append(f"- {c}")
        lines.append("")
        
    if performance:
        lines.append("⚡ **Performance & Spellchecking**")
        for c in performance:
            lines.append(f"- {c}")
        lines.append("")
        
    if under_the_hood:
        lines.append("⚙️ **Under the Hood**")
        for c in under_the_hood:
            lines.append(f"- {c}")
        lines.append("")
        
    lines.append("📦 **Downloads**")
    
    # Ensure file is written correctly
    with open("release_notes.md", "w") as f:
        f.write("\n".join(lines))

if __name__ == "__main__":
    main()
