import json,math,unittest
from pathlib import Path

ASSETS=Path(__file__).resolve().parents[1]/'src/main/resources/assets/ultimatebankingsystem'

class InWallExportTest(unittest.TestCase):
    def test_reader_target_covers_housing_and_keeps_slot_anchor(self):
        data=json.loads((ASSETS/'in_wall_atm.json').read_text())
        project=json.loads((ASSETS/'blockbench/atm_machine_hyosung_5te.bbmodel').read_text())
        elements={e['name']:e for e in project['elements']}
        housing=elements['Card reader recessed rectangular mounting pocket']
        slot=elements['Motorized card insertion throat']
        target=data['reader']
        self.assertEqual(housing['from'][:2],target['from'][:2])
        self.assertEqual(housing['to'],target['to'])
        self.assertEqual(housing['origin'],target['origin'])
        self.assertEqual(housing['rotation'][0],target['angle'])
        self.assertLessEqual(target['from'][2],elements['Card guidance silver rail']['from'][2])
        x,y=(sum(pair)/2 for pair in zip(slot['from'][:2],slot['to'][:2]))
        _,pivot_y,pivot_z=slot['origin']
        angle=math.radians(slot['rotation'][0])
        y,z=y-pivot_y,slot['from'][2]-pivot_z
        self.assertEqual([x,pivot_y+y*math.cos(angle)-z*math.sin(angle),pivot_z+y*math.sin(angle)+z*math.cos(angle)],data['readerSlot'])

    def test_six_full_depth_material_cells_surround_an_eye_level_opening(self):
        data=json.loads((ASSETS/'in_wall_atm.json').read_text())
        panels=data['panels']
        self.assertEqual(set(range(6)),{p['cell'] for p in panels})
        self.assertEqual(48,max(p['to'][1] for p in panels))
        self.assertAlmostEqual(32*48-17*20.5,sum((p['to'][0]-p['from'][0])*(p['to'][1]-p['from'][1]) for p in panels))
        for p in panels:
            self.assertEqual((0,16),(p['from'][2],p['to'][2]))
            self.assertTrue(p['to'][0]<=7.5 or p['from'][0]>=24.5 or p['to'][1]<=16.4161 or p['from'][1]>=36.9161)
        lcd=data['lcd']
        _,pivot_y,pivot_z=lcd['origin']
        _,y,z=[(lo+hi)/2 for lo,hi in zip(lcd['from'],lcd['to'])]
        angle=math.radians(lcd['angle'])
        eye_height=(pivot_y+(y-pivot_y)*math.cos(angle)-(z-pivot_z)*math.sin(angle))/16
        self.assertAlmostEqual(1.62,eye_height,places=5)
    def test_every_static_cell_is_local_and_has_complete_model_coordinates(self):
        variants=json.loads((ASSETS/'blockstates/in_wall_atm.json').read_text())['variants']
        self.assertEqual(48,len(variants))
        for cell in range(12):
            model=json.loads((ASSETS/f'models/block/in_wall_atm_cell_{cell}.json').read_text())
            for e in model['elements']:
                self.assertTrue(all(-16<=e['from'][i]<=e['to'][i]<=32 for i in range(3)),e['name'])
                if 'rotation' not in e:
                    self.assertLessEqual(e['to'][2],16,e['name'])
            if cell>=6:
                self.assertTrue(model['elements'])
                self.assertTrue(all(e['from'][2]>=0 for e in model['elements']))

if __name__=='__main__':unittest.main()
