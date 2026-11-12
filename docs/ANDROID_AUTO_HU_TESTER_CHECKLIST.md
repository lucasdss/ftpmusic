# Android Auto — Real HU Tester Checklist

Date: 2026-11-12
Covers ADR-0158 / 0159 / 0160 + voice search review.

## Connect availability alert

- [ ] First Android Auto session → phone notice once
- [ ] Reconnect same car → quiet
- [ ] Settings → Notifications → toggle OFF → no notice
- [ ] Bluetooth A2DP only (no Auto) → no Auto availability notice

## Home hub

- [ ] Home is first browse root child
- [ ] Continue listening / Mixes / Favorite playlists / artists / radio show when data exists
- [ ] Surprise Me starts playback
- [ ] Empty sections omitted

## Like / Dislike feedback

- [ ] Thumbs visible on Auto Now Playing (not only overflow)
- [ ] Like fills / unfills; Dislike fills / unfills
- [ ] Radio current item still hides reactions

## Voice / search

- [ ] “Play \<song\>” starts album context
- [ ] “Play \<artist\>” starts artist material
- [ ] In-Auto typed search still returns a result list
- [ ] Offline / LocalOnly unknown query → no crash, no network search3
