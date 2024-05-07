package team.creative.playerrevive.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    
    @Accessor
    public PostChain getBlurEffect();
    
}
