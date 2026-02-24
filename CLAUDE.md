# War3AssetsImporter - Claude Context

## Project Overview
Java Swing desktop application for importing custom 3D models (MDX) and textures (BLP) into Warcraft 3 map files (.w3x/.w3m). Work in progress.

## Build & Run

```bash
# Build
./gradlew build          # Linux/Mac
gradlew.bat build        # Windows

# Run
./gradlew run

# Test
./gradlew test
```

The project uses the Gradle wrapper — no global Gradle installation needed.

## Key Dependencies
- **wc3libs** (`net.moonlightflower`) — parse W3I, WTS, W3U, IMP, DOO_UNITS Warcraft 3 formats
- **JMPQ3** (`com.github.inwc3:1.7.14`) — read/write MPQ archives (.w3x map files)
- **blp-iio-plugin.jar** (local, `libs/`) — Java ImageIO plugin for BLP texture format
- **JUnit 5** — testing (no tests written yet)

## Project Structure

```
src/main/java/org/example/
├── Main.java                  # Entry point
├── MainFrame.java             # Main window (split pane layout)
├── Wc3MapAssetImporter.java   # Core import logic (reads/writes MPQ)
├── MapProcessingTask.java     # SwingWorker for background processing
├── AssetTreePanel.java        # Checkbox tree for MDX/BLP file selection
├── MapOptionsPanel.java       # Map metadata, preview image, options
├── PreviewPanel.java          # Asset preview (256x256)
├── JCheckBoxTree.java         # Custom JTree with tri-state checkboxes
├── JCheckBoxTreeNode.java     # Tree node with checked/partial state
├── TreeNodeData.java          # Record: name, isFile, relativePath, size, fileCount
├── CameraBounds.java          # Singleton: map camera boundary coords
├── UnitPlacementGrid.java     # Grid calculator for placing units on map
├── UnitIDGenerator.java       # Generates unique 4-char unit IDs (x000..z999)
├── UnitGenerator.java         # Unit generation helpers
├── StringUtils.java           # Game version string formatting
└── TrigStrReplacer.java       # Trigger string replacement utility
```

## Architecture

**UI flow:** `MainFrame` hosts a horizontal split between `MapOptionsPanel` (left) and a vertical split of `AssetTreePanel` + `PreviewPanel` (right).

**Processing flow:**
1. User opens a `.w3x` map → `MainFrame` loads metadata via `Wc3MapAssetImporter`
2. User selects assets in `AssetTreePanel` (checkbox tree)
3. "Process & Save" triggers `MapProcessingTask` (SwingWorker) → copies map to `processed_<name>.w3x`, inserts selected MDX/BLP files into MPQ archive, updates W3U/IMP/DOO_UNITS data

**Custom checkbox tree:** `JCheckBoxTree` fires `CheckChangeEvent` on toggle; parent nodes track partial-selection state. Uses mouse listener to prevent default JTree selection behavior.

**Unit ID generation:** IDs are 4 chars `[a-z][0-9][0-9][0-9]`, cycling from `x000` to `z999` then wrapping to `a000`.

## Settings / Config
- `build.gradle` — dependencies and build config
- `settings.gradle` — includes local `../wc3libs` build
- No external config files — all options are in-UI at runtime

## Notes
- No test coverage yet; `src/test/` does not exist
- The worktree `sleepy-hodgkin` is the active Claude workspace
- `todo.md` at project root tracks pending tasks
