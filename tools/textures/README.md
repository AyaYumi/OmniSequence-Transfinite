# Texture maintenance / 贴图维护

Current source: 2.0.7-forge. Runtime textures and metadata live in
`src/main/resources/assets/molecularmanipulator/textures/`. Editable source images
under `static/` are required to regenerate animations; they are not unused assets.

| Generator | Maintained input and output |
| --- | --- |
| `generate_animation.py` | 20 original block PNGs in `static/`; 24-frame strips and interpolation metadata |
| `animate_hole_items.py` | 32×32 `static/items/black_hole.png` / `white_hole.png`; 32-frame orbit/halo item animations |
| `rebuild_singularity.py` | Current 16×16 Hub block art; rebuild emissive overlays without repainting base texels |
| `generate_sequence_matter_fluid.py` | Tiled source/flowing Sequence Matter animation; 32 frames |

```powershell
python tools/textures/generate_animation.py
python tools/textures/animate_hole_items.py
python tools/textures/rebuild_singularity.py
python tools/textures/generate_sequence_matter_fluid.py
```

Use Python, Pillow and NumPy as required by the selected script. Preview PNG/GIF
outputs stay under ignored `build/`. Do not use generated animation strips as
source originals.

The Nexus textures, formed-panel artwork, current native models/shaders and
effect sprites used by the renderer are production assets. Item/fluid atlas animations remain
independent of the large-world dynamic-effect setting.

## 中文

动画原图是维护源，不按游戏未直接引用而删除。运行脚本会更新正式资源；修改前
检查所选输入。微型黑洞/白洞使用保存的原画重做外环流动，天枢重建仅更新自发光
层，流体生成保证平铺与时间循环。预览在 build 中，正式 JAR 只取资源目录。
