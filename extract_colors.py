import re

file_path = "/Users/sunqiqi/ans/app/src/main/java/com/example/myapplication/MainActivity.kt"

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

print("=== Found hex colors in MainActivity.kt ===")
pattern = re.compile(r'Color\(0xFF[0-9A-Fa-f]{6}\)')
for idx, line in enumerate(lines):
    match = pattern.findall(line)
    if match:
        # 排除我们已经在 178-185 行定义过的变量
        if idx + 1 >= 170 and idx + 1 <= 190:
            continue
        print(f"Line {idx+1}: {line.strip()}")
