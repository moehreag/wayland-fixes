# wayland-fixes

# Minecraft & Wayland

Other mods already promise to run the game on wayland natively, yet they also tell you that for example you are going to loose the window icon. This mod doesn't, you won't have to miss out on anything!

This is a sibling mod to [legacy-lwjgl3](https://modrinth.com/mod/moehreag-legacy-lwjgl3) and more or less does the same things in terms of wayland compatibility, just on very different versions of the game. All sections in its description regarding this also apply to this mod.

> If you are running the game inside a sandbox, e.g. by using a launcher packaged as a flatpak, make sure to allow the paths listed below to be accessed for this mod to function correctly!

<details>
<summary>Accesses Paths & Programs and why</summary>

This part is mostly copied from the previously mentioned mod since it also applies here.

### Features

- Injects a .desktop file and the icon in the correct locations for them to function correctly
- Loads & Displays a virtual cursor on in-game screens to support setting the cursor position

### Properties

- `virtual_mouse.export` may be passed as `true` to export the loaded cursor to a `cursors` directory in the instance folder. It will contain all images contained in the cursor as well as all metadata. It will also create a human-readable metadata file for each image, and a `cursor.cursor` file in the cursor config format. This file may be used to generate a new X11 cursor out of the images with the `xcursorgen` program.

### Accessed Paths
- `$HOME/.local/share/applications/com.mojang.minecraft.desktop` (write)
- `$HOME/.local/share/icons/hicolor/apps/{16x16, 32x32}/minecraft.png` (write)
- `$HOME/.local/share/icons/<theme>/cursors/left_ptr` (read only)
- `$HOME/.icons/<theme>/cursors/left_ptr` (read only)
- `/usr/share/icons/<theme>/cursors/left_ptr` (read only)

### Accessed Programs
- `xdg-icon-resource` (to update the icon system)
- `gsettings` (to query for cursor theme & size)

</details>

### Credits

- KDE visual design group (the fallback cursor file shipped with the mod is from KDE's breeze cursor theme)
