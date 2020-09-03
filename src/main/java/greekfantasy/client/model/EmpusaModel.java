package greekfantasy.client.model;

import greekfantasy.entity.EmpusaEntity;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;

public class EmpusaModel<T extends EmpusaEntity> extends BipedModel<T> {

  public EmpusaModel(float modelSize) {
    super(modelSize);
    this.textureWidth = 64;
    this.textureHeight = 64;
    
    this.bipedHead = new ModelRenderer(this);
    this.bipedHead.setTextureOffset(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, modelSize);
    this.bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);
    
    this.bipedHeadwear = new ModelRenderer(this, 32, 0);
    this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, modelSize + 0.5F);
    this.bipedHeadwear.setRotationPoint(0.0F, 0.0F, 0.0F);
    
    this.bipedBody = new ModelRenderer(this, 16, 16);
    this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, modelSize);
    this.bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);
    
    // arms
    
    this.bipedLeftArm = new ModelRenderer(this, 32, 48);
    this.bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize);
    this.bipedLeftArm.setRotationPoint(5.0F, 2.5F, 0.0F);
    this.bipedLeftArm.mirror = true;
    
    this.bipedRightArm = new ModelRenderer(this, 40, 16);
    this.bipedRightArm.addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, modelSize);
    this.bipedRightArm.setRotationPoint(-5.0F, 2.5F, 0.0F);
    
    this.bipedRightLeg = new ModelRenderer(this);
    this.bipedRightLeg.setRotationPoint(-1.9F, 12.0F, 0.0F);
    this.bipedRightLeg.setTextureOffset(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0.0F, false);

    this.bipedLeftLeg = new ModelRenderer(this);
    this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);
    this.bipedLeftLeg.setTextureOffset(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0.0F, false);
  }
  
}
