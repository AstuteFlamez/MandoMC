import base64,copy,importlib.util,struct,tempfile,unittest
from pathlib import Path
try:
 import nbtlib
 from nbtlib import tag as T
except ImportError:
 nbtlib=None

@unittest.skipIf(nbtlib is None,'NBT migration tests require nbtlib')
class MetalPlayerdataTest(unittest.TestCase):
 def setUp(self):
  spec=importlib.util.spec_from_file_location('metal_migration',Path(__file__).parents[1]/'migrate-metal-playerdata.py');self.m=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.m)
  self.templates={metal:base64.b64encode(self.m.write_item(self.item(metal,1))).decode() for metal in self.m.SHEETS.values()}
 def item(self,id,count):
  return T.Compound({'id':T.String('minecraft:echo_shard'),'count':T.Int(count),'components':T.Compound({'minecraft:custom_data':T.Compound({'PublicBukkitValues':T.Compound({'mandomc:item_id':T.String(id)})})})})
 def test_nested_inventory_ender_and_escrow_preserve_quantities(self):
  sheet=self.item('beskar_sheet',17);sheet['Slot']=T.Byte(4);plain=self.item('unrelated',13)
  blob=self.m.write_item(sheet);other=self.m.write_item(plain);array=b'\x01'+struct.pack('>i',3)+struct.pack('>i',len(blob))+blob+struct.pack('>i',0)+struct.pack('>i',len(other))+other
  n=nbtlib.File({'Inventory':T.List[T.Compound]([copy.deepcopy(sheet),plain]),'EnderItems':T.List[T.Compound]([T.Compound({'contents':T.List[T.Compound]([copy.deepcopy(sheet)])})]),'BukkitValues':T.Compound({'mandomc:interactive_tutorial_inventory':T.ByteArray(list(b if b<128 else b-256 for b in array)),'mandomc:jawa_sell_custody_v1':T.String(base64.b64encode(blob).decode()),'mandomc:auction_escrow_v1':T.String('00000000-0000-0000-0000-000000000001='+base64.b64encode(blob).decode())}),'Health':T.Float(15.5)})
  worker=self.m.Migration(self.templates);worker.visit(n);self.assertEqual({'beskar_sheet':85},worker.counts)
  self.assertEqual(17,n['Inventory'][0]['count']);self.assertEqual(4,n['Inventory'][0]['Slot']);self.assertEqual(plain,n['Inventory'][1]);self.assertEqual(15.5,n['Health'])
  before=self.m.write_item(n);again=self.m.Migration(self.templates);again.visit(n);self.assertEqual({},again.counts);self.assertEqual(before,self.m.write_item(n))
 def test_dry_run_backup_and_repeat_leave_other_fields_intact(self):
  with tempfile.TemporaryDirectory() as temp:
   root=Path(temp);world=root/'world';data=world/'players/data';data.mkdir(parents=True);(world/'level.dat').touch();(world/'session.lock').touch()
   n=nbtlib.File({'Inventory':T.List[T.Compound]([self.item('mandalorian_sheet',64)]),'Health':T.Float(11.0)});p=data/'player.dat';raw=self.m.write_item(n);p.write_bytes(raw)
   report=self.m.migrate(world,self.templates,root/'backup');self.assertEqual(1,report['filesChanged']);self.assertEqual(raw,p.read_bytes())
   self.m.migrate(world,self.templates,root/'backup',True);self.assertEqual(raw,(root/'backup/players/data/player.dat').read_bytes());saved=self.m.read_item(p.read_bytes());self.assertEqual('mandalorian_iron',self.m.Migration.item_id(saved['Inventory'][0]));self.assertEqual(64,saved['Inventory'][0]['count']);self.assertEqual(11.0,saved['Health'])
   self.assertEqual(0,self.m.migrate(world,self.templates,root/'again',True)['filesChanged'])
 def test_native_session_lock_prevents_live_mutation(self):
  import subprocess,sys
  with tempfile.TemporaryDirectory() as temp:
   root=Path(temp);(root/'level.dat').touch();(root/'session.lock').touch()
   proc=subprocess.Popen([sys.executable,'-c','import fcntl,sys;f=open(sys.argv[1],"r+b");fcntl.lockf(f,fcntl.LOCK_EX);print("locked",flush=True);sys.stdin.read()',str(root/'session.lock')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
   try:
    self.assertEqual('locked',proc.stdout.readline().strip())
    with self.assertRaises(BlockingIOError):self.m.migrate(root,self.templates,root/'backup',True)
   finally:proc.communicate('end',timeout=5)
