#!/bin/bash
cd /home/estufa/agriot || exit 1
git add -A
git commit -m "Auto-sync $(date '+%Y-%m-%d %H:%M')" || exit 0
git pull --rebase origin main
git push origin main
