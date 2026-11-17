import json, math, sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
path=Path(sys.argv[1]) if len(sys.argv)>1 else root/'src/main/resources/assets/ultimatebankingsystem/blockbench/atm_machine_hyosung_5te.bbmodel'
d=json.loads(path.read_text(encoding='utf-8'))
elements=d['elements']; by_name={e['name']:e for e in elements}
failures=[]
def check(ok,message):
    if not ok: failures.append(message)
keys=[e for e in elements if e['name'].startswith('PIN key row ')]
check(len(keys)==16,'Expected 16 independent keys')
for key in keys:
    check(key['faces']['up'].get('rotation',0)==180,key['name']+': label is upside down from the customer side')
    uv=key['faces']['up']['uv']
    geometry_ratio=(key['to'][0]-key['from'][0])/(key['to'][2]-key['from'][2])
    texture_ratio=abs((uv[2]-uv[0])/(uv[3]-uv[1]))
    check(abs(texture_ratio/geometry_ratio-1)<.001,key['name']+': texture aspect ratio stretches the lettering')
deck=next(e for e in elements if e['name']=='Full-width shallow sloping control shelf' and e.get('rotation',[0])[0]==-22.5)
bezel=by_name['Encrypted keypad charcoal recessed bezel']
def transformed(p,e,inverse=False):
    origin=e.get('origin',[0,0,0]); angle=math.radians(e.get('rotation',[0,0,0])[0])*(-1 if inverse else 1)
    y,z=p[1]-origin[1],p[2]-origin[2]
    return [p[0],origin[1]+y*math.cos(angle)-z*math.sin(angle),origin[2]+y*math.sin(angle)+z*math.cos(angle)]
def seated_on_deck(e):
    p=transformed(transformed(e['from'],e),deck,True)
    return p[1]<=deck['to'][1]+1e-5
check(seated_on_deck(bezel),'Keypad bezel is floating above the shelf')
for key in keys:check(key['from'][1]<=bezel['to'][1]+1e-6,key['name']+': floating above keypad bezel')
check(seated_on_deck(by_name['Fingerprint reader raised silver shoulder']),'Fingerprint housing is floating above shelf')
ids=[]
def walk(node):
    if isinstance(node,str):ids.append(node)
    else:
        for child in node['children']:walk(child)
check(all(isinstance(n,dict) for n in d['outliner']),'Loose top-level elements')
for node in d['outliner']:walk(node)
check(len(ids)==len(set(ids))==len(elements),'Every cube must be grouped exactly once')
check(set(ids)=={e['uuid'] for e in elements},'Outliner and cube list differ')
panels=[e for e in elements if e['name'].startswith('Copycat exterior ')]
if panels:
    check(len(panels)==10,'Expected ten pieces across six full-depth wall cells')
    check(not any(e['name'].startswith('Copycat backing ') for e in elements),'Opaque backing defeats copycat transparency')
    area=0
    for panel in panels:
        x0,y0,z0=panel['from'];x1,y1,z1=panel['to']
        check(z0==0 and z1==16,'Copycat face must be flush at full block depth')
        check(x1<=7.5 or x0>=24.5 or y1<=16.4161 or y0>=36.9161,'Copycat panel intrudes into ATM opening')
        area+=(x1-x0)*(y1-y0)
    check(abs(area-(32*48-17*20.5))<1e-5,'Copycat surround does not fill the two-by-three wall footprint')
    for i,a in enumerate(panels):
        for b in panels[i+1:]:
            check(min(a['to'][0],b['to'][0])<=max(a['from'][0],b['from'][0]) or min(a['to'][1],b['to'][1])<=max(a['from'][1],b['from'][1]),'Overlapping copycat wall faces')
for e in elements:
    check(all(math.isfinite(n) for key in ['from','to'] for n in e[key]),'Nonfinite geometry: '+e['name'])
    check(all(-16<=e['from'][i]<=e['to'][i]<=[32,48,32][i] for i in range(3)),'Invalid assembly bounds: '+e['name'])
    rotation=e.get('rotation',[0,0,0])
    check(sum(abs(n)>1e-5 for n in rotation)<=1 and all(n in [-45,-22.5,0,22.5,45] for n in rotation),'Unsupported Java rotation: '+e['name'])
export=json.loads((root/'artifacts/atm-hyosung-5te/atm_machine_hyosung_5te.json').read_text())
check(len(export['elements'])==len(elements),'Export cube count differs from source')
for e in export['elements']:
    for face in e['faces'].values():
        check(all(0<=v<=16 for v in face.get('uv',[])),'Export UV outside atlas')
if failures:
    print('FAIL: '+'\nFAIL: '.join(failures));sys.exit(1)
print('PASS: customer-facing key labels, mounted controls, grouped geometry and three-block assembly bounds; '+str(len(elements))+' cubes.')
