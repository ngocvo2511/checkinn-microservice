"""Explicit local provider modes. No credential inference, production fallback or network calls."""
from urllib.parse import urlparse
import base64
import json
import re


def sandbox_environment(values):
    mode=values.get('SUPABASE_DEV_VNPAY_MODE','simulated')
    if mode=='simulated':
        key=values.get('BATCH6B_TEST_VNPAY_HASH_SECRET','').strip()
        if not key:raise ValueError('Missing local callback simulation key')
        return {'VNPAY_TMN_CODE':'B6BTEST1','VNPAY_HASH_SECRET':key}
    if mode!='sandbox':raise ValueError('Only simulated or sandbox VNPay mode is allowed')
    code=values.get('VNPAY_TMN_CODE','').strip();key=values.get('VNPAY_HASH_SECRET','').strip()
    if not re.fullmatch(r'[A-Za-z0-9]{6,16}',code) or code=='B6BTEST1' or not key:
        raise ValueError('Configure your own Sandbox merchant code and hash secret privately')
    if key==values.get('BATCH6B_TEST_VNPAY_HASH_SECRET'):
        raise ValueError('Simulation key cannot be used as Sandbox merchant credentials')
    pay=values.get('VNPAY_PAY_URL','https://sandbox.vnpayment.vn/paymentv2/vpcpay.html').strip()
    if pay!='https://sandbox.vnpayment.vn/paymentv2/vpcpay.html':
        raise ValueError('Only the official VNPay Sandbox payment endpoint is allowed')
    returned=values.get('VNPAY_RETURN_URL','http://localhost:3000/booking/payment/return').strip()
    parsed=urlparse(returned)
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('Invalid Sandbox return URL')
    local=parsed.scheme=='http' and parsed.hostname in ('localhost','127.0.0.1') and parsed.port==3000
    external=parsed.scheme=='https' and bool(parsed.hostname)
    if not (local or external) or parsed.path!='/booking/payment/return':
        raise ValueError('Return URL must target the reviewed frontend payment return route')
    return {'VNPAY_TMN_CODE':code,'VNPAY_HASH_SECRET':key,'VNPAY_PAY_URL':pay,'VNPAY_RETURN_URL':returned}


def storage_environment(values):
    mode=values.get('SUPABASE_DEV_STORAGE_MODE','disabled')
    if mode=='disabled':
        return {'SUPABASE_URL':'http://127.0.0.1:9','SUPABASE_SERVICE_ROLE_KEY':'unconfigured-local-storage',
                'SUPABASE_BUCKET':'batch6b-unconfigured','SUPABASE_PRIVATE_BUCKET':'batch6b-unconfigured-private'}
    if mode!='supabase':raise ValueError('Unknown Storage provider mode')
    url=values.get('SUPABASE_URL','').strip().rstrip('/');parsed=urlparse(url)
    ref=values.get('SUPABASE_STORAGE_PROJECT_REF','').strip()
    if not re.fullmatch(r'[a-z]{20}',ref) or url!='https://'+ref+'.supabase.co':
        raise ValueError('Storage HTTPS URL must match the explicitly selected project ref')
    key=values.get('SUPABASE_SERVICE_ROLE_KEY','').strip()
    if not key or not (key.startswith('sb_secret_') or key.startswith('eyJ')):
        raise ValueError('A server-side Supabase secret or legacy service_role key is required')
    if key.startswith('eyJ'):
        try:
            claims=json.loads(base64.urlsafe_b64decode(key.split('.')[1]+'==='))
        except (ValueError,IndexError,UnicodeDecodeError):
            raise ValueError('Invalid legacy Storage key format') from None
        if claims.get('role')!='service_role' or claims.get('ref',ref)!=ref:
            raise ValueError('Storage requires the selected project server-side service_role key')
    public=values.get('SUPABASE_BUCKET','').strip();private=values.get('SUPABASE_PRIVATE_BUCKET','').strip()
    if public==private or not all(re.fullmatch(r'[a-z0-9][a-z0-9_-]{1,62}',x) for x in (public,private)):
        raise ValueError('Configure two distinct reviewed Storage bucket names')
    return {'SUPABASE_URL':url,'SUPABASE_SERVICE_ROLE_KEY':key,
            'SUPABASE_BUCKET':public,'SUPABASE_PRIVATE_BUCKET':private}
