package greekfantasy.client.render;

import greekfantasy.client.render.model.NymphModel;
import greekfantasy.entity.DryadEntity;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class DryadRenderer<T extends DryadEntity> extends BipedRenderer<T, NymphModel<T>> {

  public DryadRenderer(final EntityRendererManager renderManagerIn) {
    super(renderManagerIn, new NymphModel<T>(0.0F), 0.25F);
  }

  /**
   * Returns the location of an entity's texture. Doesn't seem to be called unless
   * you call Render.bindEntityTexture.
   */
  @Override
  public ResourceLocation getEntityTexture(final T entity) {
    return entity.getVariant().getTexture();
  }
}
