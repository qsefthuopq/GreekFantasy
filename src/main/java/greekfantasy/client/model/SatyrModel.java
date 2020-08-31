package greekfantasy.client.model;

import greekfantasy.entity.SatyrEntity;
import net.minecraft.client.renderer.model.ModelRenderer;

public class SatyrModel<T extends SatyrEntity> extends HoofedBipedModel<T> {
  
  private final ModelRenderer rightEar;
  private final ModelRenderer leftEar;

  public SatyrModel(float modelSize) {
    super(modelSize, true, true);
    textureWidth = 64;
    textureHeight = 64;
    
    // nose
    this.bipedHead.setTextureOffset(24, 0).addBox(-1.0F, -3.0F, -5.0F, 2.0F, 1.0F, 1.0F, 0.0F, false);
  
    // right ear
    rightEar = new ModelRenderer(this, 56, 16);
    rightEar.setRotationPoint(-3.0F, -4.0F, -1.0F);
    rightEar.rotateAngleX = -0.2618F;
    rightEar.rotateAngleY = -0.3491F;
    rightEar.addBox(-1.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F, modelSize);
    this.bipedHead.addChild(rightEar);

    // left ear
    leftEar = new ModelRenderer(this, 56, 22);
    leftEar.setRotationPoint(4.0F, -4.0F, -1.0F);
    leftEar.rotateAngleX = -0.2618F;
    leftEar.rotateAngleY = 0.3491F;
    leftEar.addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 3.0F, modelSize);
    this.bipedHead.addChild(leftEar);

    // horns
    this.bipedHead.addChild(makeHornModel(modelSize, true));
    this.bipedHead.addChild(makeHornModel(modelSize, false));
  }
  
  private ModelRenderer makeHornModel(final float modelSize, final boolean isLeft) {
    final int textureX = isLeft ? 54 : 47;
    final float horn1X = isLeft ? 4.0F : -5.0F;
    final float horn2X = isLeft ? 8.25F : -1.25F;
    final float horn3X = isLeft ? 8.5F : -1.5F;
    
    final ModelRenderer horn3 = new ModelRenderer(this);
    horn3.setRotationPoint(0.0F, -3.0F, 0.0F);
    horn3.rotateAngleX = -0.7854F;
    horn3.setTextureOffset(textureX, 59).addBox(horn3X, -3.0F, 0.0F, 1.0F, 3.0F, 2.0F, modelSize);
    horn3.mirror = isLeft;
    
    final ModelRenderer horn2 = new ModelRenderer(this);
    horn2.setRotationPoint(-4.0F, -4.0F, -1.0F);
    horn2.rotateAngleX = -0.7854F;
    horn2.setTextureOffset(textureX, 54).addBox(horn2X, -3.0F, 0.0F, 1.0F, 3.0F, 2.0F, modelSize);
    horn2.addChild(horn3);
    horn2.mirror = isLeft;
    
    final ModelRenderer horn1 = new ModelRenderer(this);
    horn1.setRotationPoint(0.0F, -6.0F, -1.0F);
    horn1.rotateAngleX = 0.8727F;
    horn1.setTextureOffset(textureX, 48).addBox(horn1X, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F, modelSize);
    horn1.addChild(horn2);
    horn1.mirror = isLeft;
    
    return horn1;
  }
}
