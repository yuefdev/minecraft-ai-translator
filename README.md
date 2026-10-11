<div align="center">

# AutoTranslation

**Play any modpack in Turkish. No resource packs, no waiting for mod authors.**

AutoTranslation is a client-side Minecraft mod that translates the English text of your mods into natural Turkish
while you play: item names, tooltips, menus, FTB Quests, Patchouli books and more.

[![Licence: AGPL-3.0](https://img.shields.io/github/license/yuefdev/minecraft-ai-translator?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/yuefdev/minecraft-ai-translator?style=flat)](https://github.com/yuefdev/minecraft-ai-translator/stargazers)
[![Build](https://img.shields.io/github/actions/workflow/status/yuefdev/minecraft-ai-translator/versions.yml?label=build)](https://github.com/yuefdev/minecraft-ai-translator/actions)
![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2%20%E2%80%93%2026.1.2-62b47a)

[Türkçe](README_TR.md) · [Install](#install) · [Supported versions](#supported-versions) · [Settings](#settings) · [FAQ](#faq)

</div>

---

## Why

Most mods ship in English only, and translation pull requests can take months or never get merged. Big modpacks
have tens of thousands of strings. AutoTranslation translates them for you, in the background, and remembers the
result.

| Without the mod | With the mod (examples) |
|---|---|
| `Iron Ingot` | `Demir Külçesi` |
| `Kill the Ender Dragon and collect its egg.` | `Ender Ejderhasını öldür ve yumurtasını topla.` |
| `Deals §c%s§r damage to nearby mobs` | `Yakındaki yaratıklara §c%s§r hasar verir` |

Colour codes (`§6…§r`), placeholders (`%s`, `{name}`), URLs and Patchouli commands are kept intact. A translation
that loses any of them is rejected.

## Features

- **Works out of the box.** With no setup it uses keyless Google Translate. For better, context-aware Turkish, paste
  a free [Gemini](https://aistudio.google.com/apikey) key. Groq, Mistral, OpenRouter, a local
  [Ollama](https://ollama.com) model and any OpenAI-compatible service work too. The mod detects the service from
  the key.
- **Translates every mod, automatically.** Language files of mods that have no Turkish file, plus English text
  hard-coded into their screens. No mod list to maintain.
- **Quests and books.** FTB Quests (titles, subtitles, descriptions, tasks, rewards), Patchouli books, and
  BetterQuesting on 1.12.2.
- **Does not touch your data.** Translation happens when text is drawn. Quest IDs, conditions, rewards, commands
  and progress stay as they are.
- **Persistent offline cache.** Each text is translated once. After that it works without internet.
- **Background pre-translation (1.12.2).** Translates the language files of all your mods at start-up, so most
  text is already Turkish the first time you see it.
- **Shared translation packs.** Ready-made translations are downloaded (download only, nothing is uploaded) and
  used before any translation service is asked. A new install of a popular pack starts mostly translated.
- **Your word wins.** Editable glossary (the same item always gets the same name) and personal corrections that
  override everything.
- **Careful with limits.** Short texts are batched into one request. When a free quota runs out, the mod switches
  to a backup model, then to keyless Google, and cached text keeps working.
- **Never translates what you type.** Chat, signs, books and name fields are left alone.

## Supported versions

| Minecraft | Loader | Java | Quests and books | Settings |
|---|---|---|---|---|
| 1.20.1 | Forge 47 | 17 | FTB Quests, Patchouli | In-game screen (Turkish) |
| 1.21.1 | NeoForge 21.1 | 21 | FTB Quests, Patchouli | `settings.json` |
| 26.1.2 | NeoForge 26.1.2 | 25 | FTB Quests | `settings.json` |
| 1.19.2 | Forge 43 | 17 | FTB Quests, Patchouli | `settings.json` |
| 1.18.2 | Forge 40 | 17 | FTB Quests, Patchouli | `settings.json` |
| 1.16.5 | Forge 36 | 8+ | FTB Quests, Patchouli | `settings.json` |
| 1.12.2 | Forge 14.23.5 | 8 | BetterQuesting, FTB Quests, Patchouli | `settings.json` |

Every version builds in CI and is started in a real Minecraft client with and without the optional mods. See
[what is and is not verified](docs/VERIFICATION.md) (Turkish). Fabric code is in the repository but is not built or
tested yet.

## Install

1. Download the jar for your version from the [Releases](https://github.com/yuefdev/minecraft-ai-translator/releases)
   page and put it in your `mods/` folder.
2. **1.20.1 only:** also install [Architectury API](https://modrinth.com/mod/architectury-api) and
   [Cloth Config](https://modrinth.com/mod/cloth-config). Other versions need no extra mods.
3. In the game, set **Options → Language → Türkçe (Türkiye)**.

That is all. Quests, books and the optional mods are picked up if they are installed. If not, the game starts
normally.

> If the language is English, nothing is translated. The mod only runs for the language you chose.

### Better translations with a free Gemini key (recommended)

Google Translate is fast but does not know the context. An AI model translates whole sentences with their meaning.
The free tier needs no credit card.

1. Create a key at <https://aistudio.google.com/apikey>.
2. Give it to the mod in one of these ways:
   - environment variable `AUTOTRANSLATION_API_KEY`, or
   - a file `AutoTranslation/secrets/api-key.txt` in your game folder containing only the key, or
   - on 1.20.1: **Mods → AutoTranslation → Config → Provider**.
3. Restart the game. The mod sees from the key that it is Gemini and switches by itself.

The key is never written to settings or logs.

## Settings

Files live in `<game folder>/AutoTranslation/`.

| File | Purpose |
|---|---|
| `settings.json` | Provider, model, requests per minute, what to translate. Created on first start. |
| `glossary.json` | Terms that must always be translated the same way. |
| `corrections.json` | Your own translations for exact English texts. Highest priority. |
| `cache/translations.jsonl` | The offline cache. Safe to delete. |

Useful options in `settings.json`:

| Option | Default | Meaning |
|---|---|---|
| `provider` | `"auto"` | `auto`, `google-free`, `gemini`, `groq`, `mistral`, `openrouter`, `ollama` or `custom` |
| `pretranslateLanguageFiles` | `true` | Translate all mod language files in the background (1.12.2) |
| `sharedPacks` | `true` | Use downloaded ready-made translations. Set `false` to switch off all pack downloads. |
| `showOriginal` | `false` | Show the English text next to the translation |
| `requestsPerMinute` | `60` | Rate limit for the translation service |

Step-by-step guide for 1.12.2: [docs/KURULUM_1.12.2.md](docs/KURULUM_1.12.2.md) (Turkish).

## Privacy

- **Mod text is sent to the translation service you use.** By default this is Google Translate's keyless endpoint;
  with a key it is that key's service. Only the text of mods and the game is sent. Text you type is never sent.
- **Shared packs are download only.** The mod fetches static files from a pack site and verifies each one against a
  SHA-256 checksum and the original text's placeholders before using it. Nothing from your game is uploaded. Packs
  contain no English source text, only hashes and translations. Turn it off with `"sharedPacks": false`.
  Details: [docs/ORTAK_PAKET.md](docs/ORTAK_PAKET.md) (Turkish).
- API keys stay out of settings files and logs.

## FAQ

**Nothing is translated.** Check that the game language is Turkish, and that `enabled` is not `false` in
`settings.json`.

**Some text is English for a few seconds.** A text is translated the first time it is shown. After that it comes
from the cache.

**Is it a server mod?** No. It is client-side and works on any server.

**Is the keyless Google service reliable?** It is an unofficial endpoint with a low rate limit (around 20 requests
a minute) and Google may block it if it sees too much traffic. It is the zero-setup fallback. A free Gemini key is
better in quality and in limits.

**Other languages?** The engine takes the target language from the game's language and has been written for any
Minecraft language code, but Turkish is the only language it has been tuned and tested for.

**Can I edit a bad translation?** Yes. Add it to `corrections.json` or, on 1.20.1, use the Corrections tab.

**Is the translation always right?** No. It is machine translation. Please report bad cases. Corrections and the
glossary exist for exactly this.

## Build from source

```bash
./gradlew :core:test       # engine unit tests (no network needed)
./gradlew :forge:build     # 1.20.1 jar, in forge/build/libs/
```

Other versions live under `versions/` and are built by the `versions.yml` workflow. Design notes:
[docs/DESIGN.md](docs/DESIGN.md) (Turkish).

## Contributing

Issues and pull requests are welcome, especially bug reports with a log, better Turkish for the glossary, and
support for more versions. Run the engine tests before you open a pull request.

## Licence and credits

AutoTranslation is free software under the **GNU Affero General Public License v3.0** (see [LICENSE](LICENSE)).

The 1.20.1 module (`common/`, `forge/`, `fabric/`) is based on
[AutoTranslation](https://github.com/Moirstral/AutoTranslation) by 墨涤千尘 (AGPL-3.0). The translation engine
(`core/`), the other version modules (`versions/`), the Turkish pipeline, the quest and book integrations and the
shared packs were added in this project. Details are in [NOTICE.md](NOTICE.md).

FTB Quests, Patchouli, BetterQuesting, Architectury API and Cloth Config belong to their authors and are not
included in this repository.
