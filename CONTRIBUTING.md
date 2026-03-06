# Contributing

## Reporting Bugs

Open an issue and include:
- What you did
- What you expected to happen
- What actually happened
- Your OS and Java version
- The map file or asset type involved (if relevant)

## Suggesting Features

Open an issue describing the feature and the use case behind it.

## Submitting a Pull Request

1. Fork the repository and create a branch from `main`
2. Make your changes
3. Verify the project builds: `./gradlew build`
4. Open a pull request with a short description of what changed and why

## Adding a Language

The UI uses Java `ResourceBundle` for i18n. All strings live in property files under `src/main/resources/`.

**1. Copy the base bundle and name it for your locale**

```
src/main/resources/messages.properties        ← base (English)
src/main/resources/messages_fr.properties     ← French (existing example)
src/main/resources/messages_de.properties     ← German (new example)
```

The suffix must be a valid [ISO 639-1 language code](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes).

**2. Translate every value in the new file**

Keep all keys identical to `messages.properties`. Only translate the values. Placeholders like `{0}` must be kept as-is.

```properties
# messages_de.properties
app.title=Warcraft 3 Modell Importer
button.openMap=Karte öffnen
status.ready=Bereit
# ... etc.
```

**3. Add a Help page for your language**

Copy `src/main/resources/help_en.html` to `help_de.html` (matching your language code) and translate the content. The app falls back to `help_en.html` if no matching file is found.

**4. Register the locale in `LanguagePanel.java`**

In `src/main/java/com/hiveworkshop/gui/settings/LanguagePanel.java`, add your locale to the list of available languages:

```java
locales = new Locale[]{
    Locale.ENGLISH,
    Locale.FRENCH,
    Locale.GERMAN,   // add this
};
```

**5. Test it**

Run the app, open Settings → Language, select your language, and verify all panels update correctly.

## Code Style

No strict style guide — just try to match the surrounding code. The project uses standard Java conventions.
