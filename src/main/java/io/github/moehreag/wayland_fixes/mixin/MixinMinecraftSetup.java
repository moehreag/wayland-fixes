package io.github.moehreag.wayland_fixes.mixin;

import io.github.moehreag.wayland_fixes.VirtualCursor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftSetup {

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initBackendSystem()Lnet/minecraft/util/TimeSupplier$Nanoseconds;"))
	private void preGLFWInit(RunArgs runArgs, CallbackInfo ci){
		if (GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_WAYLAND)) {
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_WAYLAND); // enable wayland backend if supported
		}
	}

	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;textureManager:Lnet/minecraft/client/texture/TextureManager;", ordinal = 0, shift = At.Shift.AFTER))
	private void onTextureManagerSetup(RunArgs runArgs, CallbackInfo ci){
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			VirtualCursor.getInstance().setup(MinecraftClient.getInstance().getWindow().getHandle());
		}
	}
}
