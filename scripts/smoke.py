#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Verify isolated local permits, independent review, quotas and persistence; keep credentials private."""
from pathlib import Path
import urllib.request, urllib.error, urllib.parse, http.cookiejar, json, uuid, secrets, datetime, os, argparse, re
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--base',default='http://127.0.0.1:8116');parser.add_argument('--verify',action='store_true');parser.add_argument('--state',default='.smoke-state.json');args=parser.parse_args()
assert args.state=='.smoke-state.json' or re.fullmatch(r'\.smoke-state-[a-z0-9]+\.json',args.state)
assert urllib.parse.urlparse(args.base).hostname in ('localhost','127.0.0.1')
statefile=root/args.state;checks=0
class Client:
    def __init__(self,user=None,password=None):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
        if user:self.call('/auth/login','POST',dict(username=user,password=password))
    def call(self,path,method='GET',body=None,expected=200):
        global checks
        if method!='GET' and self.csrf is None:self.csrf=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if method!='GET':headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=20) as res:code=res.status;data=json.load(res)
        except urllib.error.HTTPError as e:code=e.code;data=json.load(e)
        assert code==expected,(path,code,data.get('code') if isinstance(data,dict) else None)
        checks+=1;return data

def check(value):
    global checks
    assert value;checks+=1

def cmd(r,note='TEST 独立核对控制措施并确认范围'):
    return dict(version=r['version'],note=note,requestKey=str(uuid.uuid4()))
config=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line)
a=Client('admin',config['ADMIN_PASSWORD'])
if args.verify:
    state=json.loads(statefile.read_text());e=Client(state['executor'],state['password']);detail=e.call('/permits/'+str(state['permit']));r=detail['record']
    check(r['status']=='REVOKED');check(r['usedQuantity']==10);check(len(detail['usages'])==2);check(detail['usages'][0]['voided']);check(len(detail['events'])==8)
    check(e.call('/permits?search='+r['code'])['total']==1)
    print(json.dumps(dict(persistenceChecks=checks,result='PASS')));raise SystemExit
suffix=uuid.uuid4().hex[:8];password='Aa9'+secrets.token_urlsafe(24)
d=a.call('/admin/departments','POST',dict(name='TEST 偏差验收 '+suffix))['id']
roles={x['name']:x['id'] for x in a.call('/admin/roles')}
def user(prefix,role,dept=d):
    name=prefix+'-'+suffix
    uid=a.call('/admin/users','POST',dict(username=name,displayName='TEST '+prefix,password=password,roleId=roles[role],departmentId=dept,enabled=True))['id']
    return name,uid,Client(name,password)
an,ai,author=user('author','申请人员')
rn,ri,review=user('reviewer','技术复核员');pn,pi,approver=user('approver','批准人员');en,ei,execute=user('executor','执行人员');on,oi,other=user('other','执行人员');xn,xi,outside=user('outside','申请人员',1)
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date()
body=dict(code='TEST-'+suffix,title='TEST 定位夹具临时替代',category='FIXTURE',departmentId=d,reviewerId=ri,approverId=pi,executorId=ei,itemCode='TEST-PART-01',itemRevision='A',scopeTag='TEST-WO-01',baseline='TEST 使用原定位夹具并逐件检查尺寸',deviation='TEST 临时使用替代定位夹具',controls='TEST 首件确认后逐件检查，记录测量结果和工单凭据',externalAuthorization='TEST 系统验收，未涉及客户产品，不适用外部批准',quantityLimit=10,unit='件',validFrom=today.isoformat(),validUntil=(today+datetime.timedelta(days=7)).isoformat())
p=author.call('/permits','POST',body);pid=p['id'];path='/permits/'+str(pid)
def current():return author.call(path)['record']
def act(client,action):return client.call(path+'/commands/'+action,'POST',cmd(current()))
def use(q):
    u=cmd(current());u.update(reference='TEST-USE-'+uuid.uuid4().hex[:8],quantity=q,evidence='TEST 逐件测量记录已核对，范围和物料修订一致',itemCode='TEST-PART-01',itemRevision='A',scopeTag='TEST-WO-01');return u
Client().call('/permits',expected=401);outside.call(path,expected=403);other.call(path,expected=403);other.call(path+'/report.json',expected=403);check(other.call('/permits')['total']==0);check(len(execute.call('/options')['accounts'])==1)
act(author,'submit');a.call(path+'/commands/review','POST',cmd(current()),403);act(review,'review');act(approver,'approve');check(current()['status']=='ACTIVE')
bad=dict(body,version=current()['version'],quantityLimit=100);author.call(path,'PUT',bad,409)
bad=use(1);bad['itemRevision']='WRONG';execute.call(path+'/usages','POST',bad,409);execute.call(path+'/usages','POST',use(11),409)
u=use(6);usage=execute.call(path+'/usages','POST',u);check(execute.call(path+'/usages','POST',u)['id']==usage['id']);check(current()['usedQuantity']==6)
execute.call(path+'/usages','POST',dict(u,quantity=7),409);duplicate=use(1);duplicate['reference']=u['reference'].lower();execute.call(path+'/usages','POST',duplicate,409);check(current()['usedQuantity']==6)
execute.call(path+'/usages/'+str(usage['id'])+'/void','POST',cmd(current()),403)
v=cmd(current());approver.call(path+'/usages/'+str(usage['id'])+'/void','POST',v);approver.call(path+'/usages/'+str(usage['id'])+'/void','POST',v);check(current()['usedQuantity']==0)
u2=execute.call(path+'/usages','POST',use(10));check(current()['status']=='EXHAUSTED');act(approver,'revoke');execute.call(path+'/usages','POST',use(1),409)
detail=execute.call(path);check(len(detail['events'])==8);check(len(detail['usages'])==2);check(detail['usages'][0]['quantity']==6);check(detail['usages'][0]['voided']);export=execute.call(path+'/report.json');check('zhuatech' not in json.dumps(export));check('password' not in json.dumps(export));check(execute.call('/dashboard')['counts']['REVOKED']==1)
state=dict(author=an,reviewer=rn,approver=pn,executor=en,other=on,password=password,permit=pid,department=d,usage=u2['id'])
fd=os.open(statefile,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:json.dump(state,f)
print(json.dumps(dict(mysqlChecks=checks,result='PASS',fixtures='TEST only')))
