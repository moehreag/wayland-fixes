package io.github.moehreag.wayland_fixes.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.moehreag.wayland_fixes.WaylandFixes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.InputSupplier;
import org.apache.commons.io.IOUtils;

public class DesktopFileInjector {
	public static final String APP_ID = "com.mojang.minecraft";
	private static final String ICON_NAME = "minecraft.png";
	private static final String FILE_NAME = APP_ID + ".desktop";
	private static final String RESOURCE_LOCATION = "/assets/wayland_fixes/" + FILE_NAME;
	private static final List<Path> injectedLocations = new ArrayList<>();

	public static void inject() {
		Runtime.getRuntime().addShutdownHook(new Thread(DesktopFileInjector::uninject));

		try (InputStream stream = DesktopFileInjector.class.getResourceAsStream(RESOURCE_LOCATION)) {
			Path location = getDesktopFileLocation();

			String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow(IllegalStateException::new)
					.getMetadata().getVersion().getFriendlyString();
			injectFile(location, String.format(IOUtils.toString(Objects.requireNonNull(stream), StandardCharsets.UTF_8),
					version, ICON_NAME.substring(0, ICON_NAME.lastIndexOf("."))).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			WaylandFixes.LOGGER.error("Failed to inject icon: ", e);
		}

	}

	public static void setIcon(List<InputSupplier<InputStream>> icons) {
		for (InputSupplier<InputStream> supplier : icons) {
			try {
				BufferedImage image = ImageIO.read(supplier.get());
				Path target = getIconFileLocation(image.getWidth(), image.getHeight());
				injectFile(target, IOUtils.toByteArray(supplier.get()));
			} catch (IOException e) {
				return;
			}
		}
		updateIconSystem();
	}

	private static void injectFile(Path target, byte[] data) {
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, data);
			injectedLocations.add(target);
		} catch (IOException e) {
			WaylandFixes.LOGGER.error("Failed to inject file: ", e);
		}
	}


	private static Path getIconFileLocation(int width, int height) {
		return XDGPathResolver.getUserDataLocation().resolve("icons/hicolor").resolve(width + "x" + height)
				.resolve("apps").resolve(ICON_NAME);
	}

	private static Path getDesktopFileLocation() {
		return XDGPathResolver.getUserDataLocation().resolve("applications").resolve(FILE_NAME);
	}

	private static void updateIconSystem() {
		ProcessBuilder builder = new ProcessBuilder("xdg-icon-resource", "forceupdate");
		try {
			builder.start();
		} catch (IOException ignored) {
		}
	}

	private static void uninject() {
		injectedLocations.forEach(p -> {
			try {
				Files.deleteIfExists(p);
			} catch (IOException ignored) {

			}
		});
		updateIconSystem();
	}
}
