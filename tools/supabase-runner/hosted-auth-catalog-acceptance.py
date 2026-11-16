"""Synthetic API/OTP/catalog acceptance on the explicitly prepared Batch6B session. Retains fixtures."""
import datetime
import json
import re
import secrets
import time
import urllib.request
import uuid
import acceptance_runtime as h

checks=[]
out=h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/auth-catalog-hosted-results.json'


def check(ok,label,code=None):
    checks.append({'case':label,'status':'PASS' if ok else 'FAIL',**({'httpStatus':code} if code is not None else {})})
    out.write_text(json.dumps({'checks':checks,'hosted':True,'fixtureCleanup':'none; retained synthetic rows'},indent=2)+'\n',encoding='utf-8')
    print(label+' '+checks[-1]['status'],flush=True)
    if not ok:raise AssertionError(label)


def verify_and_login(fixture,label):
    email,password=fixture['email'],fixture['password']
    uid=fixture['response']['userId'];uuid.UUID(uid)
    check(fixture['httpStatus']==200,label+' register via Gateway',fixture['httpStatus'])
    code,_=h.api('gateway','/api/auth/login','POST',{'usernameOrEmail':email,'password':password},anonymous=True)
    check(code==401,label+' unverified login denied',code)
    found={}
    def delivered():
        with urllib.request.urlopen('http://127.0.0.1:56825/api/v1/messages',timeout=5) as res:messages=json.load(res)
        for message in messages.get('messages',[]):
            if any(x.get('Address')==email for x in message.get('To',[])):
                with urllib.request.urlopen('http://127.0.0.1:56825/api/v1/message/'+message['ID'],timeout=5) as res:body=json.load(res)
                text=body.get('Text','')+' '+body.get('HTML','')
                match=re.search(r'(?<![0-9])[0-9]{6}(?![0-9])',text)
                if match:found['otp']=match.group();return True
        return False
    h.wait(delivered,label+' real RabbitMQ to Mailpit OTP delivery',40)
    check(True,label+' real RabbitMQ/Notification/Mailpit delivery')
    code,body=h.api('gateway','/api/auth/otp/verify','POST',{'email':email,'otpCode':found['otp']},anonymous=True)
    check(code==200 and body.get('verified') is True,label+' actual OTP verification',code)
    code,body=h.api('gateway','/api/auth/login','POST',{'usernameOrEmail':email,'password':password},anonymous=True)
    check(code==200 and body.get('userId')==uid,label+' verified login via Gateway',code)
    fixture['login']=body
    return uid,body['token']


def main():
    h.require_open_acceptance()
    fixture=json.loads((h.PRIVATE/'auth-fixture.private.json').read_text())
    guest,guest_token=verify_and_login(fixture,'guest')
    code,_=h.api('gateway','/api/auth/login','POST',{'usernameOrEmail':fixture['email'],'password':'deliberately-wrong'},anonymous=True)
    check(code==401,'wrong password denied',code)
    code,body=h.api('gateway','/api/user/profile',bearer=guest_token)
    check(code==200,'profile read through Gateway',code)
    code,body=h.api('gateway','/api/user/profile','PUT',{'fullName':'Synthetic Batch6B updated','phone':'0900000000','country':'VN'},bearer=guest_token)
    check(code==200 and body.get('fullName')=='Synthetic Batch6B updated','profile update persisted',code)
    code,_=h.api('gateway','/api/bookings/user/'+guest,bearer=guest_token)
    check(code==200,'real issued JWT accesses own bookings',code)
    code,_=h.api('gateway','/api/bookings/user/'+str(uuid.uuid4()),bearer=guest_token)
    check(code==403,'real issued JWT cannot access other user bookings',code)
    code,_=h.api('gateway','/api/auth/logout','POST',bearer=guest_token)
    check(code==200,'logout stores revocation in isolated Redis',code)
    code,_=h.api('gateway','/api/bookings/user/'+guest,bearer=guest_token)
    check(code==401,'revoked JWT rejected across services',code)
    time.sleep(1.1)
    code,body=h.api('gateway','/api/auth/login','POST',{'usernameOrEmail':fixture['email'],'password':fixture['password']},anonymous=True)
    check(code==200,'fresh login after logout',code);fixture['login']=body
    (h.PRIVATE/'auth-fixture.private.json').write_text(json.dumps(fixture,indent=2)+'\n',encoding='utf-8')
    mark='b6bowner_'+secrets.token_hex(6);password=secrets.token_urlsafe(22)+'Aa1!'
    code,body=h.api('gateway','/api/auth/register-owner','POST',{'username':mark,'email':mark+'@example.invalid','password':password,'fullName':'Synthetic Batch6B Owner'},anonymous=True)
    owner_fixture={'username':mark,'email':mark+'@example.invalid','password':password,'response':body,'httpStatus':code}
    (h.PRIVATE/'owner-fixture.private.json').write_text(json.dumps(owner_fixture,indent=2)+'\n',encoding='utf-8')
    owner,owner_token=verify_and_login(owner_fixture,'owner')
    (h.PRIVATE/'owner-fixture.private.json').write_text(json.dumps(owner_fixture,indent=2)+'\n',encoding='utf-8')
    city,province=str(uuid.uuid4()),str(uuid.uuid4())
    h.sql('hotel',f"INSERT INTO provinces(id,name,hotel_count,created_at) VALUES ('{province}','Synthetic province {mark}',0,now()); INSERT INTO cities(id,parent_id,name,hotel_count,created_at) VALUES ('{city}','{province}','Synthetic city {mark}',0,now());",fixture=True)
    request={'name':'Synthetic Batch6B Draft '+mark,'description':'Synthetic acceptance; retained','starRating':3,'cityId':city,
             'address':{'street':'Synthetic street','city':'Synthetic city','country':'VN'},'contactEmail':owner_fixture['email'],'contactPhone':'0900000000',
             'businessLicenseNumber':'SYN-'+mark,'taxId':'SYN-'+mark,'ownerIdentityNumber':'SYN-'+mark,'accommodationType':'HOTEL','policies':[],'questions':[],'amenityCategories':[]}
    code,body=h.api('hotel','/hotels','POST',request,bearer=owner_token)
    check(code==200 and isinstance(body,dict) and 'id' in body,'Hotel create through real API',code)
    hotel=body['id'];check(uuid.UUID(hotel).version==4,'Hotel UUIDv4 persisted')
    code,body=h.api('hotel','/hotels/'+hotel+'/preview',bearer=owner_token)
    check(code==200,'owner reads draft Hotel',code)
    code,_=h.api('hotel','/hotels/'+hotel,anonymous=True)
    check(code==404,'draft Hotel hidden from public',code)
    code,body=h.api('hotel','/hotels/'+hotel,'PUT',{'name':request['name']+' updated','description':'Synthetic updated'},bearer=owner_token)
    check(code==200 and body.get('name')==request['name']+' updated','Hotel update through real API',code)
    room_request={'hotelId':hotel,'name':'Synthetic Batch6B Room','basePrice':1000000,'capacity':{'adults':2,'children':0},'roomAmount':2,'amenities':[],'description':'Synthetic room'}
    code,body=h.api('hotel','/api/v1','POST',room_request,bearer=owner_token)
    check(code==200 and 'id' in body,'Room type create through real API',code);room=body['id']
    check(uuid.UUID(room).version==4,'Room UUIDv4 persisted')
    code,body=h.api('hotel','/api/v1/'+room,'PUT',{'name':'Synthetic Batch6B Room updated','description':'Synthetic updated room'},bearer=owner_token)
    check(code==200,'Room type update through real API',code)
    code,body=h.api('hotel','/api/v1/'+room+'/preview',bearer=owner_token)
    check(code==200 and body.get('name')=='Synthetic Batch6B Room updated','owner reads updated room type',code)
    code,_=h.api('hotel','/api/v1/'+room,anonymous=True)
    check(code==404,'draft room hidden from public',code)
    code,_=h.api('hotel','/api/v1/'+room+'/deactivate','PUT',bearer=owner_token)
    check(code==200,'Room deactivation through real API retains data',code)
    identifiers={'guest':guest,'owner':owner,'draftHotel':hotel,'draftRoom':room,'city':city,'province':province}
    (h.PRIVATE/'api-fixture-identifiers.json').write_text(json.dumps(identifiers,indent=2)+'\n',encoding='utf-8')
    check(True,'synthetic fixtures retained; no destructive cleanup')


if __name__=='__main__':main()
