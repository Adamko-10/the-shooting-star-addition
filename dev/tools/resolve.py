# resolve.py <file> <side: ours|theirs> [from=to ...]: resolve conflict hunks by taking one side, with substitutions
import sys,re
f,side=sys.argv[1],sys.argv[2]
subs=[a.split('=',1) for a in sys.argv[3:]]
out=[];mode=None;o=[];t=[]
for line in open(f).read().split('\n'):
    if line.startswith('<<<<<<< '): mode='o';o=[];t=[];continue
    if mode=='o' and line.startswith('||||||| '): mode='b';continue
    if mode in('o','b') and line=='=======': mode='t';continue
    if mode=='t' and line.startswith('>>>>>>> '):
        chosen=o if side=='ours' else t
        for c in chosen:
            for a,b in subs: c=c.replace(a,b)
            out.append(c)
        mode=None;continue
    if mode=='o': o.append(line)
    elif mode=='t': t.append(line)
    elif mode=='b': pass
    else: out.append(line)
open(f,'w').write('\n'.join(out))
