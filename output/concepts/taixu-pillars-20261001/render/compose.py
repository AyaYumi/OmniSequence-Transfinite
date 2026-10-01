from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parent.parent
BG=(12,25,41)
INK=(230,234,229)
MUTED=(146,171,184)
GOLD=(219,184,117)
CYAN=(145,223,224)
FONT='C:/Windows/Fonts/NotoSansSC-VF.ttf'
BOLD='C:/Windows/Fonts/NotoSansCJKsc-Bold.otf'
SERIF='C:/Windows/Fonts/NotoSerifSC-VF.ttf'

def font(s,b=False,serif=False):
    return ImageFont.truetype(SERIF if serif else BOLD if b else FONT,s)

def text(im,xy,t,size=30,fill=INK,b=False,serif=False):
    ImageDraw.Draw(im).text(xy,t,font=font(size,b,serif),fill=fill)

def finalize(code,kind):
    base=Image.open(ROOT/f'{code}-{kind}-base.png').convert('RGB')
    glow=Image.open(ROOT/f'{code}-{kind}-glow.png').convert('RGB')
    a=np.asarray(base,dtype=np.float32)
    for radius,gain in [(4,.16),(14,.21),(40,.16)]:
        a+=np.asarray(glow.filter(ImageFilter.GaussianBlur(radius)),dtype=np.float32)*gain
    im=Image.fromarray(np.clip(a,0,255).astype('uint8'))
    im.save(ROOT/f'{code}-{kind}.png')
    return im

def fit(im,w,h):
    im=im.copy();im.thumbnail((w,h),Image.Resampling.LANCZOS);return im

def crop_model(im,pad=60):
    # Dark navy is the scene backdrop, excluded from the model bounds.
    a=np.asarray(im)
    mask=(a.max(axis=2)>85)
    ys,xs=np.where(mask)
    if len(xs):return im.crop((max(0,int(xs.min())-pad),max(0,int(ys.min())-pad),min(im.width,int(xs.max())+pad),min(im.height,int(ys.max())+pad)))
    return im

spec={
 'A':('星阙装甲','分段玉甲 · 内凹光槽 · 外翻护翼','柱身更有建筑体量，最接近现有体系。',
      ['外柱：厚柱脚 / 分段玉甲 / 环形冠顶','中央：四向骨架 / 扩大冠冕 / 环抱晶核']),
 'B':('悬晶天仪','悬浮分节 · 裸露晶体 · 多轴约束环','虚实对比最强，中央主柱最容易成为焦点。',
      ['外柱：离散晶舱 / 悬浮节段 / 四向护翼','中央：大型晶核 / 断开外壳 / 三轴星轨']),
 'C':('重檐天宫','层叠冠顶 · 金纹承托 · 琉璃灯芯','东方建筑感更强，整体更庄重。',
      ['外柱：双层檐冠 / 内嵌琉璃 / 悬垂晶饰','中央：三级冠檐 / 立柱承托 / 通透光井']),
 'D':('星阙 × 悬晶','推荐组合：A 的外围八柱 + B 的中央主柱','外圈负责秩序与体量，中心负责悬浮感与视觉焦点。',
      ['外围八柱：保留实体玉甲，补足柱脚与冠顶','中央主柱：放大悬浮晶核，形成清楚的虚实对比'])
}

renders={}
for code in 'ABCD':
    renders[code]={kind:finalize(code,kind) for kind in ['overview','detail']}
    title,sub,reason,notes=spec[code]
    sheet=Image.new('RGB',(2600,1820),BG);d=ImageDraw.Draw(sheet)
    text(sheet,(80,55),f'{code}  /  太虚天仪 · {title}',54,GOLD,b=True)
    text(sheet,(83,136),sub,30,MUTED)
    d.line((80,198,2520,198),fill=(49,70,86),width=2)
    full=fit(crop_model(renders[code]['overview'],70),1430,1430)
    sheet.paste(full,(60+(1440-full.width)//2,230+(1430-full.height)//2))
    detail=fit(crop_model(renders[code]['detail'],45),1000,1270)
    sheet.paste(detail,(1530+(990-detail.width)//2,300+(1260-detail.height)//2))
    text(sheet,(1600,224),'单柱细节    外围柱 / 中央主柱',30,CYAN)
    d.line((1498,250,1498,1570),fill=(45,63,78),width=2)
    text(sheet,(1570,1584),notes[0],29,INK)
    text(sheet,(1570,1640),notes[1],29,INK)
    d.line((80,1710,2520,1710),fill=(49,70,86),width=2)
    text(sheet,(82,1740),reason,29,GOLD)
    text(sheet,(1830,1750),'静态造型概念渲染 · 非游戏实拍',23,MUTED)
    sheet.save(ROOT/f'{code}-concept-sheet.png')

board=Image.new('RGB',(3000,1730),BG);d=ImageDraw.Draw(board)
text(board,(78,42),'太虚天仪 / 八柱与中央天柱',56,INK,b=True)
text(board,(82,126),'三种造型方向   ·   沿用白玉 / 淡金 / 冰蓝   ·   保留三环与八柱布局',29,MUTED)
for i,code in enumerate('ABC'):
    x=60+i*980;title,sub,reason,notes=spec[code]
    text(board,(x+24,216),f'{code}   {title}',43,GOLD,b=True)
    text(board,(x+26,283),sub,25,MUTED)
    im=fit(crop_model(renders[code]['overview'],55),935,1160)
    board.paste(im,(x+(935-im.width)//2,350+(1160-im.height)//2))
    d.line((x+24,1535,x+912,1535),fill=(52,71,87),width=2)
    text(board,(x+24,1556),reason,27,INK)
    if i<2:d.line((x+958,214,x+958,1620),fill=(42,62,78),width=2)
text(board,(84,1660),'静态三维概念预览。用于选择造型方向；材质、尺寸与方块化细节待选定后细化。',25,MUTED)
board.save(ROOT/'00-three-directions.png')

# A compact detail-only comparison makes the actual pillar redesign inspectable.
board2=Image.new('RGB',(3000,1700),BG);d=ImageDraw.Draw(board2)
text(board2,(78,48),'造型对照 / 外围柱 × 中央主柱',55,INK,b=True)
text(board2,(82,129),'去掉环带遮挡，比较柱身分节、顶冠与核心结构。',30,MUTED)
for i,code in enumerate('ABC'):
    x=50+i*980;title,sub,reason,notes=spec[code]
    text(board2,(x+30,218),f'{code}   {title}',41,GOLD,b=True)
    im=fit(crop_model(renders[code]['detail'],40),910,1190)
    board2.paste(im,(x+(930-im.width)//2,322+(1190-im.height)//2))
    text(board2,(x+27,1550),notes[0],25,MUTED)
    text(board2,(x+27,1600),notes[1],25,MUTED)
    if i<2:d.line((x+951,210,x+951,1660),fill=(42,62,78),width=2)
board2.save(ROOT/'01-pillar-details.png')
print('Saved 4 concept sheets, 2 comparison boards, and 8 clean renders to',ROOT)
