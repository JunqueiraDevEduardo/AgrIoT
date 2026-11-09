#!/bin/bash
cd /home/estufa/agriot || exit 1
echo "[$(date '+%H:%M:%S')] a correr"
git add -A
git commit -q -m "Auto-sync $(date '+%Y-%m-%d %H:%M')" || exit 0
git pull -q --rebase origin main
git push -q origin main
echo "[$(date '+%H:%M:%S')] enviado"
