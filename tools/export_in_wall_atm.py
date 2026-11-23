"""Export the editable in-wall ATM without changing the regular NCR assets."""
import base64, copy, json, math, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/ultimatebankingsystem'

def export():
    project=json.loads((ASSETS/'blockbench/atm_machine_hyosung_5te.bbmodel').read_text())
    model={'ambientocclusion':False,'render_type':'minecraft:cutout','textures':{'hyosung':'ultimatebankingsystem:block/atm_machine_hyosung_5te','copycat':'ultimatebankingsystem:block/atm_copycat_base','particle':'#hyosung'},'elements':[]}
    controls=[];parts={};panels=[];lcd=None;reader=None;reader_housing=None;cash=None;receipt=None;full=[]
    for source in project['elements']:
        e={k:copy.deepcopy(source[k]) for k in ['name','from','to']};e['faces']={}
        angle=source.get('rotation',[0,0,0])[0]
        if angle:e['rotation']={'angle':angle,'axis':'x','origin':source['origin']}
        for side,f in source['faces'].items():
            if f.get('texture') is None:continue
            e['faces'][side]={'uv':[n/128 for n in f['uv']],'texture':'#hyosung' if f['texture']==0 else '#copycat'}
            e['faces'][side]['neoforge_data']={'ambient_occlusion':False}
            if f.get('rotation'):e['faces'][side]['rotation']=f['rotation']
        name=e['name']
        full.append(copy.deepcopy(e))
        if name.startswith('PIN key row '):
            row,col=map(int,re.search(r'row (\d) column (\d)',name).groups());index=row*4+col
            controls.append({'index':index,'keypad':True,'from':e['from'],'to':e['to'],'origin':source['origin'],'angle':angle})
            parts[f'buttons/{index}']=[e];continue
        if name.startswith('Copycat exterior '):
            cell=int(e['from'][0]//16)+2*int(e['from'][1]//16)
            panels.append({'cell':cell,'from':e['from'],'to':e['to']})
            continue
        if name=='Retractable anti-trapping cash shutter':parts['cash_shutter']=[e];cash=source;continue
        if name=='Restrained green reader status LED':
            e['faces']={side:{**f,'uv':[10.08,.08,10.92,.92],'tintindex':0} for side,f in e['faces'].items()}
            parts['card_reader_led']=[e];continue
        if name=='Motorized card insertion throat':reader=source
        if name=='Card reader recessed rectangular mounting pocket':reader_housing=source
        if name=='Receipt exit slit':receipt=source
        if name.startswith('19-inch LCD - '):lcd=source;e['faces'].pop('north',None)
        model['elements'].append(e)
    assert len(controls)==16 and len(panels)==10 and {p['cell'] for p in panels}==set(range(6)) and all(v is not None for v in [lcd,reader,reader_housing,cash,receipt])
    def point(e):
        p=[(a+b)/2 for a,b in zip(e['from'],e['to'])];p[2]=e['from'][2]
        a=math.radians(e.get('rotation',[0,0,0])[0]);o=e['origin'];y,z=p[1]-o[1],p[2]-o[2]
        return [p[0],o[1]+y*math.cos(a)-z*math.sin(a),o[2]+y*math.sin(a)+z*math.cos(a)]
    def plane(e,index):return {'index':index,'keypad':False,'from':e['from'].copy(),'to':e['to'].copy(),'origin':e['origin'],'angle':e.get('rotation',[0])[0]}
    screen=plane(lcd,27);mid=(screen['from'][1]+screen['to'][1])/2;h=(screen['to'][0]-screen['from'][0])*768/1024
    screen['from'][1]=mid-h/2;screen['to'][1]=mid+h/2;screen['from'][2]-=.008
    reader_hitbox=plane(reader_housing,24)
    reader_front=[e for e in project['elements'] if e['name'].startswith(('Card reader ', 'Card guidance ', 'Motorized card insertion '))]
    assert all(e['origin']==reader_housing['origin'] and e.get('rotation')==reader_housing.get('rotation') for e in reader_front)
    reader_hitbox['from'][2]=min(e['from'][2] for e in reader_front)
    data={'buttons':sorted(controls,key=lambda b:b['index']),'lcd':screen,'reader':reader_hitbox,'readerSlot':point(reader),'cash':point(cash),'receipt':point(receipt),'panels':panels}
    parts['card_reader_guide']=[]
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
    write(ASSETS/'in_wall_atm.json',data)
    # Terrain lighting must sample the cell containing a face, not the bottom-left terminal.
    cells=[[] for _ in range(12)]
    for e in model['elements']:
        if 'rotation' in e:
            origin=e['rotation']['origin'];angle=math.radians(e['rotation']['angle'])
            middle=[(a+b)/2 for a,b in zip(e['from'],e['to'])]
            y=origin[1]+(middle[1]-origin[1])*math.cos(angle)-(middle[2]-origin[2])*math.sin(angle)
            cell=min(2,int(y//16))*2+min(1,int(middle[0]//16))
            cells[cell].append(copy.deepcopy(e))
        else:
            for cell in range(12):
                x,y,z=cell%2*16,cell%6//2*16,cell//6*16
                piece=copy.deepcopy(e)
                piece['from']=[max(v,c) for v,c in zip(e['from'],(x,y,z))]
                piece['to']=[min(v,c+16) for v,c in zip(e['to'],(x,y,z))]
                # Leave the small fascia overhang in the front cell.
                if z==0:piece['from'][2]=e['from'][2]
                if all(piece['to'][i]>piece['from'][i] for i in range(3)):
                    for axis,low,high in [(0,'west','east'),(1,'down','up'),(2,'north','south')]:
                        if piece['from'][axis]>e['from'][axis]:piece['faces'].pop(low,None)
                        if piece['to'][axis]<e['to'][axis]:piece['faces'].pop(high,None)
                    for side,face in piece['faces'].items():
                        axis_u,axis_v,flip_u,flip_v={'north':(0,1,True,True),'south':(0,1,False,True),'west':(2,1,False,True),'east':(2,1,True,True),'up':(0,2,False,False),'down':(0,2,False,True)}[side]
                        ratios=[]
                        for axis,flip in [(axis_u,flip_u),(axis_v,flip_v)]:
                            length=e['to'][axis]-e['from'][axis]
                            low=(piece['from'][axis]-e['from'][axis])/length if length else 0
                            high=(piece['to'][axis]-e['from'][axis])/length if length else 1
                            ratios.append((1-high,1-low) if flip else (low,high))
                        u0,v0,u1,v1=e['faces'][side]['uv']
                        face['uv']=[u0+(u1-u0)*ratios[0][0],v0+(v1-v0)*ratios[1][0],u0+(u1-u0)*ratios[0][1],v0+(v1-v0)*ratios[1][1]]
                    cells[cell].append(piece)
    for cell,elements in enumerate(cells):
        offset=[cell%2*16,cell%6//2*16,cell//6*16]
        for e in elements:
            for key in ['from','to']:e[key]=[v-offset[i] for i,v in enumerate(e[key])]
            if 'rotation' in e:e['rotation']['origin']=[v-offset[i] for i,v in enumerate(e['rotation']['origin'])]
            assert all(-16<=e['from'][i]<=e['to'][i]<=32 for i in range(3)),e['name']
        write(ASSETS/f'models/block/in_wall_atm_cell_{cell}.json',{**model,'elements':elements})
    write(ROOT/'artifacts/atm-hyosung-5te/atm_machine_hyosung_5te.json',{'textures':model['textures'],'elements':full})
    for e in full:
        for key in ['from','to']:e[key]=[v*2/3 for v in e[key]]
        if 'rotation' in e:e['rotation']['origin']=[v*2/3 for v in e['rotation']['origin']]
    write(ASSETS/'models/block/in_wall_atm_body.json',{**model,'elements':full})
    for name,elements in parts.items():write(ASSETS/f'models/block/atm/in_wall/{name}.json',{**model,'elements':elements})
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        for part in range(12):variants[f'facing={facing},part={part}']={'model':f'ultimatebankingsystem:block/in_wall_atm_cell_{part}','y':rotation}
    write(ASSETS/'blockstates/in_wall_atm.json',{'variants':variants})
    write(ASSETS/'models/item/in_wall_atm.json',{
        'parent':'ultimatebankingsystem:block/in_wall_atm_body',
        'display':{'gui':{
            'rotation':[-168,27,-180],
            'translation':[1.25,-2.65,0],
            'scale':[.35,.35,.35],
        }},
    })
    for t in project['textures']:
        name='atm_copycat_base.png' if t['name']=='copycat_base.png' else t['name']
        (ASSETS/'textures/block'/name).write_bytes(base64.b64decode(t['source'].split(',')[1]))
    loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ultimatebankingsystem:in_wall_atm'}],'conditions':[{'condition':'minecraft:block_state_property','block':'ultimatebankingsystem:in_wall_atm','properties':{'part':'0'}},{'condition':'minecraft:survives_explosion'}]}]}
    write(ROOT/'src/main/resources/data/ultimatebankingsystem/loot_table/blocks/in_wall_atm.json',loot)
    lang=ASSETS/'lang/en_us.json';words=json.loads(lang.read_text());words['block.ultimatebankingsystem.in_wall_atm']='In-wall ATM';write(lang,words)
    print('Exported in-wall ATM: sixteen keys, raised touchscreen, ten full-depth tiles in six material cells')

if __name__=='__main__':export()
