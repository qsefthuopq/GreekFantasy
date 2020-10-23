package greekfantasy.enchantment;

import greekfantasy.GFRegistry;
import greekfantasy.GreekFantasy;
import greekfantasy.entity.GeryonEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;

public class SmashingEnchantment extends Enchantment {
  
  private static final double BASE_RANGE = 4.0D;

  public SmashingEnchantment(final Enchantment.Rarity rarity) {
    super(rarity, EnchantmentType.WEAPON, new EquipmentSlotType[] { EquipmentSlotType.MAINHAND });
  }
  
  /**
   * @param entity the entity to check
   * @return whether the given entity should not be affected by smash attack
   **/
  private boolean isExemptFromSmashAttack(final Entity entity) {
    return entity.hasNoGravity();
  }
  
  private void useSmashAttack(final LivingEntity user, final Entity target) {
    // if entitiy is touching the ground, knock it into the air and apply stun
    if(target.isOnGround() && !isExemptFromSmashAttack(target)) {
      target.addVelocity(0.0D, 0.65D, 0.0D);
      target.attackEntityFrom(DamageSource.causeMobDamage(user), 0.25F);
      // stun effect (for living entities)
      if(target instanceof LivingEntity) {
        ((LivingEntity)target).addPotionEffect(new EffectInstance(GFRegistry.STUNNED_EFFECT, 65, 0));
      }
    }
  }
  
  @Override
  public void onEntityDamaged(LivingEntity user, Entity target, int level) {
    final double range = BASE_RANGE + 2.0D * level;
    final AxisAlignedBB aabb = new AxisAlignedBB(target.getPosition().up()).grow(range, BASE_RANGE, range);
    user.getEntityWorld().getEntitiesWithinAABBExcludingEntity(user, aabb)
      .forEach(e -> useSmashAttack(user, e));
  }
  
  @Override 
  public int getMinEnchantability(int level) { return 30; }
  @Override
  public int getMaxEnchantability(int level) { return 50; }
  @Override
  public boolean isTreasureEnchantment() { return GreekFantasy.CONFIG.isSmashingEnabled(); }
  @Override
  public boolean canVillagerTrade() { return false; }
  @Override
  public boolean canGenerateInLoot() { return false; }
  @Override
  public int getMaxLevel() { return 3; }
  @Override
  public boolean canApplyAtEnchantingTable(ItemStack stack) { return false; }
  @Override
  public boolean isAllowedOnBooks() { return false; }
}
