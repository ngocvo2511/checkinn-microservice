"""Safety boundaries for switching local provider simulations to actual Sandbox/Storage."""
import base64
import json
import unittest
from provider_config import sandbox_environment,storage_environment


class ProviderConfigTest(unittest.TestCase):
    def merchant(self):
        return {'SUPABASE_DEV_VNPAY_MODE':'sandbox','VNPAY_TMN_CODE':'TEST1234',
                'VNPAY_HASH_SECRET':'synthetic-merchant-key','BATCH6B_TEST_VNPAY_HASH_SECRET':'different-simulation-key'}

    def test_real_sandbox_merchant_is_not_overridden_by_simulation_key(self):
        result=sandbox_environment(self.merchant())
        self.assertEqual(result['VNPAY_TMN_CODE'],'TEST1234')
        self.assertEqual(result['VNPAY_HASH_SECRET'],'synthetic-merchant-key')

    def test_production_payment_endpoint_rejected(self):
        with self.assertRaises(ValueError):
            sandbox_environment({**self.merchant(),'VNPAY_PAY_URL':'https://pay.vnpay.vn/vpcpay.html'})

    def test_incomplete_real_credentials_never_fall_back_to_simulation(self):
        with self.assertRaises(ValueError):sandbox_environment({**self.merchant(),'VNPAY_HASH_SECRET':''})

    def test_fake_merchant_cannot_be_claimed_as_real_sandbox(self):
        with self.assertRaises(ValueError):sandbox_environment({**self.merchant(),'VNPAY_TMN_CODE':'B6BTEST1'})

    def storage(self,role='service_role',ref='a'*20):
        payload=base64.urlsafe_b64encode(json.dumps({'role':role,'ref':ref}).encode()).decode().rstrip('=')
        return {'SUPABASE_DEV_STORAGE_MODE':'supabase','SUPABASE_URL':'https://'+'a'*20+'.supabase.co',
                'SUPABASE_STORAGE_PROJECT_REF':'a'*20,'SUPABASE_SERVICE_ROLE_KEY':'eyJhbGciOiJIUzI1NiJ9.'+payload+'.synthetic',
                'SUPABASE_BUCKET':'public-media','SUPABASE_PRIVATE_BUCKET':'private-documents'}

    def test_anon_key_is_not_accepted_as_server_storage_key(self):
        with self.assertRaises(ValueError):storage_environment(self.storage(role='anon'))

    def test_foreign_project_legacy_storage_key_rejected(self):
        with self.assertRaises(ValueError):storage_environment(self.storage(ref='b'*20))

    def test_private_and_public_buckets_cannot_be_the_same(self):
        with self.assertRaises(ValueError):storage_environment({**self.storage(),'SUPABASE_PRIVATE_BUCKET':'public-media'})

    def test_valid_server_storage_contract_preserves_configured_buckets(self):
        result=storage_environment(self.storage())
        self.assertEqual(result['SUPABASE_PRIVATE_BUCKET'],'private-documents')


if __name__=='__main__':unittest.main()
