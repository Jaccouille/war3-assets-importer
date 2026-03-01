# War3AssetsImporter

A desktop tool for importing custom 3D models (MDX) and textures (BLP) into Warcraft 3 map files (`.w3x` / `.w3m`).

Built with Java Swing. Work in progress.

---

## Features

- Browse and select MDX/BLP assets from a folder tree
- Preview textures before importing
- Automatically generates unit definitions and places them on the map
- Draws a placement preview so you can see where units will appear
- Supports English and French UI

## Requirements

- Java 11 or newer
- A Warcraft 3 map file (`.w3x` or `.w3m`)
- A folder containing your custom MDX/BLP assets

## Download

Grab the latest `War3AssetsImporter.jar` from the [Releases](../../releases) page.

## Running

```bash
java -jar War3AssetsImporter.jar
```

No installation needed.

## Building from Source

Requires JDK 11+. The Gradle wrapper is included — no global Gradle installation needed.

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/War3AssetsImporter.git
cd War3AssetsImporter

# Build a runnable fat JAR
./gradlew shadowJar
# Output: build/libs/War3AssetsImporter.jar

# Or run directly
./gradlew run
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Usage

1. Click **Open Map** and select a `.w3x` or `.w3m` file
2. Click **Open Assets Folder** and point to your MDX/BLP directory
3. Check the assets you want to import in the tree panel
4. Configure unit naming and placement options in the **Import Configuration** tab
5. Click **Process & Save** — the tool writes a new file named `processed_<mapname>.w3x`

## License

[MIT](LICENSE)
