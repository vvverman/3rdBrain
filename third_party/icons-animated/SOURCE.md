# Animated Phosphor source

Kasha uses the Phosphor subset from `ln-dev7/icons-animated` as a reference source for interaction motion.

- upstream: `ln-dev7/icons-animated`
- pinned commit: `4d8269768ccfc73c09b773b90be8158cb4f21f4c`
- license: MIT, see `LICENSE`
- upstream catalog at this revision: arrow-down, arrow-left, arrow-right, arrow-up, bell, calendar, check, chevron-down, chevron-left, chevron-right, chevron-up, download, envelope, eye, filter, gear, heart, house, list, lock, pencil, plus, refresh, save, search, share, star, trash, upload, user, x.

Kasha does not import React or Motion at runtime. Motion behavior is translated into Compose Multiplatform and kept local in the repository. Product glyph geometry remains Phosphor; no second icon family is allowed.

Mappings currently used directly from the source catalog:

- BACK / NEXT: arrow-left / arrow-right — directional 0 → ±40 → 0 movement, 0.4 s;
- UP / DOWN: arrow-up / arrow-down — directional 0 → ±40 → 0 movement, 0.4 s;
- SETTINGS: gear — 180° rotation, 0.5 s;
- PLUS: plus — staged vertical/horizontal reveal, translated to a short scale/reveal motion for the filled glyph;
- CHECK: check — draw/reveal motion, translated to a short reveal for the filled glyph;
- EDIT: pencil;
- DELETE: trash;
- HOME: house;
- TASKS: list.

For Kasha-specific glyphs that are not present in this upstream catalog, the shape remains Phosphor and uses a restrained motion following the same timing/easing language; no non-Phosphor geometry is introduced.
