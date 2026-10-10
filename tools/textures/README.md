# Texture maintenance / 贴图维护

Current source: 2.0.8-forge. Runtime textures and metadata live in
`src/main/resources/assets/molecularmanipulator/textures/`. Editable source images
under `static/` are required to regenerate animations; they are not unused assets.

| Generator | Maintained input and output |
| --- | --- |
| `generate_ghost_matter.py` | `static/items/ghost_matter.png`; current ghost-matter item, mesh and fog resources |
| `generate_animation.py` | Block source images in `static/`; runtime textures and metadata |
| `animate_hole_items.py` | `static/items/black_hole.png` / `white_hole.png`; runtime item textures |
| `rebuild_singularity.py` | Hub source images; rebuild emissive overlays |
| `generate_sequence_matter_fluid.py` | Sequence Matter source and flowing fluid textures |

```powershell
python tools/textures/generate_ghost_matter.py
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
