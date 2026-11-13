English | [简体中文](README.md)

# Bring the TV Back to Your Home

**A TV launcher that belongs to you — and fits the whole family.**

Turn on the TV and see your family's photos, a clear clock, and the apps you actually use. You decide what appears on the home screen, keeping only what you need, so the TV blends naturally into life at home.

This project started as an Android TV home launcher and is gradually connecting the devices in your home — NAS, photo-processing services, Mac, and browser. The TV desktop is the center, and family photos tie these devices together: memories tucked away on hard drives can become scenery you see every day.

![Family TV desktop: time, date, lunar calendar, and frequently used apps, with a locally outpainted landscape background](docs/screenshots/tv-desktop.jpg)

*An actual screenshot of the TV desktop, using a landscape demo image outpainted locally by this project.*

## A Home Screen Reserved for Your Life

A clean layout places the time, date, day of the week, lunar calendar, and your favorite apps exactly where they belong. Pick the apps your family actually uses and arrange them your way, so every time the TV turns on it starts from a familiar, predictable place.

Family photos can rotate slowly as the desktop background. The background feature can be toggled on or off at any time, and local wallpapers are also supported.

## When the TV Idles, Look at Your Family

Press Back on the desktop, or leave the TV idle for a while, and it can enter a photo frame. Photos play automatically, and you can flip back and forth at will, picking a photo from the thumbnail bar at the bottom to linger on.

The full photo is shown over a soft, blurred version of the same image, so both landscape and portrait photos display naturally. Transitions are smooth, a glass-style control bar appears only when needed, then quietly fades away.

Upcoming photos are preloaded. If the network slows down, the current photo simply stays on screen a little longer; it will never turn black or pop up a notice just because the next one isn't ready. The TV keeps only a limited cache, so playback keeps going.

## NAS: A Steady Stream of Photos to the Desktop

Your photos stay on your own NAS. The service samples photos on demand and hands them to the desktop background, the photo frame, and other clients — no need to import the whole library first, and no need to build a thumbnail gallery of every photo.

In the background, multiple batches of TV wallpapers can be prepared ahead of time, continuously replenishing new content. From a management page on your home network, you can check processing progress, browse existing batches, and expand each batch to preview its photos. The service can be installed as a QNAP app and run persistently.

From storage to display, all of this happens between the devices in your own home.

![NAS management console: the finished-photo pool, outpainting progress, and photo previews](docs/screenshots/nas-manage.jpg)

*The actual management UI, shown with a separate landscape demo dataset for the finished-photo pool, pending tasks, and photo previews; the counts, batches, and capture dates are demo data.*

## Local Outpainting: Making Portrait Photos Fit the Big Screen

Many memories captured on phones are portrait photos. The optional local AI outpainting service processes photos in advance on your Mac, extending the surrounding scene for a landscape TV screen, then softly blends the extended regions so the original photo remains the star of the picture.

A single portrait photo can go from a small image in the middle of the screen to a full landscape background: the people and the original photo stay in the center, while the colors, light, and environment of the photo continue on both sides. The extended areas are then gently softened, letting the picture recede into the background so your eyes stay on real family and real memories.

The "same-photo blurred background" in the photo frame fills the surrounding area directly from the original photo; AI outpainting instead generates new surrounding content, best processed in advance and used as the desktop background. The two presentation styles can be used independently.

| Before outpainting: the portrait original, centered | After outpainting: a landscape background that fits the TV |
| --- | --- |
| ![Portrait mountain photo before outpainting](docs/screenshots/outpaint-before.jpg) | ![Landscape mountain scene after local AI outpainting and edge softening](docs/screenshots/outpaint-after.jpg) |

*An actual before/after comparison of the same photo. The demo input is a portrait crop of a publicly licensed landscape photo; the right image was generated and softened by the current local workflow. Everything beyond the original photo is AI-generated and does not represent a real scene. [See the portrait input](docs/screenshots/outpaint-source.jpg) · [Source material and processing notes](docs/screenshots/CREDITS.md).*

The NAS picks photos and schedules tasks, the Mac processes them in the background, and the TV receives finished backgrounds. You can filter photos from the last ten years that contain people, and review the results to exclude unsuitable images — such as ones where outpainting introduced clearly visible faces.

The finished pool can build up 10 batches of photos step by step; after that, new batches are added periodically to replace older ones. If processing is slow or temporarily interrupted, the TV keeps using the photos that are already available.

Outpainting can be turned off, and the original photos are always preserved. A random photo frame that shows photos directly can also be used on its own, without outpainting the whole library.

## The Same Family Memories, Also on Your Mac

A standalone native Mac photo frame connects to the same NAS photo service. Open the app, move it to another display, enter full screen, and family photos can keep you company.

It has its own window, playback, and cache, running independently of the browser. While you work, develop, or browse on the main screen, photos keep playing on the other display. It supports auto-rotation, keyboard switching, pause, a glass thumbnail bar, and restoring local photos.

![Native Mac photo frame: a mountain photo with the glass thumbnail bar at the bottom](docs/screenshots/mac-frame.jpg)

*The actual native Mac photo frame, using a landscape sample photo.*

A web version is also available: open it in a browser at home and enjoy the same clean photo playback experience.

![Web photo frame: a snowy mountain lake, soft background, and photo switcher](docs/screenshots/web-frame.jpg)

*The actual web version. The large image and thumbnails in the screenshot are publicly licensed landscape assets, [image sources and licenses](docs/screenshots/CREDITS.md).*

## Built Around One Family Desktop, All Connected

**The NAS stores and serves photos → the Mac processes backgrounds on demand → the TV presents the family desktop and photo frame.**

The native Mac photo frame and the web client can also pull photos straight from the NAS. Each part is used as needed, all serving one simple wish: let your own devices show a little more of your own family.

Photo playback currently focuses on the JPEG format. Photo storage and outpainting can both stay local — nothing needs to be uploaded to the cloud.

---

The TV desktop is built on top of [CleanYourTV](https://github.com/Gathin23/cleanyourtv), retaining the original license and third-party notices.

> Note: the rest of the documentation in this repository (e.g. the `docs/` directory) is written in Simplified Chinese.
