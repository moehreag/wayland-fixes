package io.github.moehreag.wayland_fixes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.moehreag.wayland_fixes.VirtualCursor;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Mouse.class)
public abstract class MixinMouse {

	@Shadow public abstract boolean isCursorLocked();

	@Shadow public abstract double getX();

	@Shadow public abstract double getY();

	@ModifyArgs(method = "method_22689", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;onCursorPos(JDD)V"))
	private void modifyCursorPos(Args args) {
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			args.set(1, VirtualCursor.getInstance().handleMovementX(args.get(1)));
			args.set(2, VirtualCursor.getInstance().handleMovementY(args.get(2)));
		}
	}

	@Inject(method = {"lockCursor", "unlockCursor"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;cursorLocked:Z", ordinal = 1, shift = At.Shift.AFTER))
	private void onLockCursor(CallbackInfo ci){
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			VirtualCursor.getInstance().grabMouse(isCursorLocked());
		}
	}

	@WrapOperation(method = {"lockCursor", "unlockCursor"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/InputUtil;setCursorParameters(JIDD)V"))
	private void onLockCursorSetCursorPosition(long l, int i, double d, double e, Operation<Void> original){
		if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
			if (isCursorLocked()) {
				original.call(l, i, d, e);
			}
		} else {
			VirtualCursor.getInstance().setCursorPosition(getX(), getY());
		}
	}
}
