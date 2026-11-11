package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.client.resources.IndexedAssetSource;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/**
 * Headless, real Minecraft default-font metrics. Uses the existing game resource JAR and indexed
 * asset cache (including the nonempty Unifont definition); never synthesizes or copies font data.
 * Like the live UI fixtures, this temporarily replaces client globals and must not run in parallel.
 */
public final class VanillaFontTestFixture implements AutoCloseable {
    public static final ResourceLocation DEFAULT_FONT = Style.DEFAULT_FONT;
    public final ReloadableResourceManager resources = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
    public final Font font;
    private final FileSystem resourceJar;
    private final Map<ResourceLocation, List<GlyphProvider>> providers = new HashMap<>();
    private final List<GlyphProvider> loadedProviders = new ArrayList<>();
    private final Map<GlyphInfo, float[]> inkBounds = new java.util.IdentityHashMap<>();
    private final Set<ResourceLocation> loading = new HashSet<>();
    private final Object oldResources, oldFont, oldOptions;
    private final Language oldLanguage;

    public VanillaFontTestFixture(String language) throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var assets = assetDirectory();
        var index = assetIndex(assets);
        Path extraJar;
        try (var paths = Files.list(Path.of("build/moddev/artifacts"))) {
            extraJar = paths.filter(path -> path.getFileName().toString()
                    .endsWith("-client-extra-aka-minecraft-resources.jar")).findFirst()
                    .orElseThrow(() -> new IOException("Missing ModDev Minecraft client resources JAR"));
        }
        resourceJar = FileSystems.newFileSystem(extraJar, Map.of());
        try {
            var packs = new ArrayList<net.minecraft.server.packs.PackResources>();
            packs.add(new VanillaPackResourcesBuilder().exposeNamespace("minecraft")
                    .pushUniversalPath(resourceJar.getPath("/"))
                    // The JAR's include/unifont.json is intentionally empty. Indexed assets override it.
                    .pushAssetPath(PackType.CLIENT_RESOURCES, IndexedAssetSource.createIndexFs(assets, index))
                    .build(location("vanilla")));
            var ae2 = Path.of(appeng.core.localization.ButtonToolTips.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            packs.add(new FilePackResources.FileResourcesSupplier(ae2).openPrimary(location("ae2")));
            packs.add(new PathPackResources(location("base"), Path.of("src/main/resources")));
            var field = ReloadableResourceManager.class.getDeclaredField("resources");
            field.setAccessible(true);
            field.set(resources, new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs));
            // Fail before changing client globals if the real font chain or cached assets cannot load.
            providers(DEFAULT_FONT);
            if (!(providerFor(DEFAULT_FONT, '熵') instanceof UnihexProvider))
                throw new IOException("Indexed Minecraft Unifont did not override the empty JAR definition");
        } catch (Exception | Error exception) {
            loadedProviders.forEach(GlyphProvider::close);
            resources.close();
            resourceJar.close();
            throw exception;
        }

        var client = Minecraft.getInstance();
        var field = Minecraft.class.getDeclaredField("resourceManager");
        field.setAccessible(true);
        oldResources = field.get(client);
        oldFont = client.font;
        oldOptions = client.options;
        oldLanguage = Language.getInstance();
        field.set(client, resources);
        var options = ResponsiveScreenTestFixture.allocate(net.minecraft.client.Options.class);
        options.languageCode = language;
        ResponsiveScreenTestFixture.set(client, "options", options);
        Language.inject(ClientLanguage.loadFrom(resources, List.of(language), false));
        var splitter = new StringSplitter((codepoint, style) -> glyph(style.getFont(), codepoint).getAdvance());
        font = new Font(id -> null, false) {
            @Override public StringSplitter getSplitter() { return splitter; }
        };
        // Native EditBox's substring helpers also read Font's private splitter field directly.
        ResponsiveScreenTestFixture.set(font, "splitter", splitter);
        ResponsiveScreenTestFixture.set(client, "font", font);
        OsqFonts.clearInkMetrics();
    }

    private static PackLocationInfo location(String id) {
        return new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
    }

    private static Path assetDirectory() {
        String gradleHome = System.getProperty("gradle.user.home", System.getenv("GRADLE_USER_HOME"));
        var home = gradleHome == null ? Path.of(System.getProperty("user.home"), ".gradle") : Path.of(gradleHome);
        return Path.of(System.getProperty("osq.test.minecraftAssets", home.resolve("caches/minecraft/assets").toString()));
    }

    private static String assetIndex(Path assets) throws IOException {
        try (var paths = Files.list(assets.resolve("indexes"))) {
            var indexes = paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted(java.util.Comparator.comparing(path -> !path.getFileName().toString().equals("asset-index.json")))
                    .toList();
            for (var path : indexes) {
                try (var reader = Files.newBufferedReader(path)) {
                    var objects = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("objects");
                    if (objects == null || !objects.has("minecraft/font/include/unifont.json")
                            || !objects.has("minecraft/font/unifont.zip")) continue;
                    for (var entry : objects.entrySet()) if (entry.getKey().startsWith("minecraft/font/")) {
                        String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
                        var object = assets.resolve("objects/" + hash.substring(0, 2) + "/" + hash);
                        if (!Files.isRegularFile(object)) throw new IOException("Missing indexed Minecraft font asset: " + object);
                    }
                    return path.getFileName().toString().replaceFirst("\\.json$", "");
                }
            }
        }
        throw new IOException("No Minecraft asset index with the real Unifont providers in " + assets);
    }

    private List<GlyphProvider> providers(ResourceLocation face) {
        var cached = providers.get(face);
        if (cached != null) return cached;
        if (!loading.add(face)) throw new AssertionError("Cyclic Minecraft font reference: " + face);
        try (var reader = resources.openAsReader(face.withPrefix("font/").withSuffix(".json"))) {
            var result = new ArrayList<GlyphProvider>();
            var definition = JsonParser.parseReader(reader).getAsJsonObject();
            for (var item : definition.getAsJsonArray("providers")) {
                // Conditional.CODEC uses GlyphProviderDefinition.MAP_CODEC and honors vanilla's
                // default options: bitmap enabled, Japanese variants and forced Unicode disabled.
                var conditional = GlyphProviderDefinition.Conditional.CODEC.parse(JsonOps.INSTANCE, item).getOrThrow();
                if (!conditional.filter().apply(Set.of())) continue;
                var unpacked = conditional.definition().unpack();
                if (unpacked.left().isPresent()) {
                    var provider = unpacked.left().orElseThrow().load(resources);
                    loadedProviders.add(provider);
                    result.add(provider);
                } else {
                    result.addAll(providers(unpacked.right().orElseThrow().id()));
                }
            }
            var loaded = List.copyOf(result);
            providers.put(face, loaded);
            return loaded;
        } catch (Exception exception) {
            throw new AssertionError("Cannot load actual Minecraft font " + face, exception);
        } finally {
            loading.remove(face);
        }
    }

    /** Null means genuinely absent; coverage tests must not mistake the missing-glyph box for coverage. */
    public GlyphInfo findGlyph(ResourceLocation face, int codepoint) {
        var provider = providerFor(face, codepoint);
        return provider == null ? null : provider.getGlyph(codepoint);
    }

    public GlyphProvider providerFor(ResourceLocation face, int codepoint) {
        for (var provider : providers(face)) if (provider.getGlyph(codepoint) != null) return provider;
        return null;
    }

    /** Bitmap cells include transparent margins; collision tests need visible pixels, not the quad. */
    public float[] inkBounds(GlyphInfo glyph) {
        return inkBounds.computeIfAbsent(glyph, value -> {
            float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            value.bake(sheet -> {
                bounds[0] = sheet.getLeft(); bounds[1] = sheet.getTop();
                bounds[2] = sheet.getRight(); bounds[3] = sheet.getBottom();
                if (value.getClass().getName().equals("net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")) {
                    try {
                        var image = (com.mojang.blaze3d.platform.NativeImage) glyphField(value, "image");
                        int x = (int) glyphField(value, "offsetX"), y = (int) glyphField(value, "offsetY");
                        int minX = sheet.getPixelWidth(), minY = sheet.getPixelHeight(), maxX = -1, maxY = -1;
                        for (int row = 0; row < sheet.getPixelHeight(); row++)
                            for (int col = 0; col < sheet.getPixelWidth(); col++) {
                                if ((image.getPixelRGBA(x + col, y + row) >>> 24) == 0) continue;
                                minX = Math.min(minX, col); minY = Math.min(minY, row);
                                maxX = Math.max(maxX, col); maxY = Math.max(maxY, row);
                            }
                        bounds[0] = sheet.getLeft() + minX / sheet.getOversample();
                        bounds[1] = sheet.getTop() + minY / sheet.getOversample();
                        bounds[2] = sheet.getLeft() + (maxX + 1) / sheet.getOversample();
                        bounds[3] = sheet.getTop() + (maxY + 1) / sheet.getOversample();
                    } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                }
                return null;
            });
            return bounds;
        });
    }

    private static Object glyphField(GlyphInfo glyph, String name) throws ReflectiveOperationException {
        var field = glyph.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(glyph);
    }

    /** Match vanilla's real missing-glyph fallback, rather than manufacturing a space advance. */
    public GlyphInfo glyph(ResourceLocation face, int codepoint) {
        var glyph = findGlyph(face, codepoint);
        return glyph == null ? SpecialGlyphs.MISSING : glyph;
    }

    @Override public void close() throws Exception {
        var client = Minecraft.getInstance();
        var field = Minecraft.class.getDeclaredField("resourceManager");
        field.setAccessible(true);
        field.set(client, oldResources);
        ResponsiveScreenTestFixture.set(client, "font", oldFont);
        ResponsiveScreenTestFixture.set(client, "options", oldOptions);
        Language.inject(oldLanguage);
        OsqFonts.clearInkMetrics();
        loadedProviders.forEach(GlyphProvider::close);
        resources.close();
        resourceJar.close();
    }
}
