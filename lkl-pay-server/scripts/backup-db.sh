#!/usr/bin/env bash
#
# 备份 lkl-pay-server 的 H2 数据库。
#
# 用法：
#   PAY_DB_PATH=/opt/juge/data/paydb ./backup-db.sh [保留天数]
#
# 环境变量：
#   PAY_DB_PATH   数据库文件路径（不含 .mv.db 后缀），需与 application.yml 的 spring.datasource.url 一致
#   BACKUP_DIR    备份输出目录，默认 <PAY_DB_PATH 所在目录>/backups
#   H2_JAR        h2 依赖 jar 的路径；设置后走 H2 自带的在线备份工具，可热备
#
# 说明：H2 文件库在服务运行期间处于打开状态，直接复制 .mv.db 可能得到不一致的快照。
# 本脚本优先使用 H2 的在线备份工具（需 H2_JAR）；否则回退为文件复制，
# 此时请先停止服务（systemctl stop lkl-pay-server）以保证一致性。

set -euo pipefail

DB_PATH="${PAY_DB_PATH:-./data/paydb}"
KEEP_DAYS="${1:-14}"
DB_DIR="$(cd "$(dirname "$DB_PATH")" && pwd)"
DB_NAME="$(basename "$DB_PATH")"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${BACKUP_DIR:-$DB_DIR/backups}"

mkdir -p "$BACKUP_DIR"

if [[ -n "${H2_JAR:-}" ]]; then
  echo "使用 H2 在线备份工具：$H2_JAR"
  java -cp "$H2_JAR" org.h2.tools.Backup \
    -file "$BACKUP_DIR/paydb-$STAMP.zip" \
    -dir "$DB_DIR" \
    -db "$DB_NAME"
else
  echo "警告：未设置 H2_JAR，改为直接复制数据库文件。"
  echo "      请确保服务已停止（systemctl stop lkl-pay-server），否则快照可能不一致。"
  cp -v "$DB_PATH.mv.db" "$BACKUP_DIR/paydb-$STAMP.mv.db"
fi

# 清理超过保留期的旧备份
find "$BACKUP_DIR" -type f -mtime "+$KEEP_DAYS" -delete

echo "备份完成 -> $BACKUP_DIR"