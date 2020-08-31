package greekfantasy.proxy;

import java.util.Map.Entry;

import greekfantasy.client.render.CentaurRenderer;
import greekfantasy.client.render.GorgonRenderer;
import greekfantasy.client.render.MinotaurRenderer;
import greekfantasy.client.render.NymphRenderer;
import greekfantasy.client.render.SatyrRenderer;
import greekfantasy.client.render.SirenRenderer;
import greekfantasy.entity.NymphEntity;
import greekfantasy.entity.NymphEntity.Variant;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends Proxy {
    
  @Override
  public void registerEntityRenders() {
    final IRenderFactory<NymphEntity> NYMPH_RENDERER = NymphRenderer::new;
    for(final Entry<Variant, EntityType<? extends NymphEntity>> t : nymphEntityMap.entrySet()) {
      RenderingRegistry.registerEntityRenderingHandler(t.getValue(), NYMPH_RENDERER);
    }
    RenderingRegistry.registerEntityRenderingHandler(SATYR_ENTITY, SatyrRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(CENTAUR_ENTITY, CentaurRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(MINOTAUR_ENTITY, MinotaurRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(SIREN_ENTITY, SirenRenderer::new);
    RenderingRegistry.registerEntityRenderingHandler(GORGON_ENTITY, GorgonRenderer::new);
  }
  
}
