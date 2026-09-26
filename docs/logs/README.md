# Phone log dumps

Raw `*.txt` dumps are gitignored (large / noisy). Summaries live in:

- [`../OUTSIDE_LAN_PLAY_HANG_BEHAVIOR_REPORT.md`](../OUTSIDE_LAN_PLAY_HANG_BEHAVIOR_REPORT.md)

Capture without wiping the ring buffer:

```bash
ADB=$HOME/Library/Android/sdk/platform-tools/adb
$ADB logcat -d -v threadtime > docs/logs/phone-logcat-full.txt
```

Do **not** use `make logs` for forensics (`logcat -c` destroys evidence).
