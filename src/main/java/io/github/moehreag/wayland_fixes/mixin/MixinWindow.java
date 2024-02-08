package io.github.moehreag.wayland_fixes.mixin;

import java.io.IOException;

import io.github.moehreag.wayland_fixes.util.DesktopFileInjector;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.Icons;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourcePack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", shift = At.Shift.AFTER, remap = false))
	private void onWindowHints(WindowEventHandler windowEventHandler, MonitorTracker monitorTracker, WindowSettings windowSettings, String string, String string2, CallbackInfo ci) {
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE); // disable an unsupported function on wayland
			DesktopFileInjector.inject();
			GLFW.glfwWindowHintString(GLFW.GLFW_WAYLAND_APP_ID, DesktopFileInjector.APP_ID);
		}
	}

	@Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
	private void injectIcon(ResourcePack resourcePack, Icons icons, CallbackInfo ci) {
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			try {
				DesktopFileInjector.setIcon(icons.getIcons(resourcePack));
				ci.cancel();
			} catch (IOException ignored) {

			}
		}
	}
}
