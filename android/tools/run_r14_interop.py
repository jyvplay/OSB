#!/usr/bin/env python3
"""Cross-language corpus into production Kotlin, not Office/Android execution or independent authorship."""
import ast,base64,decimal,hashlib,json,os,random,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r14';report=ROOT/'docs/verification/r14-interop.json';report.unlink(missing_ok=True)
receipt=(OUT/'result.txt').read_text()
for line in receipt.splitlines():
 if len(line)>66 and line[64:66]=='  ':assert hashlib.sha256((ROOT/line[66:]).read_bytes()).hexdigest()==line[:64],line
r=random.Random(140015);b64=lambda s:base64.b64encode(s.encode()).decode();requests=[];expected=[]
def add(op,fields,value):requests.append('\t'.join([op]+list(map(str,fields))));expected.append((op,value))
D=decimal.Decimal
with decimal.localcontext() as ctx:
 ctx.prec=60
 def luminance(rgb):
  channels=[D((rgb>>n)&255)/255 for n in (16,8,0)]
  linear=[s/D('12.92') if s<=D('0.04045') else ((s+D('.055'))/D('1.055'))**D('2.4') for s in channels]
  return sum(a*b for a,b in zip(linear,map(D,['.2126','.7152','.0722'])))
 for _ in range(100):
  a=r.randrange(1<<24);b=r.randrange(1<<24);x=luminance(a);y=luminance(b)
  add('C',[a,b],(max(x,y)+D('.05'))/(min(x,y)+D('.05')))
for _ in range(100):
 names=['item'+('0'*r.randrange(5))+str(r.randrange(10**r.randrange(1,100)))+'.txt' for _ in range(30)]
 def key(i):
  digits=re.fullmatch(r'item(\d+)\.txt',names[i])[1]
  return (int(digits),len(digits),names[i],str(i))
 add('D',map(b64,names),','.join(map(str,sorted(range(len(names)),key=key))))
# Python's maintained AST parser supplies syntax/precedence independently of our Kotlin parser.
def evaluate(node,env):
 if isinstance(node,ast.Constant):assert type(node.value) is int;return D(node.value)
 if isinstance(node,ast.Name):return env[node.id]
 if isinstance(node,ast.UnaryOp):
  v=evaluate(node.operand,env);return -v if isinstance(node.op,ast.USub) else v
 assert isinstance(node,ast.BinOp)
 a=evaluate(node.left,env);b=evaluate(node.right,env)
 if isinstance(node.op,ast.Add):return a+b
 if isinstance(node.op,ast.Sub):return a-b
 if isinstance(node.op,ast.Mult):return a*b
 assert isinstance(node.op,ast.Div);return a/b
with decimal.localcontext() as ctx:
 ctx.prec=34;ctx.rounding=decimal.ROUND_HALF_EVEN
 for _ in range(100):
  source=[str(r.randrange(1,1000))];env={'A1':D(source[0])}
  for i in range(2,10):
   expr=f'(A{i-1}*{r.randrange(1,100)}+{r.randrange(100)})/{r.randrange(1,100)}-{r.randrange(100)}'
   source.append('='+expr);env[f'A{i}']=evaluate(ast.parse(expr,mode='eval').body,env)
  add('F',map(b64,source),env['A9'])
for i in range(100):
 old=f'old🙂{i}';new=f'new\n{i}';unrelated=f'unchanged界{i}'
 add('S',map(b64,[old,new,unrelated]),b64(new)+','+b64(unrelated))
for i in range(100):
 now=r.randrange(1,100000);retention=r.randrange(1,100001);capacity=r.randrange(1,16)
 times=[r.randrange(now+1) for _ in range(40)]
 ids=sorted((j for j,t in enumerate(times) if now-t<retention),key=lambda j:(-times[j],str(j)))[:capacity]
 add('N',[now,capacity,retention]+times,','.join(map(str,ids)))
for i in range(100):
 boxes=[(0,0,10,10)]+[(x,y,x+10,y+10) for x,y in [(r.randrange(-100,101),r.randrange(-100,101)) for _ in range(30)]]
 direction=r.choice(['LEFT','RIGHT','UP','DOWN']);horizontal=direction in ('LEFT','RIGHT');positive=direction in ('RIGHT','DOWN');candidates=[]
 for j,(left,top,right,bottom) in enumerate(boxes[1:],1):
  dx=(left+right)/2-5;dy=(top+bottom)/2-5;delta=dx if horizontal else dy
  if (delta>0 if positive else delta<0):
   overlap=(min(bottom,10)>max(top,0)) if horizontal else (min(right,10)>max(left,0))
   candidates.append((not overlap,dx*dx+dy*dy,str(j)))
 answer=min(candidates)[2] if candidates else 'NONE'
 add('G',[direction]+[','.join(map(str,b)) for b in boxes],answer)
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
cp=os.pathsep.join(str(OUT/n) for n in ['checks.jar','DirectoryQuery.jar','Contrast.jar','SpatialFocus.jar','NoticeLedger.jar','SettingsPatch.jar','DecimalSheet.jar'])
p=subprocess.run([java,'-cp',cp,'dev.fusion.testing.R14Oracle'],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True,timeout=60)
actual=p.stdout.splitlines();assert len(actual)==len(expected),(len(actual),len(expected),p.stderr)
for i,(output,(op,value)) in enumerate(zip(actual,expected)):
 assert output!='REFUSED',(i,op,requests[i])
 if op=='C':assert abs(D(output)-value)<D('1e-11'),(i,output,value)
 elif op=='F':assert D(output)==value,(i,output,value)
 else:assert output==value,(i,op,output,value)
report.write_text(json.dumps({'passed':len(actual),'failed':0,'seed':140015,'counts':{op:sum(o==op for o,v in expected) for op in ['C','D','F','S','N','G']},'contrast_absolute_tolerance':'1e-11','numeric_comparison':'decimal numerical equality, precision34 HALF_EVEN','scope':'Same-author cross-language reference corpus; maintained Python decimal/AST. Not independent review, actual Office engine, Android, AX or isolation verification.','script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'compiled_receipt_sha256':hashlib.sha256(receipt.encode()).hexdigest(),'requests_sha256':hashlib.sha256(('\n'.join(requests)+'\n').encode()).hexdigest()},indent=2)+'\n')
print(f'R14: {len(actual)} cross-language cases PASS')
