"""Иконка Fingramm: один набор фигур -> SVG (для просмотра) и VectorDrawable XML (для Android).

Запуск:  python3 tools/icon/make_icon.py <каталог-вывода>
Результат: icon.svg (полная иконка на фоне), adaptive.svg (под круглой маской, пунктир — безопасная зона 66 dp),
mono.svg (тематическая иконка), а также ic_launcher_foreground.xml и ic_launcher_monochrome.xml, которые копируются
в app/src/main/res/drawable/. Геометрия и цвета — в params.json (координаты шаблона 1254x1254, снятые с картинки
заказчика). Фон адаптивной иконки (#0F1C35) задан цветом ic_launcher_background в res/values.
"""
import math, sys, json, os

OUT = sys.argv[1]
P = json.load(open(os.path.join(os.path.dirname(__file__), 'params.json')))

def f(v):
    s = ('%.1f' % v).rstrip('0').rstrip('.')
    return s if s != '-0' else '0'

def rrect(x, y, w, h, tl=0, tr=0, br=0, bl=0):
    d = [f'M{f(x+tl)},{f(y)}', f'H{f(x+w-tr)}']
    if tr: d.append(f'A{f(tr)},{f(tr)} 0 0 1 {f(x+w)},{f(y+tr)}')
    d.append(f'V{f(y+h-br)}')
    if br: d.append(f'A{f(br)},{f(br)} 0 0 1 {f(x+w-br)},{f(y+h)}')
    d.append(f'H{f(x+bl)}')
    if bl: d.append(f'A{f(bl)},{f(bl)} 0 0 1 {f(x)},{f(y+h-bl)}')
    d.append(f'V{f(y+tl)}')
    if tl: d.append(f'A{f(tl)},{f(tl)} 0 0 1 {f(x+tl)},{f(y)}')
    d.append('Z')
    return ''.join(d)

def circle(cx, cy, r):
    return f'M{f(cx-r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx+r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx-r)},{f(cy)}Z'

class Shape:
    def __init__(self, d, fill=None, stroke=None, sw=0, alpha=1.0, cap='butt', grad=None, salpha=1.0):
        self.d, self.fill, self.stroke, self.sw, self.alpha, self.cap, self.grad, self.salpha = d, fill, stroke, sw, alpha, cap, grad, salpha

class Group:
    def __init__(self, items, rot=0, px=0, py=0):
        self.items, self.rot, self.px, self.py = items, rot, px, py

class ClipGroup:
    def __init__(self, items, d):
        self.items, self.d = items, d

# grad = (x1,y1,x2,y2,[(offset,color),...])
def lin(x1, y1, x2, y2, *stops):
    return (x1, y1, x2, y2, list(stops))

def dashed_rrect(x, y, w, h, r, dash, gap, step=6.0):
    """Контур скруглённого прямоугольника (по часовой стрелке) -> список ломаных-штрихов."""
    pts = []
    def line(a, b):
        n = max(1, int(math.dist(a, b) / 2))
        return [(a[0] + (b[0]-a[0])*i/n, a[1] + (b[1]-a[1])*i/n) for i in range(n)]
    def arc(cx, cy, a0, a1):
        n = max(2, int(abs(a1-a0) * r / 2))
        return [(cx + r*math.cos(math.radians(a0 + (a1-a0)*i/n)), cy + r*math.sin(math.radians(a0 + (a1-a0)*i/n))) for i in range(n)]
    pts += line((x+r, y), (x+w-r, y)); pts += arc(x+w-r, y+r, -90, 0)
    pts += line((x+w, y+r), (x+w, y+h-r)); pts += arc(x+w-r, y+h-r, 0, 90)
    pts += line((x+w-r, y+h), (x+r, y+h)); pts += arc(x+r, y+h-r, 90, 180)
    pts += line((x, y+h-r), (x, y+r)); pts += arc(x+r, y+r, 180, 270)
    # накопленная длина (шаг ~2px)
    acc = [0.0]
    for a, b in zip(pts, pts[1:] + pts[:1]):
        acc.append(acc[-1] + math.dist(a, b))
    total = acc[-1]
    period = dash + gap
    n_d = round(total / period)
    period = total / n_d
    d_len = period * dash / (dash + gap)
    out = []
    for k in range(n_d):
        s0, s1 = k*period + P['stitch_phase'], k*period + P['stitch_phase'] + d_len
        seg = []
        for i, p in enumerate(pts):
            s = acc[i]
            for shift in (0, total):
                if s0 <= s + shift <= s1:
                    seg.append(p)
                    break
        if len(seg) >= 2:
            # прореживаем до ~6px
            keep = [seg[0]] + [q for j, q in enumerate(seg[1:-1]) if j % 3 == 2] + [seg[-1]]
            out.append(keep)
    return out

def poly_d(segs):
    return ''.join('M' + 'L'.join(f'{f(px)},{f(py)}' for px, py in s) for s in segs)

# ---------- сцена ----------
G = P['geom']; C = P['col']

def card(name):
    c = G[name]
    x, y, w, h, r = c['x'], c['y'], c['w'], c['h'], c['r']
    return Group([Shape(rrect(x, y, w, h, r, r, r, r), grad=lin(x, y, x, y+c['gl'], (0, C[name][0]), (1, C[name][1])))],
                 rot=c['rot'], px=c['px'], py=c['py'])

green = card('green')
g = G['green']
inner = Group([Shape(rrect(g['x']+g['ix'], g['y']+g['iy'], g['w']-g['ix']*2, g['ih'], 22, 22, 22, 22), fill=C['green_inner'], alpha=0.55)],
              rot=g['rot'], px=g['px'], py=g['py'])
blue = card('blue')
purple = card('purple')

L = G['lip']
lip = [
    Shape(f"M{f(L['x'])},{f(L['y1'])}V{f(L['y0']+L['r'])}A{f(L['r'])},{f(L['r'])} 0 0 1 {f(L['x']+L['r'])},{f(L['y0'])}H{f(L['x2'])}V{f(L['y1'])}Z",
          grad=lin(L['x'], L['y0'], L['x'], L['y1'], (0, C['lip_rim'][0]), (1, C['lip_rim'][1]))),
    Shape(f"M{f(L['x']+L['t'])},{f(L['y1'])}V{f(L['y0']+L['r'])}A{f(L['r']-L['t'])},{f(L['r']-L['t'])} 0 0 1 {f(L['x']+L['r'])},{f(L['y0']+L['t'])}H{f(L['x2'])}V{f(L['y1'])}Z",
          fill=C['lip_in']),
]

K = G['coin']
coin = [
    Shape(circle(K['cx']+K['sh_dx'], K['cy']+K['sh_dy'], K['r']+2), fill=C['shadow'], alpha=0.30),
    Shape(circle(K['cx'], K['cy'], K['r']), grad=lin(K['cx']-K['r'], K['cy']-K['r'], K['cx']+K['r'], K['cy']+K['r'], (0, C['coin'][0]), (1, C['coin'][1]))),
    Shape(circle(K['cx'], K['cy'], K['ring_r']), stroke=C['coin_ring'], sw=K['ring_w'], alpha=0, salpha=0.8),
]
R = G['ruble']
ruble_col = C['ruble']
ruble = [
    Shape(f"M{f(R['sx'])},{f(R['top'])}V{f(R['bot'])}", stroke=ruble_col, sw=R['sw'], salpha=0.9),
    Shape(f"M{f(R['sx']-R['sw']/2)},{f(R['top']+R['bw']/2)}H{f(R['bx'])}A{f(R['br'])},{f(R['br'])} 0 0 1 {f(R['bx'])},{f(R['top']+R['bw']/2+2*R['br'])}H{f(R['sx'])}",
          stroke=ruble_col, sw=R['bw'], salpha=0.9),
    Shape(f"M{f(R['x0'])},{f(R['bar1'])}H{f(R['x1'])}", stroke=ruble_col, sw=R['barw'], salpha=0.9),
    Shape(f"M{f(R['x0'])},{f(R['bar2'])}H{f(R['x1'])}", stroke=ruble_col, sw=R['barw'], salpha=0.9),
]

B = G['body']
body = [
    Shape(rrect(B['x']+6, B['y']+B['sh_dy'], B['w']-8, B['h'], 0, B['rtr'], B['rb'], B['rb']), fill=C['shadow'], alpha=0.30),
    Shape(rrect(B['x'], B['y'], B['w'], B['h'], 0, B['rtr'], B['rb'], B['rb']), grad=lin(0, B['y'], 0, B['y']+B['h'], (0, C['rim'][0]), (1, C['rim'][1]))),
    Shape(rrect(B['x']+B['ins_l'], B['y']+B['ins_t'], B['w']-B['ins_l']-B['ins_r'], B['h']-B['ins_t']-B['ins_b'], 0, B['rtr']-4, B['rb']-3, B['rb']-3),
          grad=lin(0, B['y'], 0, B['y']+B['h'], (0, C['body'][0]), (1, C['body'][1]))),
]
S = G['stitch']
stitches = [Shape(poly_d(dashed_rrect(S['x'], S['y'], S['w'], S['h'], S['r'], S['dash'], S['gap'])), stroke=C['stitch'], sw=S['sw'], cap='round', alpha=0, salpha=S['alpha'])]

T = G['clasp']
def clasp_shape(dy=0, grad=None, fill=None, alpha=1.0, grow=0):
    x, y, w, h = T['x']-grow, T['y']+dy-grow, T['w']+2*grow, T['h']+2*grow
    return Shape(rrect(x, y, w, h, h/2, T['rt'], T['rb'], h/2), grad=grad, fill=fill, alpha=alpha)
shadow = [clasp_shape(dy=d, fill=C['shadow'], alpha=a, grow=gr) for d, a, gr in T['shadow']]
clasp = [clasp_shape(grad=lin(0, T['y'], 0, T['y']+T['h'], (0, C['clasp'][0]), (1, C['clasp'][1])))]
N = G['button']
button = [
    Shape(circle(N['cx']-N['so'], N['cy']+N['so']*1.3, N['r']+3), fill=C['shadow'], alpha=0.45),
    Shape(circle(N['cx'], N['cy'], N['r']), grad=lin(N['cx'], N['cy']-N['r'], N['cx'], N['cy']+N['r'], (0, C['btn'][0]), (1, C['btn'][1]))),
    Shape(circle(N['cx'], N['cy'], N['r']-4), fill='#FFFFFF', alpha=0.0),
]

order_cards = [green, inner, blue, purple]
layers = order_cards + lip + coin + ruble + body + stitches + shadow + clasp + button

# ---------- SVG ----------
def svg_grad_defs(items):
    defs, n = [], [0]
    def reg(grad):
        n[0] += 1; gid = f'g{n[0]}'
        x1, y1, x2, y2, stops = grad
        s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">{s}</linearGradient>')
        return gid
    return defs, reg

def svg_node(it, reg):
    if isinstance(it, ClipGroup):
        return f'<clipPath id="mc"><path d="{it.d}"/></clipPath><g clip-path="url(#mc)">' + ''.join(svg_node(i, reg) for i in it.items) + '</g>'
    if isinstance(it, Group):
        inner = ''.join(svg_node(i, reg) for i in it.items)
        return f'<g transform="rotate({f(it.rot)} {f(it.px)} {f(it.py)})">{inner}</g>'
    a = [f'd="{it.d}"']
    if it.grad: a.append(f'fill="url(#{reg(it.grad)})"')
    elif it.fill: a.append(f'fill="{it.fill}"'); a.append(f'fill-opacity="{it.alpha}"')
    else: a.append('fill="none"')
    if it.grad and it.alpha != 1: a.append(f'fill-opacity="{it.alpha}"')
    if it.stroke:
        a += [f'stroke="{it.stroke}"', f'stroke-width="{f(it.sw)}"', f'stroke-linecap="{it.cap}"', 'stroke-linejoin="round"', f'stroke-opacity="{it.salpha}"']
    return f'<path {" ".join(a)}/>'

defs, reg = svg_grad_defs(layers)
body_svg = ''.join(svg_node(l, reg) for l in layers)
bg = P['col']['bg']
with open(os.path.join(OUT, 'icon.svg'), 'w', encoding='utf-8') as fh:
    fh.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="1254" height="1254" viewBox="0 0 1254 1254"><defs>{"".join(defs)}</defs>'
             f'<rect width="1254" height="1254" fill="{bg}"/>{body_svg}</svg>')

# ---------- Android VectorDrawable ----------
def a_node(it, ind, mono=False):
    pad = '    ' * ind
    if isinstance(it, ClipGroup):
        s = f'{pad}<group>\n{pad}    <clip-path android:pathData="{it.d}" />\n'
        s += ''.join(a_node(i, ind+1, mono) for i in it.items)
        return s + f'{pad}</group>\n'
    if isinstance(it, Group):
        s = f'{pad}<group android:pivotX="{f(it.px)}" android:pivotY="{f(it.py)}" android:rotation="{f(it.rot)}">\n'
        s += ''.join(a_node(i, ind+1, mono) for i in it.items)
        return s + f'{pad}</group>\n'
    attrs = [f'android:pathData="{it.d}"']
    inner = ''
    def grad_xml(grad, attr):
        x1, y1, x2, y2, stops = grad
        items = ''.join(f'{pad}        <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
        return (f'{pad}    <aapt:attr name="android:{attr}">\n{pad}        <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
                f'android:endX="{f(x2)}" android:endY="{f(y2)}">\n{items}{pad}        </gradient>\n{pad}    </aapt:attr>\n')
    if it.grad:
        inner += grad_xml(it.grad, 'fillColor')
        if it.alpha != 1: attrs.append(f'android:fillAlpha="{it.alpha}"')
    elif it.fill and it.alpha > 0:
        attrs.append(f'android:fillColor="{it.fill}"')
        if it.alpha != 1: attrs.append(f'android:fillAlpha="{it.alpha}"')
    if it.stroke:
        attrs += [f'android:strokeColor="{it.stroke}"', f'android:strokeWidth="{f(it.sw)}"', f'android:strokeLineCap="{it.cap}"', 'android:strokeLineJoin="round"']
        if it.salpha != 1: attrs.append(f'android:strokeAlpha="{it.salpha}"')
    if inner:
        return f'{pad}<path\n' + ''.join(f'{pad}    {a}\n' for a in attrs).rstrip('\n') + f'>\n{inner}{pad}</path>\n'
    return f'{pad}<path\n' + ''.join(f'{pad}    {a}\n' for a in attrs).rstrip('\n') + ' />\n'

def keep(it):
    if isinstance(it, (Group, ClipGroup)): return True
    return not (it.grad is None and it.fill and it.alpha == 0 and not it.stroke)

s = P['android']
scale, tx, ty = s['scale'], s['tx'], s['ty']
inner_xml = ''.join(a_node(l, 2) for l in layers if keep(l))
fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Кошелёк с картами и монетой. Нарисовано по шаблону иконки; значимая часть укладывается в центральные 66 dp
     адаптивной иконки (масштаб группы подобран под это). Координаты — в системе шаблона 1254×1254. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1254"
    android:viewportHeight="1254">
    <group
        android:pivotX="627"
        android:pivotY="627"
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{tx}"
        android:translateY="{ty}">
{inner_xml}    </group>
</vector>
'''
with open(os.path.join(OUT, 'ic_launcher_foreground.xml'), 'w', encoding='utf-8') as fh:
    fh.write(fg)
print('ok', len(fg))

# ---------- монохромный слой (Android 13+, тематические иконки): тона задаёт прозрачность ----------
BLACK = '#000000'
def mono_card(name, alpha):
    c = G[name]
    return Group([Shape(rrect(c['x'], c['y'], c['w'], c['h'], c['r'], c['r'], c['r'], c['r']), fill=BLACK, alpha=alpha)],
                 rot=c['rot'], px=c['px'], py=c['py'])

above_body = f"M0,0H1254V{f(B['y'])}H0Z"
mono_layers = [
    ClipGroup([
        mono_card('green', 0.40), mono_card('blue', 0.55), mono_card('purple', 0.70),
        Shape(circle(K['cx'], K['cy'], K['r']), fill=BLACK, alpha=0.55),
        Shape(circle(K['cx'], K['cy'], K['ring_r']), stroke=BLACK, sw=K['ring_w'], salpha=0.9),
    ] + [Shape(r_.d, stroke=BLACK, sw=r_.sw, salpha=1.0) for r_ in ruble], above_body),
    Shape(lip[0].d, fill=BLACK, alpha=0.6),
    Shape(body[1].d, fill=BLACK, alpha=0.6),
    Shape(stitches[0].d, stroke=BLACK, sw=stitches[0].sw, cap='round', salpha=1.0),
    Shape(clasp[0].d, fill=BLACK, alpha=1.0),
]
mono_xml = ''.join(a_node(l, 2) for l in mono_layers if keep(l))
mono = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Монохромный вариант для тематических иконок Android 13+: те же фигуры, различие тонов — прозрачностью. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1254"
    android:viewportHeight="1254">
    <group
        android:pivotX="627"
        android:pivotY="627"
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{tx}"
        android:translateY="{ty}">
{mono_xml}    </group>
</vector>
'''
with open(os.path.join(OUT, 'ic_launcher_monochrome.xml'), 'w', encoding='utf-8') as fh:
    fh.write(mono)
print('mono ok', len(mono))

# ---------- проверочные SVG: адаптивная иконка под круглой маской и монохром ----------
defs2, reg2 = svg_grad_defs(layers)
fg_svg = ''.join(svg_node(l, reg2) for l in layers)
mono_defs, mono_reg = svg_grad_defs(mono_layers)
mono_svg = ''.join(svg_node(l, mono_reg) for l in mono_layers)
tr = f'translate({627+tx-627*scale} {627+ty-627*scale}) scale({scale})'
with open(os.path.join(OUT, 'adaptive.svg'), 'w', encoding='utf-8') as fh:
    fh.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="1254" height="1254" viewBox="0 0 1254 1254"><defs>{"".join(defs2)}'
             f'<clipPath id="m"><circle cx="627" cy="627" r="{72/108*1254/2}"/></clipPath></defs>'
             f'<rect width="1254" height="1254" fill="#444"/><g clip-path="url(#m)"><rect width="1254" height="1254" fill="{bg}"/>'
             f'<g transform="{tr}">{fg_svg}</g></g>'
             f'<circle cx="627" cy="627" r="{66/108*1254/2}" fill="none" stroke="#ff0" stroke-width="3" stroke-dasharray="12 8"/></svg>')
with open(os.path.join(OUT, 'mono.svg'), 'w', encoding='utf-8') as fh:
    fh.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="1254" height="1254" viewBox="0 0 1254 1254"><defs>{"".join(mono_defs)}</defs>'
             f'<rect width="1254" height="1254" fill="#E8DEF8"/><g transform="{tr}" style="color:#21005D">{mono_svg}</g></svg>')
