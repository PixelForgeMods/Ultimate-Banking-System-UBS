package net.austizz.ultimatebankingsystem.client.model;

import net.austizz.ultimatebankingsystem.block.custom.ATMBlock;
import net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.client.AtmControls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Copycat faces are terrain quads so vanilla, Sodium and shader packs light them as wall blocks. */
public final class AtmWallBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockState> MATERIAL = new ModelProperty<>();
    private static final ModelProperty<BlockPos> POSITION = new ModelProperty<>();
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem","block/atm_copycat_base");
    private final int cell;
    private final Direction facing;

    public AtmWallBakedModel(BakedModel base,BlockState state) {
        super(base);cell=state.getValue(InWallATMBlock.PART);facing=state.getValue(ATMBlock.FACING);
    }
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType renderType) {
        return net.neoforged.neoforge.common.util.TriState.TRUE;
    }
    @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data) {
        var root=InWallATMBlock.origin(pos,state);
        var material=level.getBlockEntity(root) instanceof AtmBlockEntity atm?atm.wallMaterial(cell):null;
        return data.derive().with(MATERIAL,material==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():material).with(POSITION,pos.immutable()).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType) {
        var result=new ArrayList<>(originalModel.getQuads(state,side,random,data,renderType));
        if(side!=null)return result;
        var mc=Minecraft.getInstance();var material=data.get(MATERIAL);
        if(material!=null && material.isAir())material=null;
        var materialModel=material==null?null:mc.getBlockRenderer().getBlockModel(material);
        var rotation=BlockModelRotation.by(0,AtmControls.rotation(facing));
        var bakery=new FaceBakery();
        for(var panel:AtmControls.wallPanels()) {
            if(panel.cell()!=cell)continue;
            var b=panel.bounds().move(-(cell%2)*16,-(cell/2)*16,0);
            for(var direction:Direction.values()) {
                var worldDirection=Direction.rotate(rotation.getRotation().getMatrix(),direction);
                var candidates=materialModel==null?List.<BakedQuad>of():materialModel.getQuads(material,worldDirection,random,ModelData.EMPTY,null);
                var source=candidates.isEmpty()?null:candidates.getFirst();
                var sprite=source!=null?source.getSprite():materialModel!=null?materialModel.getParticleIcon():mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(DEFAULT);
                int tint=source==null?-1:source.getTintIndex();
                var face=new BlockElementFace(null,-1,"",new BlockFaceUV(uv(direction,b),0));
                var quad=bakery.bakeQuad(new Vector3f((float)b.minX,(float)b.minY,(float)b.minZ),
                        new Vector3f((float)b.maxX,(float)b.maxY,(float)b.maxZ),face,sprite,direction,rotation,null,true);
                if(tint>=0 && material!=null)net.neoforged.neoforge.client.model.QuadTransformers.applyingColor(
                        mc.getBlockColors().getColor(material,mc.level,data.has(POSITION)?data.get(POSITION):BlockPos.ZERO,tint)).processInPlace(quad);
                result.add(quad);
            }
        }
        return result;
    }
    static float[] uv(Direction face,net.minecraft.world.phys.AABB b) {
        return switch(face) {
            case NORTH -> new float[]{(float)(16-b.maxX),(float)(16-b.maxY),(float)(16-b.minX),(float)(16-b.minY)};
            case SOUTH -> new float[]{(float)b.minX,(float)(16-b.maxY),(float)b.maxX,(float)(16-b.minY)};
            case WEST -> new float[]{(float)b.minZ,(float)(16-b.maxY),(float)b.maxZ,(float)(16-b.minY)};
            case EAST -> new float[]{(float)(16-b.maxZ),(float)(16-b.maxY),(float)(16-b.minZ),(float)(16-b.minY)};
            case UP -> new float[]{(float)b.minX,(float)b.minZ,(float)b.maxX,(float)b.maxZ};
            case DOWN -> new float[]{(float)b.minX,(float)(16-b.maxZ),(float)b.maxX,(float)(16-b.minZ)};
        };
    }
}
