# Exercise media guides

Apex Fit keeps workout/session data local in Room. Exercise media is deliberately separate so adding or removing images/videos never requires a database migration.

## Free Exercise DB images and instructions

- Source: https://github.com/yuhonas/free-exercise-db
- License: Unlicense / public-domain dedication.
- `app/src/main/assets/exercise_media.json` maps every bundled Apex Fit exercise ID to the upstream exercise, image paths, and source instructions.
- Images are loaded from the upstream raw GitHub repository only when the form guide is opened, then cached by Coil.
- The workout logger itself never preloads image or video media.

## YouTube Shorts / videos

`app/src/main/assets/exercise_videos.json` stores curated YouTube embeds separately from the exercise database. Entries may be either a legacy string URL/video ID or a structured record:

```json
{
  "dumbbell-lateral-raise": {
    "video_id": "qpAvhLiy50Y",
    "url": "https://www.youtube.com/shorts/qpAvhLiy50Y",
    "title": "The Perfect Lateral Raise — Do This for Bigger Shoulders",
    "match_type": "fuzzy",
    "match_score": 78,
    "channel": "DEMIC"
  }
}
```

The parser accepts YouTube Shorts URLs, regular watch URLs, `youtu.be` URLs, embed URLs, or raw 11-character YouTube video IDs.

The player is created only after the user taps **Play technique video**. It uses YouTube's privacy-enhanced embed domain, does not autoplay, and does not download or rehost the video.

### DEMIC curation

The November 2026 import started from a 876-row Free Exercise DB to DEMIC Shorts candidate CSV. The raw matcher produced 330 candidates, but Apex Fit does not import every fuzzy result.

Production import rules:

- exact candidates are eligible;
- fuzzy candidates require a match score of at least 65;
- the exercise and video must pass a compatible movement-family gate;
- known misleading variants are explicitly rejected;
- fuzzy stretches are rejected unless manually verified;
- custom Apex exercise aliases inherit a video only through their explicit Free Exercise DB source mapping.

This produced 230 approved Free Exercise DB source exercises and 248 Apex Fit exercise IDs after aliases. Fuzzy videos are labeled **RELATED TECHNIQUE** in the UI rather than represented as exact demonstrations.

Known rejected examples include Kettlebell Thruster → glute-thrust content, Calf Press on Leg Press Machine → generic leg-press content, and Handstand Push-Ups → pike-push-up content.

For production, continue to prefer videos Apex Fit owns or has permission to use. Third-party YouTube content must remain embedded/streamed through YouTube; do not download or bundle the audiovisual content, cover YouTube controls/branding, or gate access to the YouTube video itself behind payment.
