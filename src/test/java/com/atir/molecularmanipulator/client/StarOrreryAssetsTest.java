package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.render.StarOrreryRenderer;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class StarOrreryAssetsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/molecularmanipulator");
    private static final Path MODELS = ASSETS.resolve("models/block/star_orrery");
    private static final Path TEXTURES = ASSETS.resolve("textures/block/star_orrery");
    private static final List<String> FORMS = List.of(StarOrreryRenderer.FORMS);
    private static final List<String> PARTS = List.of(StarOrreryRenderer.PARTS);
    /** Untiered block textures owned by the real foundation blocks, never drawn by the OBJ renderer. */
    private static final String FOUNDATION = "foundation";

    /** The exact standalone model ids the renderer and its model registration ask for. */
    private static List<String> registeredRoots() {
        var roots = new ArrayList<String>();
        for (String form : FORMS) {
            for (String part : PARTS) roots.add(form + "/" + part);
            for (int ring = 1; ring <= 9; ring++) roots.add(form + "/ring_" + ring);
        }
        return roots;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Set<Path> filesUnder(Path dir) throws Exception {
        var files = new HashSet<Path>();
        try (var stream = Files.walk(dir)) {
            for (var file : stream.filter(Files::isRegularFile).toList()) {
                var relative = dir.relativize(file);
                if (relative.getName(0).toString().equals(FOUNDATION)) continue;
                files.add(normalize(file));
            }
        }
        return files;
    }

    @Test
    void registeredRootsCloseOverEveryModelAndTextureAndUseValidObjFlagsMaterialsAndAtlasBoundedUvs() throws Exception {
        var roots = registeredRoots();
        assertEquals(3 * (PARTS.size() + 9), roots.size(), "Each form registers every renderer part plus its nine belts");
        assertEquals(roots.size(), Set.copyOf(roots).size(), "No duplicate root registrations");

        var models = new HashSet<Path>();
        var textures = new HashSet<Path>();
        var textureNamesByForm = new TreeMap<String, Set<String>>();
        var animationsByForm = new TreeMap<String, Integer>();
        for (String root : roots) {
            String form = root.split("/", 2)[0];
            Path json = MODELS.resolve(root + ".json");
            assertTrue(Files.isRegularFile(json), json.toString());
            models.add(normalize(json));
            var model = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            assertEquals("neoforge:obj", model.get("loader").getAsString());
            assertFalse(model.get("automatic_culling").getAsBoolean());
            assertFalse(model.get("emissive_ambient").getAsBoolean());
            assertFalse(model.has("emissive_ambient_occlusion"));
            var obj = ASSETS.resolve(model.get("model").getAsString().split(":", 2)[1]);
            assertTrue(Files.isRegularFile(obj), obj.toString());
            models.add(normalize(obj));
            int vertices = 0, uv = 0, faces = 0, materialLines = 0;
            for (var line : Files.readAllLines(obj)) {
                if (line.startsWith("v ")) vertices++;
                if (line.startsWith("vt ")) {
                    uv++;
                    var values = line.split("\\s+");
                    for (int i = 1; i <= 2; i++) {
                        double n = Double.parseDouble(values[i]);
                        assertTrue(Double.isFinite(n) && n >= 0 && n <= 1, obj + " " + line);
                    }
                }
                if (line.startsWith("f ")) faces++;
                if (line.startsWith("mtllib ")) {
                    var mtl = obj.resolveSibling(line.substring(7));
                    assertTrue(Files.isRegularFile(mtl), mtl.toString());
                    models.add(normalize(mtl));
                    for (var material : Files.readAllLines(mtl)) if (material.startsWith("map_Kd ")) {
                        materialLines++;
                        var texture = ASSETS.resolve("textures/" + material.substring(7).split(":", 2)[1] + ".png");
                        assertTrue(Files.isRegularFile(texture), material);
                        assertEquals(form, texture.getParent().getFileName().toString(),
                                "Tier materials must stay inside their own form: " + material);
                        textures.add(normalize(texture));
                        textureNamesByForm.computeIfAbsent(form, key -> new TreeSet<>())
                                .add(texture.getFileName().toString());
                        var meta = Path.of(texture + ".mcmeta");
                        if (Files.isRegularFile(meta)) {
                            animationsByForm.merge(form, 1, Integer::sum);
                            textures.add(normalize(meta));
                            assertNotNull(JsonParser.parseString(Files.readString(meta)).getAsJsonObject().get("animation"));
                        }
                    }
                }
            }
            assertTrue(vertices > 0 && uv > 0 && faces > 0, obj.toString());
            assertTrue(materialLines > 0, "Every retained mesh must still bind at least one atlas sprite: " + obj);
        }

        // Exactly one JSON, OBJ and MTL per registered root, and nothing else.
        assertEquals(3 * roots.size(), models.size(), "Each root owns one JSON, OBJ and MTL");
        assertEquals(models, filesUnder(MODELS), "No unreachable model, material or duplicate geometry may remain");
        assertEquals(textures, filesUnder(TEXTURES), "No unreferenced texture or stray .mcmeta may remain");
        for (String form : FORMS) {
            assertTrue(textureNamesByForm.containsKey(form) && !textureNamesByForm.get(form).isEmpty(),
                    "Each form still ships its atlas sprites: " + form);
        }
        assertEquals(Set.copyOf(FORMS), textureNamesByForm.keySet(), "Only registered forms keep textures");
        // Any animated sprite still on disk must be sampled by a registered mesh, and every sampled
        // animation must be counted. The retired galaxy/nebula/star animations are gone, so the
        // counts may legitimately be zero; the three tiers must simply agree with each other.
        var animatedOnDisk = new TreeMap<String, Integer>();
        for (var form : FORMS) {
            var formTextures = TEXTURES.resolve(form);
            int count = 0;
            if (Files.isDirectory(formTextures)) {
                try (var stream = Files.list(formTextures)) {
                    count = stream.filter(file -> file.toString().endsWith(".mcmeta")).toList().size();
                }
            }
            animatedOnDisk.put(form, count);
            assertEquals(count, animationsByForm.getOrDefault(form, 0),
                    "A leftover animated sprite would be unreachable, and every sampled animation must be counted");
        }
        assertEquals(1, Set.copyOf(animatedOnDisk.values()).size(), "Every tier animates the same sprites");
    }

    @Test
    void floatingPedestalSeparatesOpaqueRingsTranslucentGlowAndLocalDial() throws Exception {
        for (String form : FORMS) {
            var folder = MODELS.resolve(form);
            for (String part : List.of("floating_pedestal", "floating_pedestal_glow",
                    "floating_pedestal_dial", "floating_pedestal_dial_glow", "floating_front_gem")) {
                assertTrue(PARTS.contains(part), "Registered floating part " + part);
                var model = JsonParser.parseString(Files.readString(folder.resolve(part+".json"))).getAsJsonObject();
                boolean translucent = part.endsWith("_glow");
                assertEquals(translucent ? "minecraft:translucent" : "minecraft:cutout", model.get("render_type").getAsString());
                assertEquals(!translucent, model.get("shade_quads").getAsBoolean(),
                        "Metal stays scene-lit and shaded; only the separate light bands emit");
                var material = Files.readString(folder.resolve(part+".mtl"));
                for (String line : material.lines().toList()) if (line.startsWith("map_Kd ")) {
                    assertTrue(line.contains("/"+form+"/floating_"), "New materials must not overwrite shared ring maps");
                }
                if (part.startsWith("floating_pedestal_dial")) {
                    for (String line : Files.readAllLines(folder.resolve(part+".obj"))) if (line.startsWith("v ")) {
                        assertTrue(Math.abs(Double.parseDouble(line.split("\\s+")[2])) < .22D,
                                "The renderer translates the dial's local origin only once");
                    }
                }
            }
            assertFalse(Files.exists(folder.resolve("floating_pedestal_petals.json")),
                    "Retired inverted crystal supports stay removed");
            assertFalse(Files.exists(TEXTURES.resolve(form+"/floating_pedestal_crystal.png")),
                    "The retired petal sprite must not return");
        }
    }

    @Test
    void foundationBlocksHaveCubeModelsRecipesMiningTagsAndSelfDrops() throws Exception {
        for (String suffix : List.of("engraved_plinth","gilded_plinth")) {
            String id = "star_orrery_"+suffix, key = "molecularmanipulator:"+id;
            var model = JsonParser.parseString(Files.readString(ASSETS.resolve("models/block/"+id+".json"))).getAsJsonObject();
            assertEquals("minecraft:block/cube_bottom_top",model.get("parent").getAsString());
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String path = texture.getValue().getAsString().split(":",2)[1];
                assertTrue(path.startsWith("block/star_orrery/foundation/"), "Unformed materials must not reuse the ornate OBJ textures");
                var file = ASSETS.resolve("textures/"+path+".png");
                assertTrue(Files.isRegularFile(file));
                var image = javax.imageio.ImageIO.read(file.toFile());
                assertEquals(64,image.getWidth());assertEquals(64,image.getHeight());
                for (int y=0;y<64;y++) for (int x=0;x<64;x++) assertEquals(255,image.getRGB(x,y)>>>24);
            }
            var blockstate = JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/"+id+".json"))).getAsJsonObject();
            for (boolean formed : List.of(false,true)) {
                assertEquals("molecularmanipulator:block/"+id,blockstate.getAsJsonObject("variants").getAsJsonObject("formed="+formed).get("model").getAsString());
            }
            var recipe = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/molecularmanipulator/recipe/"+id+".json"))).getAsJsonObject();
            assertEquals(key,recipe.getAsJsonObject("result").get("id").getAsString());
            var loot = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/molecularmanipulator/loot_table/blocks/"+id+".json"))).getAsJsonObject();
            assertEquals(key,loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
            for (String tag : List.of("mineable/pickaxe","needs_diamond_tool")) {
                var values = JsonParser.parseString(Files.readString(RESOURCES.resolve("data/minecraft/tags/block/"+tag+".json"))).getAsJsonObject().getAsJsonArray("values");
                assertTrue(values.asList().stream().anyMatch(v -> v.getAsString().equals(key)));
            }
        }
    }

    @Test
    void templatesAreNorthBasedOrreriesWithCentralShellAndOnlyTouchFunctionalSlots() throws Exception {
        String coreName = "molecularmanipulator:star_computation_engine_core";
        String casingName = "molecularmanipulator:star_computation_engine_casing";
        String portName = "molecularmanipulator:star_computation_observation_port";
        var corePos = new BlockPos(37,38,37);
        var portPositions = Set.of(corePos.north(),corePos.south(),corePos.east(),corePos.west());
        for (int lit = 0; lit <= 9; lit++) {
            var path = RESOURCES.resolve("data/molecularmanipulator/structure/star_orrery_l"+lit+".nbt");
            try (var stream = Files.newInputStream(path)) {
                var nbt = NbtIo.readCompressed(stream,NbtAccounter.create(16*1024*1024));
                assertEquals(3955,nbt.getInt("DataVersion"));
                var size = nbt.getList("size",Tag.TAG_INT);
                assertEquals(75,size.getInt(0));assertEquals(76,size.getInt(1));assertEquals(75,size.getInt(2));
                assertTrue(nbt.getList("entities",Tag.TAG_COMPOUND).isEmpty());
                var palette = nbt.getList("palette",Tag.TAG_COMPOUND);
                var blocks = nbt.getList("blocks",Tag.TAG_COMPOUND);
                assertEquals(531 + StarOrreryFoundation.restorationOffsets().size(),blocks.size());
                var names = new HashMap<BlockPos,String>();
                int anchors=0, cores=0, casings=0, ports=0, plinths=0, trims=0;
                for (int i = 0; i < blocks.size(); i++) {
                    var block = blocks.getCompound(i);
                    var xyz = block.getList("pos",Tag.TAG_INT);
                    var pos = new BlockPos(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                    var state = palette.getCompound(block.getInt("state"));
                    var name = state.getString("Name");
                    assertNull(names.put(pos,name));
                    if (name.endsWith("star_position_anchor")) anchors++;
                    if (name.equals(coreName)) cores++;
                    if (name.equals(casingName)) casings++;
                    if (name.equals(portName)) ports++;
                    if (name.endsWith(":star_orrery_engraved_plinth")) plinths++;
                    if (name.endsWith(":star_orrery_gilded_plinth")) trims++;
                    if (name.contains(":star_orrery_")) {
                        assertEquals("false",state.getCompound("Properties").getString("formed"),"Templates start with physical building blocks");
                    }
                    if (name.equals(coreName) || name.equals(casingName) || name.equals(portName)) {
                        var props = state.getCompound("Properties");
                        assertEquals("north",props.getString("facing"));
                        assertEquals("false",props.getString("formed"));
                        if (name.equals(coreName)) {
                            assertEquals(corePos,pos);
                            assertEquals("true",props.getString("star_orrery"));
                        } else {
                            assertEquals("false",props.getString("invisible"),"Loose shell blocks stay visible");
                        }
                    }
                }
                assertEquals(1,cores);
                assertEquals(22,casings);
                assertEquals(4,ports);
                for (var pos : BlockPos.betweenClosed(corePos.offset(-1,-1,-1), corePos.offset(1,1,1))) {
                    String expected = pos.equals(corePos) ? coreName : portPositions.contains(pos) ? portName : casingName;
                    assertEquals(expected,names.get(pos),"L"+lit+" central "+pos);
                }
                assertEquals(520,plinths);
                assertEquals(408,trims);
                for (var offset : StarOrreryFoundation.restorationOffsets()) {
                    if (StarOrreryFoundation.materialAt(offset) == null) {
                        assertEquals("minecraft:air",names.get(new BlockPos(37,38,37).offset(offset)),
                                "Template replacement must open the retired solid terraces");
                    }
                }
                assertFalse(names.containsKey(new BlockPos(68,0,37)), "Do not clear unrelated positions beyond old base slots");
                for (var member : StarOrreryFoundation.members()) {
                    var pos = StarOrreryFoundation.position(new BlockPos(37,38,37),Direction.NORTH,member);
                    String suffix = member.material() == StarOrreryFoundation.Material.PLINTH ? "engraved_plinth" : "gilded_plinth";
                    assertEquals("molecularmanipulator:star_orrery_"+suffix,names.get(pos),"L"+lit+" foundation "+pos);
                }
                int expected = 0;
                for (int ring = 0; ring < 9; ring++) {
                    for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                        var pos = StarOrreryLayout.slot(new BlockPos(37,38,37),Direction.NORTH,ring,slot);
                        assertEquals(ring < lit ? "molecularmanipulator:"+FORMS.get(ring/3)+"_star_position_anchor" : "minecraft:air",
                                names.get(pos),"L"+lit+" ring"+ring);
                    }
                    if (ring < lit) expected += StarOrreryLayout.slotCount(ring);
                }
                assertEquals(expected,anchors);
            }
        }
    }
}
