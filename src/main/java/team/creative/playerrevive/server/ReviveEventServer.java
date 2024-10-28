package team.creative.playerrevive.server;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import team.creative.creativecore.common.config.premade.MobEffectConfig;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.cap.Bleeding;
import team.creative.playerrevive.packet.HelperPacket;

public class ReviveEventServer {
    
    public static boolean isReviveActive(Entity player) {
        if (player instanceof Player p && p.isCreative() && !PlayerRevive.CONFIG.bleeding.triggerForCreative)
            return false;
        return PlayerRevive.CONFIG.bleedInSingleplayer || player.getServer().isPublished();
    }
    
    @SubscribeEvent
    public void playerTick(PlayerTickEvent event) {
        if (event.phase == Phase.START && event.side == LogicalSide.SERVER && isReviveActive(event.player)) {
            Player player = event.player;
            if (!player.isAlive())
                return;
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            
            if (revive.isBleeding()) {
                revive.tick(player);
                
                if (revive.downedTime() % 5 == 0)
                    PlayerReviveServer.sendUpdatePacket(player);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                
                for (MobEffectConfig effect : PlayerRevive.CONFIG.bleeding.bleedingEffects)
                    player.addEffect(effect.create());
                
                if (PlayerRevive.CONFIG.bleeding.shouldGlow)
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10));
                
                if (revive.revived())
                    PlayerReviveServer.revive(player);
                else if (revive.bledOut())
                    PlayerReviveServer.kill(player);
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerLeave(PlayerLoggedOutEvent event) {
        IBleeding revive = PlayerReviveServer.getBleeding(event.getEntity());
        if (revive.isBleeding())
            PlayerReviveServer.kill(event.getEntity());
        if (!event.getEntity().level().isClientSide)
            PlayerReviveServer.removePlayerAsHelper(event.getEntity());
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void playerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Player && !event.getEntity().level().isClientSide) {
            Player target = (Player) event.getTarget();
            Player helper = event.getEntity();
            IBleeding revive = PlayerReviveServer.getBleeding(target);
            if (revive.isBleeding()) {
                event.setCanceled(true);
                if (PlayerRevive.CONFIG.revive.needReviveItem) {
                    if (PlayerRevive.CONFIG.revive.consumeReviveItem && !revive.isItemConsumed()) {
                        if (PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem())) {
                            if (!helper.isCreative()) {
                                helper.getMainHandItem().shrink(1);
                                helper.getInventory().setChanged();
                            }
                            revive.setItemConsumed();
                        } else {
                            if (!helper.level().isClientSide)
                                helper.sendSystemMessage(Component.translatable("playerrevive.revive.item", PlayerRevive.CONFIG.revive.reviveItem.description()));
                            return;
                        }
                    } else if (!PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem()))
                        return;
                }
                
                PlayerReviveServer.removePlayerAsHelper(helper);
                revive.revivingPlayers().add(helper);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(target.getUUID(), true), (ServerPlayer) helper);
            }
        }
    }
    
    private boolean doesByPass(Player player, DamageSource source) {
        var registry = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        if (source.type() == registry.getOrThrow(PlayerRevive.BLED_TO_DEATH))
            return true;
        if (PlayerRevive.CONFIG.bypassDamageSources.contains(source.getMsgId()))
            return true;
        if (PlayerRevive.CONFIG.bypassDamageSources.contains(source.typeHolder().unwrapKey().get().location().toString()))
            return true;
        return false;
    }
    
    @SubscribeEvent
    public void playerDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            if (revive.isBleeding()) {
                if (doesByPass(player, event.getSource()))
                    return;
                
                if (revive.bledOut())
                    event.setCanceled(true);
                
                if (revive.downedTime() <= PlayerRevive.CONFIG.bleeding.initialDamageCooldown)
                    event.setCanceled(true);
                
                if (event.getSource().getEntity() instanceof Player) {
                    if (PlayerRevive.CONFIG.bleeding.disablePlayerDamage)
                        event.setCanceled(true);
                } else if (event.getSource().getEntity() instanceof LivingEntity) {
                    if (PlayerRevive.CONFIG.bleeding.disableMobDamage)
                        event.setCanceled(true);
                } else if (PlayerRevive.CONFIG.bleeding.disableOtherDamage)
                    event.setCanceled(true);
                
            } else if (PlayerRevive.CONFIG.revive.abortOnDamage)
                PlayerReviveServer.removePlayerAsHelper(player);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerDied(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && isReviveActive(event.getEntity()) && !event.getEntity().level().isClientSide) {
            if (!doesByPass(player, event.getSource())) {
                IBleeding revive = PlayerReviveServer.getBleeding(player);
                
                if (revive.bledOut() || revive.isBleeding()) {
                    if (revive.isBleeding())
                        PlayerRevive.CONFIG.sounds.death.play(player, SoundSource.PLAYERS);
                    for (Player helper : revive.revivingPlayers())
                        PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) helper);
                    revive.revivingPlayers().clear();
                    return;
                }
                
                PlayerReviveServer.removePlayerAsHelper(player);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) player);
                
                PlayerReviveServer.startBleeding(player, event.getSource());
                
                if (player.isPassenger())
                    player.stopRiding();
                
                event.setCanceled(true);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                
                player.setHealth(PlayerRevive.CONFIG.bleeding.bleedingHealth);
                
                if (PlayerRevive.CONFIG.bleeding.bleedingMessage)
                    if (PlayerRevive.CONFIG.bleeding.bleedingMessageTrackingOnly) {
                        if (player.level().getChunkSource() instanceof ServerChunkCache chunkCache)
                            chunkCache.broadcastAndSend(player, new ClientboundSystemChatPacket(Component.translatable("playerrevive.chat.bleeding", player.getDisplayName()), false));
                    } else
                        player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("playerrevive.chat.bleeding", player.getDisplayName()), false);
            }
        }
    }
    
    @SubscribeEvent
    public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player)
            event.addCapability(PlayerRevive.BLEEDING_NAME, new ICapabilityProvider() {
                
                private LazyOptional<IBleeding> bleed = LazyOptional.of(Bleeding::new);
                
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return PlayerRevive.BLEEDING.orEmpty(cap, bleed);
                }
            });
    }
    
}
