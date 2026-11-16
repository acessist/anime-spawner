# Anime Spawner

**[Download for Windows x64](https://github.com/acessist/anime-spawner/releases/latest/download/AnimeSpawner-1.1.0-Windows-x64.zip)** · [Release notes](https://github.com/acessist/anime-spawner/releases/latest)

Extract the ZIP and run **Anime Spawner.exe**.

Import your own GIFs and images and place them on your Windows desktop. Anime Spawner uses transparent, draggable windows with controls for size, playback speed, opacity and mirroring.

## Run

For a packaged build, extract the ZIP and open **Anime Spawner.exe**. Keep the `app` and `runtime` folders beside it.

To run from source, install JDK 21+ and use:

```bat
build.cmd run
```

## Import and manage animations

- Click **Import animation** or drop files into the library. Animated GIF, APNG, PNG, JPEG and BMP are supported.
- Select an item and click **Spawn on desktop**. Drag it to move; scroll to resize; double-click to pause. Right-click for more controls.
- Click **Delete selected…** to remove an import. After confirmation, its library copy, assigned sound and desktop instances are removed. The original files you imported remain untouched.
- Save an arrangement under **Desktop Scenes**. Scenes refer to library items, so scenes using a deleted animation need updating.

Closing the manager leaves the app in the system tray when available. Use the tray menu or **Configuration → Quit Anime Spawner** to exit.

## Features

- Animated GIF and APNG playback with transparency.
- Search, favorites, per-animation settings and multiple desktop instances.
- Saved scenes, optional session restoration and multi-monitor placement.
- Global pause/hide controls and per-instance pause/drag lock.
- Sprite-sheet import, background-color removal, GIF export and PNG frame export.
- WAV, AIFF and AU sounds on spawn.
- Optional video and WebP conversion using a separately installed FFmpeg executable.

FFmpeg conversion takes the first eight seconds at 15 fps and 320-pixel width. Set its executable path in Configuration.
