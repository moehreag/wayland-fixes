package io.github.moehreag.wayland_fixes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.moehreag.wayland_fixes.util.XDGPathResolver;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public class VirtualCursor {
	private static final Logger LOGGER = WaylandFixes.LOGGER;
	private final List<XCursor.ImageChunk> chunks = new ArrayList<>();
	private double virt_offset_x, virt_offset_y;
	private int current;
	private Identifier[] images;
	private long animationTime;
	private boolean virtual;
	private long windowHandle;
	private double last_x;
	private double last_y;
	private static final VirtualCursor INSTANCE = new VirtualCursor();

	public static VirtualCursor getInstance() {
		return INSTANCE;
	}

	private boolean mayVirtualize() {
		return MinecraftClient.getInstance().world != null;
	}

	/*
	 * whether we are on a screen where the virtual cursor is allowed
	 */
	private boolean isValidScreen() {
		Screen s = MinecraftClient.getInstance().currentScreen;
		return s instanceof HandledScreen || s instanceof ChatScreen || s instanceof BookEditScreen;
	}

	public void setup(long window) {
		this.windowHandle = window;
		virt_offset_x = 0;
		virt_offset_y = 0;
		loadCursor();
	}

	public void destroy() {
		for (Identifier i : images) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(i);
		}
	}

	/*
	 * Set the cursor position (of the virtual cursor)
	 */
	public void setCursorPosition(double x, double y) {
		virt_offset_y = y - last_y;
		virt_offset_x = x - last_x;
	}

	public void grabMouse(boolean grab) {
		if (grab) {
			virtual = false;
			GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
		} else {
			if (isValidScreen() && mayVirtualize()) {
				GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
				virtual = true;
			} else {
				GLFW.glfwSetInputMode(this.windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
			}
		}
	}

	public static void render(DrawContext context) {
		getInstance().draw(context);
	}

	/*
	 * Draw the virtual cursor
	 */
	private void draw(DrawContext context) {
		if (virtual && images[0] != null) {
			RenderSystem.enableBlend();
			RenderSystem.setShaderColor(1, 1, 1, 1);
			RenderSystem.setShaderTexture(0, images[current]);

			double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
			float x = (float) getX();
			float y = (float) getY();

			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 1000);
			context.getMatrices().scale((float) (1f / scale), (float) (1 / scale), 0);
			context.drawTexture(images[current], (int) ((x - (getCurrent().xhot / scale))), (int) ((y - (getCurrent().yhot / scale))), 0, 0, (int) (getCurrent().width), (int) (getCurrent().height), (int) (getCurrent().width), (int) (getCurrent().height));

			context.getMatrices().pop();
			advanceAnimation();
		}
	}

	private void advanceAnimation() {
		if (images.length > 1) {
			if (animationTime == 0 || System.currentTimeMillis() - animationTime > getCurrent().delay) {
				animationTime = System.currentTimeMillis();
				current++;
				if (current >= images.length) {
					current = 0;
				}
			}
		}
	}

	private XCursor.ImageChunk getCurrent() {
		return chunks.get(current);
	}

	/*
	 * Load the virtual cursor
	 */
	private void loadCursor() {
		XCursor cursor = SystemCursor.load();

		if (Boolean.getBoolean("virtual_mouse.export")) {
			cursor.export();
		}

		chunks.clear();
		for (XCursor.Chunk chunk : cursor.chunks) {
			if (chunk instanceof XCursor.ImageChunk) {
				XCursor.ImageChunk c = (XCursor.ImageChunk) chunk;
				if (c.getSubtype() == XDGPathResolver.getCursorSize()) {
					chunks.add(c);
				}
			}
		}

		current = 0;
		images = new Identifier[chunks.size()];
		for (int i = 0; i < images.length; i++) {
			XCursor.ImageChunk c = chunks.get(i);
			NativeImage image = new NativeImage((int) c.getWidth(), (int) c.getHeight(), true);
			for (int x = 0; x < c.getWidth(); x++) {
				for (int y = 0; y < c.getHeight(); y++) {
					image.setColor(x, y, (int) c.getPixels()[(int) (x + (c.getHeight() * y))]);
				}
			}
			images[i] = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("virtual_cursor", new NativeImageBackedTexture(image));
		}
	}

	public double handleMovementX(double x) {
		last_x = x;
		if (virtual) {

			/*
			 * Stop the virtual cursor from leaving the screen entirely
			 */
			while (getX() < 0) { // TODO get rid if the loops. loops are bad here.
				virt_offset_x++;
			}
			while (getX() >= MinecraftClient.getInstance().getWindow().getWidth() - 2) {
				virt_offset_x--;
			}
		}
		return getX();
	}

	public double handleMovementY(double y) {
		last_y = y;
		if (virtual) {

			/*
			 * Stop the virtual cursor from leaving the screen entirely
			 */
			while (getY() < 0) {
				virt_offset_y++;
			}
			while (getY() >= MinecraftClient.getInstance().getWindow().getHeight() - 2) {
				virt_offset_y--;
			}
		}
		return getY();
	}

	public double getX() {
		return virtual ? last_x + virt_offset_x : last_x;
	}

	public double getY() {
		return virtual ? last_y + virt_offset_y : last_y;
	}

	private static class SystemCursor {

		private static final SystemCursor INSTANCE = new SystemCursor();

		/*
		 * Loads the cursor file and parse it
		 */
		public static XCursor load() {

			try {
				byte[] c = IOUtils.toByteArray(INSTANCE.getArrowCursor());


				ByteBuffer buf = ByteBuffer.wrap(c);

				return XCursor.parse(buf);

			} catch (IOException e) {
				throw new IllegalStateException("Unable to load cursor texture!", e);
			}

		}

		private InputStream getArrowCursor() throws IOException {
			Path theme = XDGPathResolver.getIconTheme("cursors/left_ptr"); // load the arrow pointer cursor from the selected theme
			if (theme != null) {
				LOGGER.info("Loading system cursor: " + theme);
				return Files.newInputStream(theme);
			}

			LOGGER.info("Falling back to packaged cursor");

			return MinecraftClient.getInstance().getResourceManager().getResource(new Identifier("virtual_cursor", "default")).map(r -> {
				try {
					return r.getInputStream();
				} catch (IOException ignored) {
				}
				return null;
			}).orElse(this.getClass().getResourceAsStream("/assets/virtual_cursor/default"));
		}
	}

	@Data
	private static class XCursor {

		private final String magic;
		private final long headerLength;
		private final long fileVersion;
		private final long toCEntryCount;
		private final XCursor.TableOfContents[] toC;
		private final XCursor.Chunk[] chunks;

		/**
		 * Parse a cursor icon file
		 *
		 * @param buf the data of the file
		 * @return a parsed cursor object
		 */
		public static XCursor parse(ByteBuffer buf) {

			String magic = getString(buf, 4);
			if (!"Xcur".equals(magic)) {
				throw new IllegalArgumentException("Not an Xcursor file! Magic: " + magic);
			}

			long headerLength = getInt(buf);
			long version = getInt(buf);
			long ntoc = getInt(buf);

			XCursor.TableOfContents[] toc = new XCursor.TableOfContents[(int) ntoc];
			XCursor.Chunk[] chunks = new XCursor.Chunk[(int) ntoc];

			for (int i = 0; i < ntoc; i++) {
				/*
				 * Procedure:
				 *  - read a table of contents
				 *  - read the corresponding chunk from the file without modifying the buffer's indices
				 *  - repeat until all tables are read
				 */
				XCursor.TableOfContents table = new XCursor.TableOfContents(getInt(buf), getInt(buf), getInt(buf));
				toc[i] = table;
				chunks[i] = parseChunk(buf, table);
			}

			return new XCursor(magic, headerLength, version, ntoc, toc, chunks);
		}

		/*
		 * read a chunk from a corresponding table
		 */
		private static XCursor.Chunk parseChunk(ByteBuffer buf, XCursor.TableOfContents table) {
			int pos = buf.position();
			buf.position((int) table.position);
			XCursor.Chunk c;
			switch ((int) table.type) {
				case 0xfffe0001: // Comment
					c = parseComment(buf, table); // I have yet to find a single cursor file that uses these, not even `xcursorgen` supports them.
					break;
				case 0xfffd0002: // Image
					c = parseImage(buf, table);
					break;
				default:
					throw new IllegalArgumentException("Unrecognized type: " + table.type);
			}
			buf.position(pos);
			return c;
		}

		/*
		 * parse an image chunk
		 */
		private static XCursor.Chunk parseImage(ByteBuffer buf, XCursor.TableOfContents table) {
			long size = getInt(buf);
			if (size != 36) {
				throw new IllegalArgumentException("not an image chunk! size != 36: " + size);
			}

			long type = getInt(buf);
			if (type != 0xfffd0002L || type != table.type) {
				throw new IllegalArgumentException("not an image chunk! type != image: " + type);
			}

			long subtype = getInt(buf);
			if (subtype != table.subtype) {
				throw new IllegalArgumentException("not an image chunk! subtype != table.subtype: " + subtype);
			}
			long version = getInt(buf);

			long width = getInt(buf);

			if (width > 0x7ff) {
				throw new IllegalArgumentException("image too large! width > 0x7ff: " + width);
			}

			long height = getInt(buf);
			if (height > 0x7ff) {
				throw new IllegalArgumentException("image too large! height > 0x7ff: " + height);
			}
			long xhot = getInt(buf);
			if (xhot > width) {
				throw new IllegalArgumentException("xhot outside image!: " + xhot);
			}
			long yhot = getInt(buf);
			if (yhot > height) {
				throw new IllegalArgumentException("yhot outside image!: " + yhot);
			}
			long delay = getInt(buf);

			long[] pixels = new long[(int) (width * height)];

			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = getInt(buf);
			}

			return new XCursor.ImageChunk(size, type, subtype, version, width, height, xhot, yhot, delay, pixels);
		}

		/*
		 * parse a comment chunk
		 */
		private static XCursor.Chunk parseComment(ByteBuffer buf, XCursor.TableOfContents table) {
			long size = getInt(buf);
			if (size != 20) {
				throw new IllegalArgumentException("not a comment chunk! size != 20: " + size);
			}

			long type = getInt(buf);
			if (type != 0xfffe0001L || type != table.type) {
				throw new IllegalArgumentException("not a comment chunk! type != comment: " + type);
			}

			long subtype = getInt(buf);
			if (subtype != table.subtype) {
				throw new IllegalArgumentException("not a comment chunk! subtype != table.subtype: " + subtype);
			}
			long version = getInt(buf);
			long commentLength = getInt(buf);
			String comment = getString(buf, (int) commentLength);
			return new XCursor.CommentChunk(size, type, subtype, version, commentLength, comment);
		}

		private static long getInt(ByteBuffer buf) {
			return readUnsignedInteger(buf).longValue();
		}

		private static BigInteger readUnsignedInteger(ByteBuffer buffer) {
			return new BigInteger(1, readBytes(buffer));
		}

		private static byte[] readBytes(ByteBuffer buffer) {
			byte[] bytes = new byte[4];
			for (int i = 0; i < 4; i++) {
				bytes[4 - 1 - i] = buffer.get();
			}
			return bytes;
		}

		private static String getString(ByteBuffer buf, int length) {
			byte[] data = new byte[length];
			for (int i = 0; i < length; i++) {
				data[i] = buf.get();
			}
			return new String(data, StandardCharsets.UTF_8);
		}

		/*
		 * export the cursor files
		 */
		public void export() {

			Path dir = Paths.get("cursors");
			try {
				Files.walkFileTree(dir,
						new SimpleFileVisitor<Path>() {
							@Override
							public FileVisitResult postVisitDirectory(
									Path dir, IOException exc) throws IOException {
								Files.delete(dir);
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult visitFile(
									Path file, BasicFileAttributes attrs)
									throws IOException {
								Files.delete(file);
								return FileVisitResult.CONTINUE;
							}
						});
				Files.createDirectory(dir);
			} catch (IOException e) {
				LOGGER.warn("Failed to create clean export directory, export will likely fail!", e);
			}

			LOGGER.info("Exporting chunks..");
			for (XCursor.Chunk c : chunks) {
				c.export();
			}
		}

		@Data
		private static class TableOfContents {
			private final long type;
			private final long subtype; // type-specific label - image size
			private final long position; // byte position in the file;
		}

		@Data
		private abstract static class Chunk {
			private final long length; // header length
			private final long type, subtype; // must match Table of Contents
			private final long version; // Chunk type version

			public Chunk(long length, long type, long subtype, long version) {
				this.length = length;
				this.type = type;
				this.subtype = subtype;
				this.version = version;
			}

			public abstract void export();
		}

		/*
		 * Comment header size: 20 bytes
		 *
		 * Type: 0xfffe0001
		 * subtype: 1 (COPYRIGHT), 2 (LICENSE), 3 (OTHER)
		 * version: 1
		 */
		@Getter
		@ToString
		@EqualsAndHashCode(callSuper = true)
		private static class CommentChunk extends XCursor.Chunk {

			private final long length;
			private final String comment;

			public CommentChunk(long length, long type, long subtype, long version, long commentLength, String comment) {
				super(length, type, subtype, version);
				this.length = commentLength;
				this.comment = comment;
			}

			public void export() {

				String name;
				if (getSubtype() == 1) {
					name = "COPYRIGHT";
				} else if (getSubtype() == 2) {
					name = "LICENSE";
				} else {
					name = "COMMENT";
				}

				try {
					Files.write(Paths.get("cursors", name), comment.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
				} catch (IOException e) {
					LOGGER.warn("Image export failed!", e);
				}

			}
		}

		/*
		 * Image header size: 36 bytes
		 *
		 * Type: 0xfffd002
		 * subtype: image size
		 * version: 1
		 *
		 */
		@Getter
		@ToString
		@EqualsAndHashCode(callSuper = true)
		private static class ImageChunk extends XCursor.Chunk {

			private final long width, height, xhot, yhot;
			private final long delay;
			private final long[] pixels;

			public ImageChunk(long length, long type, long subtype, long version, long width, long height, long xhot, long yhot, long delay, long[] pixels) {
				super(length, type, subtype, version);
				this.width = width;
				this.height = height;
				this.xhot = xhot;
				this.yhot = yhot;
				this.delay = delay;
				this.pixels = pixels;
			}

			public int[] getImage() {
				int[] data = new int[pixels.length];

				for (int i = 0; i < pixels.length; i++) {
					data[i] = (int) pixels[i];
				}

				return data;
			}

			public void export() {

				BufferedImage im = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_ARGB);

				int[] data = getImage();

				for (int i = 0; i < data.length; i++) {
					im.setRGB((int) (i % width), (int) (i / height), data[i]);
				}


				List<String> lines = new ArrayList<>();
				lines.add("[Sizes]");
				lines.add("Cursor: " + getSubtype() + "x" + getSubtype());
				lines.add("Image: " + width + "x" + height);
				lines.add("[Hotspots]");
				lines.add("X: " + xhot);
				lines.add("Y: " + yhot);
				lines.add("[Delay]");
				lines.add("" + delay);


				String imageName = getSubtype() + "x" + getSubtype();
				String name = imageName;
				if (delay != 0) {

					int i = 0;
					name = imageName + "_" + i;
					while (new File("cursors", name + ".png").exists()) {
						i++;
						name = imageName + "_" + i;
					}


				}

				String cursor = (getSubtype() + " " + xhot + " " + yhot + " " + name + ".png");
				Path cursorFile = Paths.get("cursors", "cursor.cursor");
				if (delay != 0) {
					cursor += " " + delay;
					if (Files.exists(cursorFile)) {
						cursor = "\n" + cursor;
					}
				}

				try {
					ImageIO.write(im, "png", new File("cursors", name + ".png"));
					Files.write(Paths.get("cursors", name + ".txt"), lines, StandardOpenOption.CREATE);
					Files.write(cursorFile, cursor.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				} catch (IOException e) {
					LOGGER.warn("Image export failed!", e);
				}
			}
		}
	}
}
