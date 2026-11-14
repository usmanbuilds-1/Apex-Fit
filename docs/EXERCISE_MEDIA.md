# Exercise media guides

Apex Fit keeps workout/session data local in Room. Exercise media is deliberately separate so adding or removing images/videos never requires a database migration.

## Free Exercise DB images

- Source: https://github.com/yuhonas/free-exercise-db
- License: Unlicense / public-domain dedication.
- `app/src/main/assets/exercise_media.json` maps every bundled Apex Fit exercise ID to the upstream exercise/image paths.
- Images are loaded from the upstream raw GitHub repository only when the form guide is opened, then cached by Coil.
- The workout logger itself never preloads image media.

## YouTube Shorts / videos

`app/src/main/assets/exercise_videos.json` maps an Apex Fit exercise ID to either a YouTube video ID or a normal YouTube URL.

Example:

```json
{
  "dumbbell-lateral-raise": "https://www.youtube.com/shorts/VIDEO_ID_HERE"
}
```

The parser accepts:

- `https://youtube.com/shorts/<id>`
- `https://youtube.com/watch?v=<id>`
- `https://youtu.be/<id>`
- a raw 11-character YouTube video ID

The player is created only after the user taps **Play technique video**. It uses YouTube's privacy-enhanced embed domain, does not autoplay, and does not download or rehost the video.

For production, curate videos you own or have permission to use and verify that embedding remains enabled. Do not download Shorts into the app, cover YouTube controls/branding, or make access to the YouTube video itself a paid/gated action.
