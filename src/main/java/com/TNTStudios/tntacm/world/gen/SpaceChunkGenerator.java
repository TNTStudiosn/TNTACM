// src/main/java/com/TNTStudios/tntacm/world/gen/SpaceChunkGenerator.java
package com.TNTStudios.tntacm.world.gen;

// import com.TNTStudios.tntacm.block.ModBlocks; // <-- Ya no es necesario
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SpaceChunkGenerator extends ChunkGenerator {

    // El CODEC es necesario para que Minecraft sepa cómo guardar y cargar este generador de mundo.
    public static final Codec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, instance.stable(SpaceChunkGenerator::new))
    );

    // Constructor simple que solo necesita el origen de biomas.
    public SpaceChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    // Aquí es donde ocurre la magia: poblamos el chunk.
    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        // Mi objetivo es simple: llenar todo de aire.
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int y = chunk.getBottomY(); y < chunk.getTopY(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(mutablePos.set(x, y, z), Blocks.AIR.getDefaultState(), false);
                }
            }
        }

        // He quitado el código que generaba la plataforma. Ahora es un vacío total.

        return CompletableFuture.completedFuture(chunk);
    }

    // El resto de métodos los sobreescribo para que no hagan nada o devuelvan valores de "vacío".
    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        // No quiero que se construya ninguna superficie.
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        // La altura del terreno es siempre el fondo del mundo.
        return world.getBottomY();
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        // Devuelvo una columna vacía de bloques.
        return new VerticalBlockSample(world.getBottomY(), new BlockState[0]);
    }

    // Estos métodos evitan que se generen cuevas, estructuras o entidades de forma natural.
    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {}

    @Override
    public void populateEntities(ChunkRegion region) {}

    // Defino los límites del mundo.
    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -64;
    }

    // Este es el método que corregí.
    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
    }
}