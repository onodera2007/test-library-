package github.com.gengyoubo.mixins;

import github.com.gengyoubo.fix.SpecialLatex.PatreonBenefitsFix;
import github.com.gengyoubo.fix.SpecialLatex.SpecialLatex;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.TagUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpecialVariantMixin {
    @Unique
    private static final String SPECIAL_FORM_PREFIX = "special/form_";
    @Unique
    private static final String CHANGED_E_SPECIAL_FORM_ID = "ChangedESpecialFormId";

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void changede$normalizeSpecialVariantTagBeforeBaseLoad(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(CHANGED_E_SPECIAL_FORM_ID)) {
            return;
        }

        ResourceLocation variantId = ResourceLocation.tryParse(tag.getString(CHANGED_E_SPECIAL_FORM_ID));
        if (variantId == null || !PatreonBenefitsFix.isSpecialFormId(variantId)) {
            return;
        }

        // Normalize to static SPECIAL_LATEX key so upstream registry lookup won't warn
        // about missing dynamic entries during base player load.
        tag.putString("TransfurVariant", TransfurVariant.SPECIAL_LATEX.toString());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void changede$restoreSpecialVariant(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(CHANGED_E_SPECIAL_FORM_ID)) {
            return;
        }
        ResourceLocation variantId = ResourceLocation.tryParse(tag.getString(CHANGED_E_SPECIAL_FORM_ID));
        if (variantId == null) {
            return;
        }
        if (!PatreonBenefitsFix.isSpecialFormId(variantId)) {
            return;
        }
        final ResourceLocation finalVariantId = variantId;

        TransfurVariant<?> variant = PatreonBenefitsFix.resolveVariant(finalVariantId);
        if (variant == null) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        ProcessTransfur.setPlayerTransfurVariant(self, variant, null, 1.0f, false, entity -> {
            if (tag.contains("Leash", 10)) {
                entity.getChangedEntity().setLeashInfoTag(tag.getCompound("Leash"));
            }
        });

        ProcessTransfur.getPlayerTransfurVariantSafe(self).ifPresent(instance -> {
            if (tag.contains("TransfurVariantData")) {
                instance.load(tag.getCompound("TransfurVariantData"));
            }

            if (instance.getChangedEntity() instanceof SpecialLatex specialLatex) {
                String path = finalVariantId.getPath();
                if (path.startsWith(SPECIAL_FORM_PREFIX)) {
                    try {
                        specialLatex.setSpecialForm(UUID.fromString(path.substring(SPECIAL_FORM_PREFIX.length())));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        });
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void changede$saveSpecialVariantTag(CompoundTag tag, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!ProcessTransfur.isPlayerTransfurred(self)) {
            // Player is currently human: clear all transfur state markers in save data,
            // including generic transfur tags and our special-form extension tags.
            tag.remove(CHANGED_E_SPECIAL_FORM_ID);
            tag.remove("TransfurVariant");
            tag.remove("TransfurVariantData");
            return;
        }
        ProcessTransfur.getPlayerTransfurVariantSafe(self).ifPresent(instance -> {
            ResourceLocation id = instance.getFormId();
            if (PatreonBenefitsFix.isSpecialFormId(id)) {
                tag.putString(CHANGED_E_SPECIAL_FORM_ID, id.toString());
            } else {
                tag.remove(CHANGED_E_SPECIAL_FORM_ID);
            }
        });
    }
}
