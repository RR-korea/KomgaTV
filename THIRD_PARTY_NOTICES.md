# Third-Party Software Notices and Information

This project incorporates components from the following open source projects:

---

## Komelia

- **Project:** Komelia (https://github.com/Snd-R/Komelia)
- **Copyright:** Copyright 2024 Snd-R
- **License:** Apache License, Version 2.0
- **Used Components / Logic:**
  - Reader viewing options definitions and default values (`ImageReaderSettings.kt`):
    - `pageLayout` (`SINGLE_PAGE`, `DOUBLE_PAGES`, `DOUBLE_PAGES_NO_COVER`)
    - `scaleType` (`SCREEN`, `FIT_WIDTH`, `FIT_HEIGHT`, `ORIGINAL`)
    - `stretchToFit` (boolean)
    - `readingDirection` priority and option mapping
  - Paged spread map calculation and offset layout algorithm (`PagedReaderState.kt`):
    - Double page spread segmentation (`constructDoublePageSegments`)
    - Offset handling and landscape page standalone layout (`buildSpreadMapForDoublePages`)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

## komga-client

- **Project:** komga-client (https://github.com/Snd-R/komga-client)
- **License:** MIT License
- **Use:** Komga REST API client library (dependency).

---

## Noto Sans

- **Project:** Noto Sans (https://github.com/notofonts/latin-greek-cyrillic)
- **Copyright:** Copyright 2022 The Noto Project Authors
- **License:** SIL Open Font License 1.1
- **Use:** The "KomgaTV" wordmark in the app icon and TV banner is rendered from Noto Sans Bold.

---

## Acknowledgements

KomgaTV was made with deep respect for the **Komga** (https://github.com/gotson/komga) and
**Komelia** (https://github.com/Snd-R/Komelia) projects. KomgaTV is an independent project and is not affiliated with either.

